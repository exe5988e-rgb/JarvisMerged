package com.jarvismini.llm

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client for communicating with the enhanced llamafile proxy server
 * 
 * FINAL VERSION: Supports all endpoints with proper execution
 */
class TermuxLlamaClient(
    private val serverHost: String = "127.0.0.1",
    private val serverPort: Int = 8888
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
     * Check if the server is running and healthy
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
     * Chat with JARVIS - Uses /chat_sync endpoint
     * For JarvisChatScreen
     */
    suspend fun chat(query: String, timeoutSeconds: Int = 60): CommandResult = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/chat_sync")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 10000
            connection.readTimeout = (timeoutSeconds + 10) * 1000
            connection.doOutput = true

            val jsonBody = JSONObject().apply {
                put("query", query)
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
                503 -> {
                    CommandResult(
                        success = false,
                        error = "LLM server not running. Please start llamafile server in Termux."
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
                error = "Request timed out. The model may be busy or the query too complex."
            )
        } catch (e: java.net.ConnectException) {
            Log.e(tag, "Connection error", e)
            CommandResult(
                success = false,
                error = "Cannot connect to server. Make sure llamafile and proxy are running in Termux."
            )
        } catch (e: Exception) {
            Log.e(tag, "Chat error", e)
            CommandResult(
                success = false,
                error = "Error: ${e.message}"
            )
        }
    }

    /**
     * Generate a shell command - Uses /generate_sync endpoint
     * For TermuxCommandScreen
     * 
     * This now uses the PROPER endpoint instead of chat
     */
    suspend fun generateCommand(query: String): CommandResult = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/generate_sync")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 10000
            connection.readTimeout = 40000  // 40 second timeout
            connection.doOutput = true

            val jsonBody = JSONObject().apply {
                put("query", query)
            }

            Log.d(tag, "Generating command for: $query")

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

            Log.d(tag, "Command response ($responseCode): $response")

            when (responseCode) {
                200 -> {
                    val json = JSONObject(response)
                    if (json.getBoolean("success")) {
                        val command = json.getString("command")
                        Log.d(tag, "Generated command: $command")
                        CommandResult(
                            success = true,
                            command = command,
                            response = command  // Include response for compatibility
                        )
                    } else {
                        CommandResult(
                            success = false,
                            error = json.optString("error", "Failed to generate command")
                        )
                    }
                }
                503 -> {
                    CommandResult(
                        success = false,
                        error = "LLM server not running. Please start llamafile server in Termux."
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
            Log.e(tag, "Command generation timeout", e)
            CommandResult(
                success = false,
                error = "Command generation timed out. Try a simpler request."
            )
        } catch (e: java.net.ConnectException) {
            Log.e(tag, "Connection error", e)
            CommandResult(
                success = false,
                error = "Cannot connect to server. Make sure llamafile and proxy are running in Termux."
            )
        } catch (e: Exception) {
            Log.e(tag, "Command generation error", e)
            CommandResult(
                success = false,
                error = "Error: ${e.message}"
            )
        }
    }

    /**
     * Execute a shell command via /execute endpoint
     * 
     * NOW ACTUALLY WORKS! Uses enhanced_proxy.py execution
     */
    suspend fun executeCommand(command: String): CommandResult = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/execute")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 10000
            connection.readTimeout = 60000  // 60 second timeout for execution
            connection.doOutput = true

            val jsonBody = JSONObject().apply {
                put("command", command)
            }

            Log.d(tag, "Executing command: $command")

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

            when (responseCode) {
                200 -> {
                    val json = JSONObject(response)
                    val success = json.getBoolean("success")
                    val output = json.optString("output", "")
                    val exitCode = json.optInt("exit_code", -1)
                    
                    Log.d(tag, "Command executed: success=$success, exitCode=$exitCode")
                    
                    CommandResult(
                        success = success,
                        output = output,
                        exitCode = exitCode
                    )
                }
                else -> {
                    CommandResult(
                        success = false,
                        error = "HTTP $responseCode: $response",
                        exitCode = -1
                    )
                }
            }
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(tag, "Command execution timeout", e)
            CommandResult(
                success = false,
                error = "Command execution timed out (60s limit). The command may still be running in background.",
                exitCode = -1
            )
        } catch (e: java.net.ConnectException) {
            Log.e(tag, "Connection error", e)
            CommandResult(
                success = false,
                error = "Cannot connect to server. Make sure the proxy is running in Termux.",
                exitCode = -1
            )
        } catch (e: Exception) {
            Log.e(tag, "Command execution error", e)
            CommandResult(
                success = false,
                error = "Execution error: ${e.message}",
                exitCode = -1
            )
        }
    }
}
