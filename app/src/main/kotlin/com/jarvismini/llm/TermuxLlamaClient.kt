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
 * Allows the JarvisMerged app to generate and execute shell commands
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
     */
    suspend fun generateCommand(query: String): CommandResult = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/generate")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            connection.doOutput = true

            val jsonBody = JSONObject().apply {
                put("query", query)
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

            Log.d(tag, "Generate response ($responseCode): $response")

            if (responseCode == 200) {
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
            } else {
                CommandResult(
                    success = false,
                    error = "HTTP $responseCode: $response"
                )
            }
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

/**
 * Example ViewModel integration for Compose UI
 */
class JarvisLlamaViewModel {
    private val llamaClient = TermuxLlamaClient()

    sealed class LlamaState {
        object Idle : LlamaState()
        object Generating : LlamaState()
        object WaitingConfirmation : LlamaState()
        object Executing : LlamaState()
        data class Success(val result: TermuxLlamaClient.CommandResult) : LlamaState()
        data class Error(val message: String) : LlamaState()
    }

    var state: LlamaState = LlamaState.Idle
        private set

    suspend fun processQuery(
        query: String,
        onConfirmCommand: suspend (String) -> Boolean
    ) {
        try {
            state = LlamaState.Generating

            val result = llamaClient.generateAndExecute(query) { command ->
                state = LlamaState.WaitingConfirmation
                onConfirmCommand(command)
            }

            state = if (result.success) {
                LlamaState.Success(result)
            } else {
                LlamaState.Error(result.error ?: "Unknown error")
            }
        } catch (e: Exception) {
            state = LlamaState.Error(e.message ?: "Unknown error")
        }
    }
}
