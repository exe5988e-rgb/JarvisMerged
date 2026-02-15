package com.jarvismini.llm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.io.File
import java.io.RandomAccessFile

/**
 * FIXED v3: LAN Pairing Support with Auth Token
 * 
 * New Features:
 * - Reads paired server IP/port from SharedPreferences
 * - Includes Bearer token authentication for paired devices
 * - Falls back to localhost if not paired
 * - Logs connection details for debugging
 * 
 * Previous fixes:
 * - Adaptive polling eliminates "Unknown error"
 * - File sync delays handled
 * - Multiple validation retries
 */
class TermuxLlamaClient(
    private val context: Context,
    serverHostOverride: String? = null,
    serverPortOverride: Int? = null
) {
    private val tag = "TermuxLlamaClient"
    
    // Load pairing preferences
    private val prefs = context.getSharedPreferences("jarvis_lan", Context.MODE_PRIVATE)
    private val isPaired = prefs.getBoolean("is_paired", false)
    
    // Use override if provided, otherwise check pairing, fallback to localhost
    private val serverHost: String = serverHostOverride ?: run {
        if (isPaired) {
            val savedIp = prefs.getString("server_ip", null)
            Log.d(tag, "Loaded paired IP: $savedIp")
            savedIp ?: "127.0.0.1"
        } else {
            "127.0.0.1"
        }
    }
    
    private val serverPort: Int = serverPortOverride ?: run {
        if (isPaired) {
            val savedPort = prefs.getString("server_port", null)?.toIntOrNull()
            Log.d(tag, "Loaded paired port: $savedPort")
            savedPort ?: 8888
        } else {
            8888
        }
    }
    
    private val authToken: String? = if (isPaired) {
        val token = prefs.getString("auth_token", null)
        Log.d(tag, "Loaded auth token: ${token?.take(8)}...")
        token
    } else {
        null
    }
    
    private val baseUrl = "http://$serverHost:$serverPort"
    
    init {
        Log.d(tag, "Initialized - Server: $baseUrl, Paired: $isPaired, Has Token: ${authToken != null}")
    }
    
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

    suspend fun checkHealth(): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = java.net.URL("$baseUrl/health")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            
            // ðŸ†• Add auth token if paired
            if (authToken != null) {
                connection.setRequestProperty("Authorization", "Bearer $authToken")
            }
            
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val isHealthy = connection.responseCode == 200
            Log.d(tag, "Health check: $isHealthy (Server: $baseUrl)")
            isHealthy
        } catch (e: Exception) {
            Log.e(tag, "Health check failed: ${e.message}")
            false
        }
    }

    suspend fun chat(query: String, timeoutSeconds: Int = 60): CommandResult = withContext(Dispatchers.IO) {
        try {
            Log.d(tag, "Chat request to $baseUrl: $query")
            
            val url = java.net.URL("$baseUrl/chat_sync")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            
            // ðŸ†• Add auth token if paired
            if (authToken != null) {
                connection.setRequestProperty("Authorization", "Bearer $authToken")
                Log.d(tag, "Added auth token to request")
            }
            
            connection.connectTimeout = 10000
            connection.readTimeout = (timeoutSeconds + 10) * 1000
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

            Log.d(tag, "Chat response code: $responseCode")

            when (responseCode) {
                200 -> {
                    val json = org.json.JSONObject(response)
                    if (json.getBoolean("success")) {
                        val chatResponse = json.getString("response")
                        Log.d(tag, "Chat success: ${chatResponse.take(100)}...")
                        CommandResult(success = true, response = chatResponse)
                    } else {
                        val errorMsg = json.optString("error", "Unknown error")
                        Log.e(tag, "Chat error: $errorMsg")
                        CommandResult(success = false, error = errorMsg)
                    }
                }
                401 -> {
                    Log.e(tag, "Authentication failed - invalid or missing token")
                    CommandResult(success = false, error = "Unauthorized - please pair device again")
                }
                else -> {
                    Log.e(tag, "HTTP $responseCode: $response")
                    CommandResult(success = false, error = "Server error: $responseCode")
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Chat request failed", e)
            CommandResult(success = false, error = "Connection failed: ${e.message}")
        }
    }

    suspend fun generateCommand(query: String, timeoutSeconds: Int = 30): CommandResult = withContext(Dispatchers.IO) {
        try {
            Log.d(tag, "Generate command: $query")
            
            val url = java.net.URL("$baseUrl/generate_sync")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            
            // ðŸ†• Add auth token if paired
            if (authToken != null) {
                connection.setRequestProperty("Authorization", "Bearer $authToken")
            }
            
            connection.connectTimeout = 10000
            connection.readTimeout = (timeoutSeconds + 10) * 1000
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

            Log.d(tag, "Generate response code: $responseCode")

            when (responseCode) {
                200 -> {
                    val json = org.json.JSONObject(response)
                    if (json.getBoolean("success")) {
                        val command = json.getString("command")
                        Log.d(tag, "Generated command: $command")
                        CommandResult(success = true, command = command, response = command)
                    } else {
                        val errorMsg = json.optString("error", "Unknown error")
                        Log.e(tag, "Generate error: $errorMsg")
                        CommandResult(success = false, error = errorMsg)
                    }
                }
                401 -> {
                    Log.e(tag, "Authentication failed - invalid or missing token")
                    CommandResult(success = false, error = "Unauthorized - please pair device again")
                }
                else -> {
                    Log.e(tag, "HTTP $responseCode: $response")
                    CommandResult(success = false, error = "Server error: $responseCode")
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Generate request failed", e)
            CommandResult(success = false, error = "Connection failed: ${e.message}")
        }
    }

    suspend fun executeCommand(command: String, timeoutSeconds: Int = 30): CommandResult = withContext(Dispatchers.IO) {
        try {
            Log.d(tag, "Execute command: $command")
            
            val url = java.net.URL("$baseUrl/execute")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            
            // ðŸ†• Add auth token if paired
            if (authToken != null) {
                connection.setRequestProperty("Authorization", "Bearer $authToken")
            }
            
            connection.connectTimeout = 10000
            connection.readTimeout = (timeoutSeconds + 10) * 1000
            connection.doOutput = true

            val jsonBody = """{"command": "$command"}"""
            
            connection.outputStream.use { os ->
                os.write(jsonBody.toByteArray())
            }

            val responseCode = connection.responseCode
            val response = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }

            Log.d(tag, "Execute response code: $responseCode")

            when (responseCode) {
                200 -> {
                    val json = org.json.JSONObject(response)
                    val success = json.getBoolean("success")
                    val output = json.optString("output", "")
                    val exitCode = json.optInt("exit_code", -1)
                    
                    Log.d(tag, "Execution ${if (success) "succeeded" else "failed"}: exit $exitCode")
                    CommandResult(
                        success = success,
                        output = output,
                        exitCode = exitCode,
                        command = command
                    )
                }
                401 -> {
                    Log.e(tag, "Authentication failed - invalid or missing token")
                    CommandResult(success = false, error = "Unauthorized - please pair device again")
                }
                else -> {
                    Log.e(tag, "HTTP $responseCode: $response")
                    CommandResult(success = false, error = "Server error: $responseCode")
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Execute request failed", e)
            CommandResult(success = false, error = "Connection failed: ${e.message}")
        }
    }

    // File-based execution methods (keep existing implementation)
    suspend fun generateAndExecuteCommand(query: String): CommandResult = withContext(Dispatchers.IO) {
        val generateResult = generateCommand(query)
        if (!generateResult.success || generateResult.command == null) {
            return@withContext generateResult
        }
        
        executeCommand(generateResult.command)
    }

    private fun readFileWithRetry(file: File, maxRetries: Int = 3): String {
        repeat(maxRetries) { attempt ->
            try {
                RandomAccessFile(file, "r").use { raf ->
                    raf.fd.sync()
                    val content = raf.readLine() ?: ""
                    if (content.isNotBlank()) {
                        return content
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "Read attempt ${attempt + 1} failed: ${e.message}")
            }
            Thread.sleep(20)
        }
        return ""
    }

    private suspend fun waitForResult(timeoutSeconds: Int): String = withContext(Dispatchers.IO) {
        val resultFile = File(RESULT_FILE)
        val startTime = System.currentTimeMillis()
        val timeoutMs = timeoutSeconds * 1000L
        
        var checkCount = 0
        var lastSize = -1L
        var sameCount = 0
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            checkCount++
            
            if (resultFile.exists()) {
                val currentSize = resultFile.length()
                
                if (currentSize > 0) {
                    if (currentSize == lastSize) {
                        sameCount++
                        if (sameCount >= 3) {
                            val content = readFileWithRetry(resultFile)
                            if (content.isNotBlank()) {
                                val elapsed = System.currentTimeMillis() - startTime
                                Log.d(tag, "Result ready after ${elapsed}ms (${checkCount} checks)")
                                return@withContext content
                            }
                        }
                    } else {
                        sameCount = 0
                    }
                    lastSize = currentSize
                }
            }
            
            val interval = when {
                checkCount <= 10 -> 50L
                checkCount <= 30 -> 100L
                else -> 200L
            }
            delay(interval)
        }
        
        val elapsed = System.currentTimeMillis() - startTime
        "Timeout after ${elapsed}ms (${checkCount} checks, file exists: ${resultFile.exists()})"
    }
}
