package com.jarvismini.llm

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client for communicating with the local Termux llama.cpp server
 * 
 * UPDATED: Now uses /generate_sync endpoint with proper timeout handling
 * The server will wait up to 25 seconds before responding, which fits
 * within the 30-second client timeout.
 */
class TermuxLlamaClient(
    private val serverHost: String = "127.0.0.1",
    private val serverPort: Int = 8080
) {
    private val baseUrl = "http://$serverHost:$serverPort"
    private val tag = "TermuxLlamaClient"

    data class CommandResult(
        val success: Boolean,
        val command: String? = null,
        val output: String? = null,
        val exitCode: Int? = null,
        val error: String? = null
    )

    /**
     * Check if the Termux server is running and healthy
     */
    suspend fun checkHealth(): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/health")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d(tag, "Health check: $response")
                true
            } else {
                Log.w(tag, "Health check failed: $responseCode")
                false
            }
        } catch (e: Exception) {
            Log.e(tag, "Health check error: ${e.message}")
            false
        }
    }

    /**
     * Generate a shell command from natural language query
     * 
     * UPDATED: Uses /generate_sync endpoint which waits up to 25 seconds
     * This is within the 30-second client timeout and avoids the issue
     * where the server takes too long to respond.
     */
    suspend fun generateCommand(query: String): CommandResult = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/generate_sync")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 30000  // 30 second connection timeout
            connection.readTimeout = 30000     // 30 second read timeout
            connection.doOutput = true

            val jsonBody = JSONObject().apply {
                put("query", query)
                put("wait_time", 25)  // Server will wait up to 25 seconds
            }

            Log.d(tag, "Sending request to $url: $jsonBody")

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(jsonBody.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            val response = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
            }

            Log.d(tag, "Generate response ($responseCode): $response")

            when (responseCode) {
                200 -> {
                    // Success - command generated
                    val json = JSONObject(response)
                    if (json.getBoolean("success")) {
                        CommandResult(
                            success = true,
                            command = json.getString("command")
                        )
                    } else {
                        CommandResult(
                            success = false,
                            error = json.optString("error", "Unknown error")
                        )
                    }
                }
                202 -> {
                    // Accepted but still processing (timeout on server side)
                    val json = JSONObject(response)
                    CommandResult(
                        success = false,
                        error = "Generation still in progress. The LLM is taking longer than expected. " +
                                "This usually happens on first run when the model is loading. " +
                                "Please try again in a few moments."
                    )
                }
                else -> {
                    CommandResult(
                        success = false,
                        error = "HTTP $responseCode: $response"
                    )
                }
            }
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(tag, "Generate command timeout", e)
            CommandResult(
                success = false,
                error = "Connection timeout. The server might be:\n" +
                        "• Still loading the model (first run takes 30-60s)\n" +
                        "• Processing a complex request\n" +
                        "• Not running\n\n" +
                        "Please check that the Termux server is running and try again."
            )
        } catch (e: java.net.ConnectException) {
            Log.e(tag, "Connection failed", e)
            CommandResult(
                success = false,
                error = "Failed to connect to server at $baseUrl.\n\n" +
                        "Make sure:\n" +
                        "1. Termux is running\n" +
                        "2. The server script is started\n" +
                        "3. Server is listening on port $serverPort"
            )
        } catch (e: Exception) {
            Log.e(tag, "Generate command error", e)
            CommandResult(
                success = false,
                error = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * Execute a shell command on Termux
     */
    suspend fun executeCommand(command: String): CommandResult = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/execute")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 60000
            connection.readTimeout = 60000
            connection.doOutput = true

            val jsonBody = JSONObject().apply {
                put("command", command)
            }

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(jsonBody.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            val response = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
            }

            Log.d(tag, "Execute response ($responseCode): $response")

            if (responseCode == 200) {
                val json = JSONObject(response)
                CommandResult(
                    success = json.getBoolean("success"),
                    command = command,
                    output = json.optString("output"),
                    exitCode = json.optInt("exit_code")
                )
            } else {
                CommandResult(
                    success = false,
                    error = "HTTP $responseCode: $response"
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "Execute command error", e)
            CommandResult(
                success = false,
                error = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * Generate and execute with explicit user confirmation
     */
    suspend fun generateAndExecute(
        query: String,
        confirmCallback: suspend (String) -> Boolean
    ): CommandResult {
        // First generate the command
        val generateResult = generateCommand(query)
        
        if (!generateResult.success || generateResult.command == null) {
            return generateResult
        }

        // Ask user for confirmation
        val shouldExecute = confirmCallback(generateResult.command)
        
        if (!shouldExecute) {
            return CommandResult(
                success = false,
                command = generateResult.command,
                error = "User cancelled execution"
            )
        }

        return executeCommand(generateResult.command)
    }
}
