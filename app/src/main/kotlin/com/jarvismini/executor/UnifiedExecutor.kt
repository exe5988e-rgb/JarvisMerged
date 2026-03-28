package com.jarvismini.executor

import android.content.Context
import android.util.Log
import com.jarvismini.api.RequestSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

interface UnifiedExecutor {
    suspend fun execute(request: ExecutionRequest): ExecutionResult
}

data class ExecutionRequest(
    val command: String,
    val source: RequestSource,
    val timeout: Long = 30000L,
    val maxOutputSize: Int = 10000
)

data class ExecutionResult(
    val success: Boolean,
    val output: String,
    val exitCode: Int,
    val executionTime: Long,
    val source: RequestSource
)

class FileBasedExecutor(private val context: Context) : UnifiedExecutor {
    
    private val tag = "FileBasedExecutor"
    private val workDir = File("/sdcard/jarvis")
    private val commandFile = File(workDir, "command.txt")
    private val resultFile = File(workDir, "result.txt")
    private val statusFile = File(workDir, "status.txt")
    
    override suspend fun execute(request: ExecutionRequest): ExecutionResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        try {
            Log.d(tag, "Executing: ${request.command}")
            
            if (!workDir.exists()) {
                workDir.mkdirs()
            }
            
            if (!isDaemonRunning()) {
                return@withContext ExecutionResult(
                    success = false,
                    output = "Termux daemon not running. Start jarvis_executor.sh",
                    exitCode = -1,
                    executionTime = System.currentTimeMillis() - startTime,
                    source = request.source
                )
            }
            
            resultFile.writeText("")
            commandFile.writeText(request.command)
            
            val result = withTimeout(request.timeout) {
                waitForResult()
            }
            
            val executionTime = System.currentTimeMillis() - startTime
            val parts = result.split("|", limit = 2)
            val exitCode = parts.getOrNull(0)?.toIntOrNull() ?: -1
            val output = parts.getOrNull(1) ?: result
            
            val truncatedOutput = if (output.length > request.maxOutputSize) {
                output.take(request.maxOutputSize) + "\n... (truncated)"
            } else {
                output
            }
            
            ExecutionResult(
                success = exitCode == 0,
                output = truncatedOutput.ifEmpty { "Command completed" },
                exitCode = exitCode,
                executionTime = executionTime,
                source = request.source
            )
            
        } catch (e: Exception) {
            Log.e(tag, "Execution failed", e)
            ExecutionResult(
                success = false,
                output = "Error: ${e.message}",
                exitCode = -1,
                executionTime = System.currentTimeMillis() - startTime,
                source = request.source
            )
        }
    }
    
    private fun isDaemonRunning(): Boolean {
        return statusFile.exists() && statusFile.readText().trim() == "ready"
    }
    
    private suspend fun waitForResult(): String {
        repeat(300) {
            if (resultFile.exists() && resultFile.length() > 0) {
                val content = resultFile.readText().trim()
                if (content.isNotEmpty()) {
                    return content
                }
            }
            delay(100)
        }
        throw Exception("Timeout waiting for result")
    }
}
