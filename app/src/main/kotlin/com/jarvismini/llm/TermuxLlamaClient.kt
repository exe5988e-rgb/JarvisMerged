package com.jarvismini.llm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * TermuxLlamaClient with FIFO-based command execution
 * 
 * ARCHITECTURE:
 * 1. LLM (chat, generate) → HTTP proxy (port 8888) → llamafile (port 8080)
 * 2. Command execution → FIFO pipes → jarvis_executor.sh in Termux
 * 
 * REQUIREMENTS:
 * - llamafile server running: bash ~/llamafile_server.sh
 * - HTTP proxy running: python3 ~/llamafile_proxy.py
 * - Executor running: bash ~/jarvis_executor.sh &
 * 
 * FIFO COMMUNICATION:
 * - App writes command → ~/.jarvis/commands (named pipe)
 * - Executor reads command, executes it, writes result
 * - App reads result ← ~/.jarvis/results (named pipe)
 * - Format: "exit_code|output"
 */
class TermuxLlamaClient(
    val context: Context? = null,
    private val serverHost: String = "127.0.0.1",
    private val serverPort: Int = 8888
) {
    private val baseUrl = "http://$serverHost:$serverPort"
    private val tag = "TermuxLlamaClient"
    
    // FIFO paths - must match jarvis_executor.sh
    private val termuxHome = "/data/data/com.termux/files/home"
    private val commandFifo = "$termuxHome/.jarvis/commands"
    private val resultFifo = "$termuxHome/.jarvis/results"

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
            val url = URL("$baseUrl/health")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            connection.responseCode == 200
        } catch (e: Exception) {
            false
        }
    }

    suspend fun chat(query: String, timeoutSeconds: Int = 60): CommandResult = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/chat_sync")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 10000
            connection.readTimeout = (timeoutSeconds + 10) * 1000
            connection.doOutput = true

            OutputStreamWriter(connection.outputStream).use {
                it.write(JSONObject().apply { put("query", query) }.toString())
                it.flush()
            }

            val responseCode = connection.responseCode
            val response = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }

            when (responseCode) {
                200 -> {
                    val json = JSONObject(response)
                    if (json.getBoolean("success")) {
                        CommandResult(success = true, response = json.getString("response"))
                    } else {
                        CommandResult(success = false, error = json.optString("error"))
                    }
                }
                else -> CommandResult(success = false, error = "HTTP $responseCode")
            }
        } catch (e: Exception) {
            CommandResult(success = false, error = e.message ?: "Connection failed")
        }
    }

    suspend fun generateCommand(query: String): CommandResult = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/generate_sync")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 10000
            connection.readTimeout = 40000
            connection.doOutput = true

            OutputStreamWriter(connection.outputStream).use {
                it.write(JSONObject().apply { put("query", query) }.toString())
                it.flush()
            }

            val responseCode = connection.responseCode
            val response = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }

            when (responseCode) {
                200 -> {
                    val json = JSONObject(response)
                    if (json.getBoolean("success")) {
                        val cmd = json.getString("command")
                        CommandResult(success = true, command = cmd, response = cmd)
                    } else {
                        CommandResult(success = false, error = json.optString("error"))
                    }
                }
                else -> CommandResult(success = false, error = "HTTP $responseCode")
            }
        } catch (e: Exception) {
            CommandResult(success = false, error = e.message ?: "Connection failed")
        }
    }

    /**
     * Execute command via FIFO pipes (jarvis_executor.sh)
     * 
     * This is the BEST method because:
     * - Direct Termux execution (not subprocess from Python)
     * - Real output returned to app
     * - No HTTP overhead
     * - Works for all commands including interactive ones
     */
    suspend fun executeCommand(command: String): CommandResult = withContext(Dispatchers.IO) {
        try {
            Log.d(tag, "FIFO execute: $command")
            
            val cmdFile = File(commandFifo)
            val resFile = File(resultFifo)
            
            // Check if executor is running
            if (!cmdFile.exists() || !resFile.exists()) {
                return@withContext CommandResult(
                    success = false,
                    error = "Executor not running.\nRun in Termux:\n  bash ~/jarvis_executor.sh &",
                    exitCode = -1,
                    method = "fifo_not_ready"
                )
            }
            
            // Write command to FIFO
            try {
                cmdFile.writeText("$command\n")
            } catch (e: Exception) {
                return@withContext CommandResult(
                    success = false,
                    error = "Cannot write to FIFO: ${e.message}",
                    exitCode = -1,
                    method = "fifo_write_error"
                )
            }
            
            // Read result with timeout (60 seconds)
            var result: String? = null
            repeat(120) { // 120 * 500ms = 60s
                try {
                    val text = resFile.readText().trim()
                    if (text.isNotEmpty()) {
                        result = text
                        return@repeat
                    }
                } catch (e: Exception) {
                    // Not ready yet
                }
                delay(500)
            }
            
            if (result.isNullOrEmpty()) {
                return@withContext CommandResult(
                    success = false,
                    error = "Timeout (60s) waiting for result",
                    exitCode = -1,
                    method = "fifo_timeout"
                )
            }
            
            // Parse "exit_code|output"
            val parts = result!!.split("|", limit = 2)
            if (parts.size != 2) {
                return@withContext CommandResult(
                    success = false,
                    error = "Invalid result format",
                    exitCode = -1,
                    method = "fifo_parse_error"
                )
            }
            
            val exitCode = parts[0].toIntOrNull() ?: -1
            val output = parts[1]
            
            CommandResult(
                success = exitCode == 0,
                output = output.ifEmpty { "✓ Command completed" },
                exitCode = exitCode,
                method = "fifo"
            )
            
        } catch (e: Exception) {
            Log.e(tag, "FIFO error", e)
            CommandResult(
                success = false,
                error = "FIFO error: ${e.message}",
                exitCode = -1,
                method = "fifo_error"
            )
        }
    }
    
    /**
     * Check if jarvis_executor.sh is running
     */
    suspend fun checkExecutorStatus(): Boolean = withContext(Dispatchers.IO) {
        try {
            File(commandFifo).exists() && File(resultFifo).exists()
        } catch (e: Exception) {
            false
        }
    }
}
