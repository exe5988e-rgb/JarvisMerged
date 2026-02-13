package com.jarvismini.llm

import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client for communicating with llamafile via Termux intents
 * 
 * UPDATED VERSION: Uses Termux intents to execute commands directly
 * Falls back to HTTP when intents are not available
 */
class TermuxLlamaClient(
    private val context: Context? = null,
    private val serverHost: String = "127.0.0.1",
    private val serverPort: Int = 8888
) {
    private val baseUrl = "http://$serverHost:$serverPort"
    private val tag = "TermuxLlamaClient"
    
    // Termux intent constants
    private companion object {
        const val TERMUX_SERVICE = "com.termux.app.RunCommandService"
        const val TERMUX_PACKAGE = "com.termux"
        
        // File paths for command queue
        const val QUEUE_DIR = ".jarvis_queue"
        const val SCRIPT_DIR = ".jarvis_scripts"
    }

    data class CommandResult(
        val success: Boolean,
        val response: String? = null,
        val command: String? = null,
        val output: String? = null,
        val exitCode: Int? = null,
        val error: String? = null,
        val method: ExecutionMethod = ExecutionMethod.HTTP
    )
    
    enum class ExecutionMethod {
        TERMUX_INTENT,  // Direct Termux execution via intent
        HTTP,           // HTTP proxy (legacy)
        SCRIPT_FILE     // Script file in Termux home directory
    }

    /**
     * Check if Termux is installed
     */
    private fun isTermuxInstalled(): Boolean {
        return try {
            context?.packageManager?.getPackageInfo(TERMUX_PACKAGE, 0) != null
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Execute command using Termux intent
     * This is the preferred method as it works directly with Termux
     */
    private fun executeViaTermuxIntent(command: String): Boolean {
        if (context == null) {
            Log.w(tag, "Context not provided, cannot use Termux intents")
            return false
        }
        
        if (!isTermuxInstalled()) {
            Log.w(tag, "Termux not installed")
            return false
        }
        
        return try {
            // Use Termux:Tasker plugin intent (RUN_COMMAND)
            val intent = Intent().apply {
                setClassName(TERMUX_PACKAGE, TERMUX_SERVICE)
                action = "com.termux.RUN_COMMAND"
                putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command))
                putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
                putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0") // Add to current session
            }
            
            context.startService(intent)
            Log.d(tag, "Executed via Termux intent: $command")
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to execute via Termux intent", e)
            false
        }
    }
    
    /**
     * Execute command using script file
     * Writes command to a file in Termux home directory for execution
     */
    private suspend fun executeViaScriptFile(command: String): CommandResult = withContext(Dispatchers.IO) {
        try {
            val termuxHome = "/data/data/com.termux/files/home"
            val scriptDir = File(termuxHome, SCRIPT_DIR)
            val queueDir = File(termuxHome, QUEUE_DIR)
            
            // Create timestamp-based filenames
            val timestamp = System.currentTimeMillis()
            val scriptFile = File(scriptDir, "cmd_${timestamp}.sh")
            val resultFile = File(queueDir, "result_${timestamp}.txt")
            
            // Ensure directories exist (if we have access)
            scriptDir.mkdirs()
            queueDir.mkdirs()
            
            // Write the script
            val scriptContent = """#!/data/data/com.termux/files/usr/bin/bash
cd ~
$command > ${resultFile.absolutePath} 2>&1
echo "EXIT_CODE=$?" >> ${resultFile.absolutePath}
"""
            scriptFile.writeText(scriptContent)
            scriptFile.setExecutable(true)
            
            // Execute via Termux intent
            val executed = context?.let {
                executeViaTermuxIntent("bash ${scriptFile.absolutePath}")
            } ?: false
            
            if (executed) {
                CommandResult(
                    success = true,
                    output = "Command queued for execution. Check $resultFile for results.",
                    exitCode = 0,
                    method = ExecutionMethod.SCRIPT_FILE
                )
            } else {
                CommandResult(
                    success = false,
                    error = "Failed to queue command",
                    method = ExecutionMethod.SCRIPT_FILE
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "Script file execution failed", e)
            CommandResult(
                success = false,
                error = "Script execution failed: ${e.message}",
                method = ExecutionMethod.SCRIPT_FILE
            )
        }
    }

    /**
     * Check if the HTTP server is running and healthy
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
                            response = json.getString("response"),
                            method = ExecutionMethod.HTTP
                        )
                    } else {
                        CommandResult(
                            success = false,
                            error = json.optString("error", "Unknown error"),
                            method = ExecutionMethod.HTTP
                        )
                    }
                }
                503 -> {
                    CommandResult(
                        success = false,
                        error = "LLM server not running. Please start llamafile server in Termux.",
                        method = ExecutionMethod.HTTP
                    )
                }
                else -> {
                    CommandResult(
                        success = false,
                        error = "HTTP $responseCode: $response",
                        method = ExecutionMethod.HTTP
                    )
                }
            }
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(tag, "Chat timeout", e)
            CommandResult(
                success = false,
                error = "Request timed out. The model may be busy or the query too complex.",
                method = ExecutionMethod.HTTP
            )
        } catch (e: java.net.ConnectException) {
            Log.e(tag, "Connection error", e)
            CommandResult(
                success = false,
                error = "Cannot connect to server. Make sure llamafile and proxy are running in Termux.",
                method = ExecutionMethod.HTTP
            )
        } catch (e: Exception) {
            Log.e(tag, "Chat error", e)
            CommandResult(
                success = false,
                error = "Error: ${e.message}",
                method = ExecutionMethod.HTTP
            )
        }
    }

    /**
     * Generate a shell command - Uses /generate_sync endpoint
     */
    suspend fun generateCommand(query: String): CommandResult = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/generate_sync")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 10000
            connection.readTimeout = 40000
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
                            response = command,
                            method = ExecutionMethod.HTTP
                        )
                    } else {
                        CommandResult(
                            success = false,
                            error = json.optString("error", "Failed to generate command"),
                            method = ExecutionMethod.HTTP
                        )
                    }
                }
                503 -> {
                    CommandResult(
                        success = false,
                        error = "LLM server not running. Please start llamafile server in Termux.",
                        method = ExecutionMethod.HTTP
                    )
                }
                else -> {
                    CommandResult(
                        success = false,
                        error = "HTTP $responseCode: $response",
                        method = ExecutionMethod.HTTP
                    )
                }
            }
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(tag, "Command generation timeout", e)
            CommandResult(
                success = false,
                error = "Command generation timed out. Try a simpler request.",
                method = ExecutionMethod.HTTP
            )
        } catch (e: java.net.ConnectException) {
            Log.e(tag, "Connection error", e)
            CommandResult(
                success = false,
                error = "Cannot connect to server. Make sure llamafile and proxy are running in Termux.",
                method = ExecutionMethod.HTTP
            )
        } catch (e: Exception) {
            Log.e(tag, "Command generation error", e)
            CommandResult(
                success = false,
                error = "Error: ${e.message}",
                method = ExecutionMethod.HTTP
            )
        }
    }

    /**
     * Execute a shell command
     * ENHANCED: Now tries multiple methods in order:
     * 1. Termux intent (direct execution)
     * 2. Script file (queued execution)
     * 3. HTTP proxy (fallback)
     */
    suspend fun executeCommand(command: String): CommandResult = withContext(Dispatchers.IO) {
        Log.d(tag, "Executing command: $command")
        
        // Method 1: Try Termux intent (fastest, most reliable)
        if (context != null && isTermuxInstalled()) {
            try {
                val success = executeViaTermuxIntent(command)
                if (success) {
                    Log.d(tag, "Command executed via Termux intent")
                    return@withContext CommandResult(
                        success = true,
                        output = "Command executed via Termux",
                        exitCode = 0,
                        method = ExecutionMethod.TERMUX_INTENT
                    )
                }
            } catch (e: Exception) {
                Log.w(tag, "Termux intent failed, trying script file", e)
            }
        }
        
        // Method 2: Try script file (works when Termux is installed)
        if (context != null && isTermuxInstalled()) {
            try {
                val result = executeViaScriptFile(command)
                if (result.success) {
                    Log.d(tag, "Command queued via script file")
                    return@withContext result
                }
            } catch (e: Exception) {
                Log.w(tag, "Script file failed, trying HTTP", e)
            }
        }
        
        // Method 3: Fall back to HTTP proxy
        try {
            val url = URL("$baseUrl/execute")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 10000
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

            when (responseCode) {
                200 -> {
                    val json = JSONObject(response)
                    val success = json.getBoolean("success")
                    val output = json.optString("output", "")
                    val exitCode = json.optInt("exit_code", -1)
                    
                    Log.d(tag, "Command executed via HTTP: success=$success, exitCode=$exitCode")
                    
                    CommandResult(
                        success = success,
                        output = output,
                        exitCode = exitCode,
                        method = ExecutionMethod.HTTP
                    )
                }
                else -> {
                    CommandResult(
                        success = false,
                        error = "HTTP $responseCode: $response",
                        exitCode = -1,
                        method = ExecutionMethod.HTTP
                    )
                }
            }
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(tag, "Command execution timeout", e)
            CommandResult(
                success = false,
                error = "Command execution timed out (60s limit). The command may still be running in background.",
                exitCode = -1,
                method = ExecutionMethod.HTTP
            )
        } catch (e: java.net.ConnectException) {
            Log.e(tag, "Connection error - all methods failed", e)
            CommandResult(
                success = false,
                error = "Cannot execute command. Install Termux or start the HTTP proxy server.",
                exitCode = -1,
                method = ExecutionMethod.HTTP
            )
        } catch (e: Exception) {
            Log.e(tag, "Command execution error", e)
            CommandResult(
                success = false,
                error = "Execution error: ${e.message}",
                exitCode = -1,
                method = ExecutionMethod.HTTP
            )
        }
    }
    
    /**
     * Start llamafile server in Termux
     */
    suspend fun startLlamafileServer(): CommandResult {
        val startCommand = "bash ~/start_llamafile_server.sh"
        return executeCommand(startCommand)
    }
    
    /**
     * Start JARVIS proxy server in Termux
     */
    suspend fun startProxyServer(): CommandResult {
        val startCommand = "python3 ~/llamafile_proxy.py &"
        return executeCommand(startCommand)
    }
    
    /**
     * Start complete JARVIS system (llamafile + proxy)
     */
    suspend fun startJarvisSystem(): CommandResult {
        val startCommand = "bash ~/jarvis_server_init.sh"
        return executeCommand(startCommand)
    }
}
