package com.jarvismini.llm

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client for communicating with the local llamafile server via proxy
 * 
 * UPDATED: Now uses port 8888 (llamafile_proxy.py)
 */
class TermuxLlamaClient(
    private val serverHost: String = "127.0.0.1",
    private val serverPort: Int = 8888  // ← CHANGED from 8080 to 8888
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
     * This works with the llamafile_proxy.py server
     */
    suspend fun chat(query: String, timeoutSeconds: Int = 60): CommandResult = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/chat_sync")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 10000  // 10 second connect timeout
            connection.readTimeout = (timeoutSeconds + 10) * 1000  // timeout + buffer
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
                        error = "LLM server not running. Please start llamafile server first."
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
                error = "Cannot connect to server. Make sure llamafile and proxy are running."
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
     * Generate a shell command - For command generation requests
     * Note: The old server had /generate_sync endpoint, but since llamafile
     * uses the same /chat_sync endpoint, we'll just use chat() with command prompts
     */
    suspend fun generateCommand(query: String): CommandResult {
        // Just use the chat endpoint with command-generation prompt
        val commandPrompt = "Generate a Termux shell command for: $query\nRespond with ONLY the command, no explanation."
        return chat(commandPrompt, timeoutSeconds = 30)
    }

    /**
     * Execute a shell command (if the server supports /execute endpoint)
     * Note: llamafile_proxy doesn't have this, so you'd need to add it if needed
     */
    suspend fun executeCommand(command: String): CommandResult = withContext(Dispatchers.IO) {
        CommandResult(
            success = false,
            error = "Command execution not supported by llamafile proxy. Execute commands manually in Termux."
        )
    }
}
