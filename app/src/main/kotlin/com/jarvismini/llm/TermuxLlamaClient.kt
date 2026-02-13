package com.jarvismini.llm

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client for communicating with Termux LLaMA server and executor
 * 
 * UPDATED: File-based executor support for /sdcard compatibility
 */
class TermuxLlamaClient(private val context: Context) {
    
    // LLM Server configuration (for command generation)
    private val llamaServerUrl = "http://127.0.0.1:8080"
    
    // File-based executor configuration (NEW)
    private val EXECUTOR_DIR = "/sdcard/jarvis"
    private val COMMAND_FILE = "$EXECUTOR_DIR/command.txt"
    private val RESULT_FILE = "$EXECUTOR_DIR/result.txt"
    private val STATUS_FILE = "$EXECUTOR_DIR/status.txt"
    
    /**
     * Check if the executor service is running
     */
    suspend fun checkHealth(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Check if status file exists and contains "ready"
            val statusFile = File(STATUS_FILE)
            if (!statusFile.exists()) {
                return@withContext false
            }
            
            val status = statusFile.readText().trim()
            return@withContext status == "ready" || status == "executing"
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Generate a Termux command from natural language query
     */
    suspend fun generateCommand(query: String): CommandResult = withContext(Dispatchers.IO) {
        try {
            // Call LLM server to generate command
            val url = URL("$llamaServerUrl/completion")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            
            // Create prompt for command generation
            val prompt = """Convert this request to a Termux bash command. 
                |Respond with ONLY the command, no explanation.
                |
                |Request: $query
                |Command:""".trimMargin()
            
            val jsonBody = """{"prompt": "$prompt", "n_predict": 100}"""
            
            connection.outputStream.use { os ->
                os.write(jsonBody.toByteArray())
            }
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                
                // Parse JSON response and extract command
                // Simple parsing - you might want to use a JSON library
                val command = extractCommandFromResponse(response)
                
                CommandResult(
                    success = true,
                    command = command
                )
            } else {
                CommandResult(
                    success = false,
                    error = "Server returned code: $responseCode"
                )
            }
        } catch (e: Exception) {
            CommandResult(
                success = false,
                error = "Failed to generate command: ${e.message}"
            )
        }
    }
    
    /**
     * Execute a command using the file-based executor
     */
    suspend fun executeCommand(command: String): ExecutionResult = withContext(Dispatchers.IO) {
        try {
            // Verify executor is running
            val statusFile = File(STATUS_FILE)
            if (!statusFile.exists()) {
                return@withContext ExecutionResult(
                    success = false,
                    error = "Executor not running. Status file not found.",
                    method = "file-based"
                )
            }
            
            // Clear old result
            val resultFile = File(RESULT_FILE)
            if (resultFile.exists()) {
                resultFile.writeText("")
            }
            
            // Write command to command file
            val commandFile = File(COMMAND_FILE)
            commandFile.writeText(command)
            
            // Wait for executor to process (check status)
            var attempts = 0
            val maxAttempts = 50 // 10 second timeout (50 * 200ms)
            
            while (attempts < maxAttempts) {
                Thread.sleep(200)
                
                // Check if result is ready
                if (resultFile.exists() && resultFile.length() > 0) {
                    val resultContent = resultFile.readText()
                    if (resultContent.isNotBlank()) {
                        break
                    }
                }
                
                attempts++
            }
            
            // Read result
            if (!resultFile.exists() || resultFile.length() == 0L) {
                return@withContext ExecutionResult(
                    success = false,
                    error = "Timeout waiting for command execution",
                    method = "file-based"
                )
            }
            
            val result = resultFile.readText()
            
            // Parse result (format: "exit_code|output")
            val parts = result.split("|", limit = 2)
            val exitCode = parts.getOrNull(0)?.toIntOrNull() ?: -1
            val output = parts.getOrNull(1) ?: ""
            
            ExecutionResult(
                success = exitCode == 0,
                output = output,
                exitCode = exitCode,
                method = "file-based"
            )
        } catch (e: Exception) {
            ExecutionResult(
                success = false,
                error = "Execution failed: ${e.message}",
                method = "file-based"
            )
        }
    }
    
    /**
     * Extract command from LLM response
     * Simple implementation - enhance as needed
     */
    private fun extractCommandFromResponse(response: String): String {
        // Very basic parsing - you should use a proper JSON parser
        try {
            // Look for "content" field in response
            val contentStart = response.indexOf("\"content\"")
            if (contentStart != -1) {
                val valueStart = response.indexOf(":", contentStart) + 1
                val valueEnd = response.indexOf("\"", valueStart + 1)
                if (valueEnd != -1) {
                    return response.substring(valueStart, valueEnd)
                        .trim()
                        .trim('"')
                        .trim()
                }
            }
            
            // Fallback: return trimmed response
            return response.trim()
        } catch (e: Exception) {
            return response.trim()
        }
    }
    
    /**
     * Result of command generation
     */
    data class CommandResult(
        val success: Boolean,
        val command: String? = null,
        val error: String? = null
    )
    
    /**
     * Result of command execution
     */
    data class ExecutionResult(
        val success: Boolean,
        val output: String? = null,
        val exitCode: Int? = null,
        val error: String? = null,
        val method: String? = null
    )
}
