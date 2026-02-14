package com.jarvismini.llm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.io.File
import java.io.RandomAccessFile

/**
 * FIXED v2: Adaptive polling eliminates "Unknown error"
 * 
 * Root cause: File sync delays between executor write and Android read
 * Solution: Adaptive polling with immediate first check
 * 
 * Changes:
 * - Start checking immediately (no 100ms initial delay)
 * - Adaptive intervals: 50ms → 100ms → 200ms (faster detection)
 * - Force file sync with RandomAccessFile
 * - Multiple validation retries for partial writes
 * - Better error messages with actual timing data
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

    suspend fun executeCommandViaServer(command: String): CommandResult = withContext(Dispatchers.IO) {
        try {
            Log.d(tag, "Execute via server: $command")
            
            val url = java.net.URL("$baseUrl/execute")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 10000
            connection.readTimeout = 70000
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

            when (responseCode) {
                200 -> {
                    val json = org.json.JSONObject(response)
                    if (json.getBoolean("success")) {
                        val output = json.optString("output", "")
                        val exitCode = json.optInt("exit_code", 0)
                        Log.d(tag, "Server execution success: exitCode=$exitCode")
                        CommandResult(
                            success = exitCode == 0,
                            output = output.ifEmpty { "✓ Command completed" },
                            exitCode = exitCode,
                            method = "server"
                        )
                    } else {
                        val error = json.optString("error", "Unknown error")
                        Log.e(tag, "Server execution failed: $error")
                        CommandResult(success = false, error = error, method = "server")
                    }
                }
                else -> {
                    Log.e(tag, "HTTP error: $responseCode")
                    CommandResult(success = false, error = "HTTP $responseCode", method = "server")
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Server execution exception", e)
            CommandResult(success = false, error = e.message ?: "Server error", method = "server")
        }
    }

    /**
     * Read file with forced sync to get latest content
     */
    private fun readFileWithSync(file: File): String? {
        return try {
            // Use RandomAccessFile to force cache invalidation
            RandomAccessFile(file, "r").use { raf ->
                val content = ByteArray(raf.length().toInt())
                raf.readFully(content)
                String(content).trim()
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * FIXED v2: Adaptive polling eliminates file sync delays
     * 
     * Timing strategy:
     * - Phase 1: 0-500ms   → Check every 50ms  (10 attempts, catches fast commands)
     * - Phase 2: 500-3000ms → Check every 100ms (25 attempts, catches medium commands)
     * - Phase 3: 3000-60s  → Check every 200ms (285 attempts, long commands)
     * 
     * Total: 320 attempts over 60 seconds with better early detection
     */
    suspend fun executeCommandViaFile(command: String): CommandResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        try {
            Log.d(tag, "========================================")
            Log.d(tag, "Execute via file: $command")
            
            val statusFile = File(STATUS_FILE)
            val commandFile = File(COMMAND_FILE)
            val resultFile = File(RESULT_FILE)
            
            // Check if executor is running
            if (!statusFile.exists()) {
                Log.e(tag, "Status file not found")
                return@withContext CommandResult(
                    success = false,
                    error = "Executor not running.\nRun in Termux:\n  bash ~/jarvis_executor_optimized.sh &",
                    exitCode = -1,
                    method = "file-based"
                )
            }
            
            val status = statusFile.readText().trim()
            Log.d(tag, "Executor status: $status")
            
            if (status != "ready" && status != "executing") {
                return@withContext CommandResult(
                    success = false,
                    error = "Executor status: $status\n\nRestart executor in Termux",
                    exitCode = -1,
                    method = "file-based"
                )
            }
            
            // Clear old result with forced sync
            Log.d(tag, "Clearing old result...")
            if (resultFile.exists()) {
                resultFile.delete()
                delay(30) // File deletion propagation
            }
            resultFile.createNewFile()
            
            // Write empty content with sync
            RandomAccessFile(resultFile, "rw").use { raf ->
                raf.setLength(0)
                raf.fd.sync() // Force sync to storage
            }
            Log.d(tag, "Result file cleared and synced")
            
            // Write command
            commandFile.writeText(command)
            Log.d(tag, "Command written at ${System.currentTimeMillis() - startTime}ms")
            
            // Adaptive polling: NO initial delay, start checking immediately
            var resultContent: String? = null
            var attempts = 0
            var totalDelay = 0L
            
            // Phase 1: Fast polling for quick commands (0-500ms, 50ms interval)
            val phase1Attempts = 10
            for (i in 0 until phase1Attempts) {
                resultContent = readFileWithSync(resultFile)
                
                if (!resultContent.isNullOrEmpty() && resultContent.contains("|")) {
                    val elapsed = System.currentTimeMillis() - startTime
                    Log.d(tag, "✓ Result found in Phase 1 (fast) after ${elapsed}ms")
                    Log.d(tag, "  Attempts: ${i + 1}, Format: ${resultContent.take(50)}...")
                    break
                }
                
                delay(50)
                totalDelay += 50
                attempts++
            }
            
            // Phase 2: Medium polling (500-3000ms, 100ms interval)
            if (resultContent.isNullOrEmpty() || !resultContent.contains("|")) {
                val phase2Attempts = 25
                for (i in 0 until phase2Attempts) {
                    resultContent = readFileWithSync(resultFile)
                    
                    if (!resultContent.isNullOrEmpty() && resultContent.contains("|")) {
                        val elapsed = System.currentTimeMillis() - startTime
                        Log.d(tag, "✓ Result found in Phase 2 (medium) after ${elapsed}ms")
                        Log.d(tag, "  Attempts: ${attempts + i + 1}, Format: ${resultContent.take(50)}...")
                        break
                    }
                    
                    delay(100)
                    totalDelay += 100
                    attempts++
                }
            }
            
            // Phase 3: Slow polling for long commands (3s-60s, 200ms interval)
            if (resultContent.isNullOrEmpty() || !resultContent.contains("|")) {
                val phase3Attempts = 285 // Up to 60s total
                for (i in 0 until phase3Attempts) {
                    resultContent = readFileWithSync(resultFile)
                    
                    if (!resultContent.isNullOrEmpty() && resultContent.contains("|")) {
                        val elapsed = System.currentTimeMillis() - startTime
                        Log.d(tag, "✓ Result found in Phase 3 (slow) after ${elapsed}ms")
                        Log.d(tag, "  Attempts: ${attempts + i + 1}, Format: ${resultContent.take(50)}...")
                        break
                    }
                    
                    delay(200)
                    totalDelay += 200
                    attempts++
                    
                    // Log progress every 5 seconds in Phase 3
                    if (i > 0 && i % 25 == 0) {
                        val elapsed = System.currentTimeMillis() - startTime
                        Log.d(tag, "⏳ Still waiting... ${elapsed}ms elapsed, ${attempts + i} attempts")
                    }
                }
            }
            
            val actualElapsed = System.currentTimeMillis() - startTime
            
            // Final validation
            if (resultContent.isNullOrEmpty() || !resultContent.contains("|")) {
                Log.e(tag, "❌ TIMEOUT or INVALID RESULT")
                Log.e(tag, "  Actual time: ${actualElapsed}ms")
                Log.e(tag, "  Total attempts: $attempts")
                Log.e(tag, "  Result content: '${resultContent?.take(100)}'")
                Log.e(tag, "  Manual check: cat $RESULT_FILE")
                
                return@withContext CommandResult(
                    success = false,
                    error = "No result after ${actualElapsed}ms ($attempts checks).\n\nLast read: '${resultContent?.take(50)}'\n\nManual check:\n  cat $RESULT_FILE",
                    exitCode = -1,
                    method = "file-based"
                )
            }
            
            // Parse result: "exit_code|output"
            val parts = resultContent.split("|", limit = 2)
            
            if (parts.size != 2) {
                Log.e(tag, "❌ Invalid result format: ${resultContent.take(100)}")
                return@withContext CommandResult(
                    success = false,
                    error = "Invalid format after ${actualElapsed}ms.\n\nGot: ${resultContent.take(50)}\n\nExpected: exitcode|output",
                    exitCode = -1,
                    method = "file-based"
                )
            }
            
            val exitCode = parts[0].toIntOrNull() ?: -1
            val output = parts[1]
            
            Log.d(tag, "✓ Execution complete:")
            Log.d(tag, "  Actual time: ${actualElapsed}ms")
            Log.d(tag, "  Total attempts: $attempts")
            Log.d(tag, "  Exit code: $exitCode")
            Log.d(tag, "  Output length: ${output.length} chars")
            Log.d(tag, "  Output preview: ${output.take(100)}")
            Log.d(tag, "========================================")
            
            CommandResult(
                success = exitCode == 0,
                output = output.ifEmpty { "✓ Command completed (no output)" },
                exitCode = exitCode,
                method = "file-based"
            )
            
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            Log.e(tag, "❌ File-based execution exception after ${elapsed}ms", e)
            CommandResult(
                success = false,
                error = "Execution failed after ${elapsed}ms: ${e.message}\n\nCheck:\n  adb logcat | grep TermuxLlama",
                exitCode = -1,
                method = "file-based"
            )
        }
    }

    suspend fun executeCommand(command: String): CommandResult {
        return executeCommandViaFile(command)
    }
    
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
