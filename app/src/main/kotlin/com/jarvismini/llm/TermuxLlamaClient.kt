package com.jarvismini.llm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.io.File

/**
 * Client for communicating with Termux LLaMA server and executor
 * 
 * FILE-BASED COMMUNICATION:
 * - Commands written to /sdcard/jarvis/command.txt
 * - Results read from /sdcard/jarvis/result.txt
 * - Status checked via /sdcard/jarvis/status.txt
 * 
 * ARCHITECTURE:
 * 1. LLM (chat, generate) → HTTP (port 8888) → llamafile (port 8080)
 * 2. Command execution → File-based → jarvis_executor.sh in Termux
 */
class TermuxLlamaClient(
    private val context: Context,
    private val serverHost: String = "127.0.0.1",
    private val serverPort: Int = 8888
) {
    private val baseUrl = "http://$serverHost:$serverPort"
    private val tag = "TermuxLlamaClient"
    
    // File-based executor configuration
    private val EXECUTOR_DIR = "/sdcard/jarvis"
    private val COMMAND_FILE = "$EXECUTOR_DIR/command.txt"
    private val RESULT_FILE = "$EXECUTOR_DIR/result.txt"
    private val STATUS_FILE = "$EXECUTOR_DIR/status.txt"

    data class CommandResult(
        val success: Boolean,
        val response: String? = null,
        val command: String? = null,
        val output: String? = null,
        val exitCode: Int? = null,
        val error: String? = null,
        val method: String? = null
    )

    /**
     * Check if the LLM server is healthy
     */
    suspend fun checkHealth(): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = java.net.URL("$baseUrl/health")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val isHealthy = connection.responseCode == 200
            Log.d(tag, "Health check: $isHealthy")
            isHealthy
        } catch (e: Exception) {
            Log.e(tag, "Health check failed", e)
            false
        }
    }

    /**
     * Chat with the LLM (conversational mode)
     * Uses /chat_sync endpoint for natural conversations
     */
    suspend fun chat(query: String, timeoutSeconds: Int = 60): CommandResult = withContext(Dispatchers.IO) {
        try {
            Log.d(tag, "Chat request: $query")
            
            val url = java.net.URL("$baseUrl/chat_sync")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 10000
            connection.readTimeout = (timeoutSeconds + 10) * 1000
            connection.doOutput = true

            // Create JSON request
            val jsonBody = """{"query": "$query"}"""
            
            connection.outputStream.use { os ->
                os.write(jsonBody.toByteArray())
            }

            val responseCode = connection.responseCode
            val response = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }

            Log.d(tag, "Chat response code: $responseCode")

            when (responseCode) {
                200 -> {
                    // Parse JSON response
                    val json = org.json.JSONObject(response)
                    if (json.getBoolean("success")) {
                        val chatResponse = json.getString("response")
                        Log.d(tag, "Chat success: ${chatResponse.take(100)}...")
                        CommandResult(success = true, response = chatResponse)
                    } else {
                        val error = json.optString("error", "Unknown error")
                        Log.e(tag, "Chat failed: $error")
                        CommandResult(success = false, error = error)
                    }
                }
                else -> {
                    Log.e(tag, "HTTP error: $responseCode")
                    CommandResult(success = false, error = "HTTP $responseCode: $response")
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Chat exception", e)
            CommandResult(success = false, error = e.message ?: "Connection failed")
        }
    }

    /**
     * Generate a Termux command from natural language
     * Uses /generate_sync endpoint for command generation
     */
    suspend fun generateCommand(query: String): CommandResult = withContext(Dispatchers.IO) {
        try {
            Log.d(tag, "Generate command: $query")
            
            val url = java.net.URL("$baseUrl/generate_sync")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 10000
            connection.readTimeout = 40000
            connection.doOutput = true

            val jsonBody = """{"query": "$query"}"""
            
            connection.outputStream.use { os ->
                os.write(jsonBody.toByteArray())
            }

            val responseCode = connection.responseCode
            val response = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }

            when (responseCode) {
                200 -> {
                    val json = org.json.JSONObject(response)
                    if (json.getBoolean("success")) {
                        val cmd = json.getString("command")
                        Log.d(tag, "Generated command: $cmd")
                        CommandResult(success = true, command = cmd, response = cmd)
                    } else {
                        val error = json.optString("error", "Unknown error")
                        Log.e(tag, "Command generation failed: $error")
                        CommandResult(success = false, error = error)
                    }
                }
                else -> {
                    Log.e(tag, "HTTP error: $responseCode")
                    CommandResult(success = false, error = "HTTP $responseCode")
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Generate command exception", e)
            CommandResult(success = false, error = e.message ?: "Connection failed")
        }
    }

    /**
     * Execute command using file-based communication with jarvis_executor.sh
     * 
     * WORKFLOW:
     * 1. Write command to /sdcard/jarvis/command.txt
     * 2. jarvis_executor.sh reads it and executes
     * 3. Result written to /sdcard/jarvis/result.txt
     * 4. Read result (format: "exit_code|output")
     */
    suspend fun executeCommand(command: String): CommandResult = withContext(Dispatchers.IO) {
        try {
            Log.d(tag, "Execute command: $command")
            
            val statusFile = File(STATUS_FILE)
            val commandFile = File(COMMAND_FILE)
            val resultFile = File(RESULT_FILE)
            
            // Check if executor is running
            if (!statusFile.exists()) {
                return@withContext CommandResult(
                    success = false,
                    error = "Executor not running. Run in Termux:\n  bash ~/jarvis_executor.sh &",
                    exitCode = -1,
                    method = "file-based"
                )
            }
            
            val status = statusFile.readText().trim()
            if (status != "ready" && status != "executing") {
                return@withContext CommandResult(
                    success = false,
                    error = "Executor status: $status",
                    exitCode = -1,
                    method = "file-based"
                )
            }
            
            // Clear old result
            if (resultFile.exists()) {
                resultFile.writeText("")
            }
            
            // Write command
            commandFile.writeText(command)
            Log.d(tag, "Command written to file")
            
            // Wait for result (max 60 seconds)
            var attempts = 0
            val maxAttempts = 300 // 300 * 200ms = 60 seconds
            
            while (attempts < maxAttempts) {
                delay(200)
                
                if (resultFile.exists() && resultFile.length() > 0) {
                    val resultContent = resultFile.readText()
                    if (resultContent.isNotBlank()) {
                        Log.d(tag, "Got result after ${attempts * 200}ms")
                        break
                    }
                }
                
                attempts++
            }
            
            // Check timeout
            if (!resultFile.exists() || resultFile.length() == 0L) {
                return@withContext CommandResult(
                    success = false,
                    error = "Timeout waiting for command execution (60s)",
                    exitCode = -1,
                    method = "file-based"
                )
            }
            
            // Parse result: "exit_code|output"
            val result = resultFile.readText()
            val parts = result.split("|", limit = 2)
            
            if (parts.size != 2) {
                return@withContext CommandResult(
                    success = false,
                    error = "Invalid result format: $result",
                    exitCode = -1,
                    method = "file-based"
                )
            }
            
            val exitCode = parts[0].toIntOrNull() ?: -1
            val output = parts[1]
            
            Log.d(tag, "Command executed: exitCode=$exitCode")
            
            CommandResult(
                success = exitCode == 0,
                output = output.ifEmpty { "✓ Command completed" },
                exitCode = exitCode,
                method = "file-based"
            )
            
        } catch (e: Exception) {
            Log.e(tag, "Execute command exception", e)
            CommandResult(
                success = false,
                error = "Execution failed: ${e.message}",
                exitCode = -1,
                method = "file-based"
            )
        }
    }
    
    /**
     * Check if jarvis_executor.sh is running
     */
    suspend fun checkExecutorStatus(): Boolean = withContext(Dispatchers.IO) {
        try {
            val statusFile = File(STATUS_FILE)
            if (!statusFile.exists()) {
                return@withContext false
            }
            
            val status = statusFile.readText().trim()
            status == "ready" || status == "executing"
        } catch (e: Exception) {
            Log.e(tag, "Executor status check failed", e)
            false
        }
    }
}
