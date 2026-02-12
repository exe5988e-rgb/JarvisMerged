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
 * COMPLETE VERSION: Supports both chat and command generation
 */
class TermuxLlamaClient(
    private val serverHost: String = "127.0.0.1",
    private val serverPort: Int = 8080
) {
    private val baseUrl = "http://$serverHost:$serverPort"
    private val tag = "TermuxLlamaClient"

    data class CommandResult(
        val success: Boolean,
        val response: String? = null,
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
     * Chat with JARVIS - General conversation endpoint
     * Uses /chat_sync for natural language conversations
     */
    suspend fun chat(query: String, timeoutSeconds: Int = 300): CommandResult = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/chat_sync")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 60000      // 60 second connect timeout
            connection.readTimeout = (timeoutSeconds + 10) * 1000  // timeout + buffer
            connection.doOutput = true

            val jsonBody = JSONObject().apply {
                put("query", query)
                put("wait_time", timeoutSeconds)
            }

            Log.d(tag, "Sending chat request: $query")

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

            Log.d(tag, "Chat response ($responseCode): ${response.take(200)}...")

            when (responseCode) {
                200 -> {
                    val json = JSONObject(response)
                    if (json.getBoolean("success")) {
                        CommandResult(
                            success = true,
                            response = json.getString("response")
                        )
                    } else {
                        CommandResult(
                            success = false,
                            error = json.optString("error", "Unknown error")
                        )
                    }
                }
                202 -> {
                    CommandResult(
                        success = false,
                        error = "Response generation is taking longer than expected. " +
                                "Please try a simpler query or wait and try again."
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
            Log.e(tag, "Chat timeout", e)
            CommandResult(
                success = false,
                error = "Request timeout ($timeoutSeconds seconds). The LLM might be:\n" +
                        "• Processing a very complex query\n" +
                        "• Loading the model (first run)\n" +
                        "• Experiencing resource constraints\n\n" +
                        "Try a simpler query or check the Termux server logs."
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
            Log.e(tag, "Chat error", e)
            CommandResult(
                success = false,
                error = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * Generate a shell command from natural language query
     * Uses /generate_sync for command generation
     */
    suspend fun generateCommand(query: String): CommandResult = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/generate_sync")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 60000
            connection.readTimeout = 300000  // 5 minutes
            connection.doOutput = true

            val jsonBody = JSONObject().apply {
                put("query", query)
                put("wait_time", 300)
            }

            Log.d(tag, "Sending command generation request: $query")

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
                    CommandResult(
                        success = false,
                        error = "Command generation still in progress. Please try again."
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
            Log.e(tag, "Generate timeout", e)
            CommandResult(
                success = false,
                error = "Command generation timeout. Check Termux server."
            )
        } catch (e: java.net.ConnectException) {
            Log.e(tag, "Connection failed", e)
            CommandResult(
                success = false,
                error = "Failed to connect to server at $baseUrl.\n\n" +
                        "Make sure the Termux server is running."
            )
        } catch (e: Exception) {
            Log.e(tag, "Generate error", e)
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

            Log.d(tag, "Execute response ($responseCode)")

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
            Log.e(tag, "Execute error", e)
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
        val generateResult = generateCommand(query)
        
        if (!generateResult.success || generateResult.command == null) {
            return generateResult
        }

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
