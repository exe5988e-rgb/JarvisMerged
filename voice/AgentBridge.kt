package com.jarvismini.voice

/**
 * AgentBridge.kt
 *
 * Sends a task string to the Jarvis agent loop and waits for the result.
 *
 * Two transport modes (auto-detected):
 *
 *   Mode A — File IPC (same device, Phone A running both app + Termux)
 *     Writes task to /sdcard/jarvis/agent_task.txt
 *     Polls       /sdcard/jarvis/agent_result.txt
 *     This reuses the EXACT same file IPC pattern as FileBasedExecutor
 *     (already in UnifiedExecutor.kt)
 *
 *   Mode B — LAN HTTP (two device setup)
 *     POST http://<PhoneA_IP>:8891/agent_task
 *     {"task": "Open WhatsApp"}
 *     → {"status": "done", "result": "Opened WhatsApp successfully"}
 *     Phone A runs agent_http_bridge.py (new, built in ai-output.txt)
 *
 * Config file: /sdcard/jarvis/agent_bridge.conf
 *   mode=file          ← default, same device
 *   mode=lan
 *   lan_host=192.168.29.48
 *   lan_port=8891
 */

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class AgentBridge(private val context: Context) {

    companion object {
        private const val TAG          = "AgentBridge"
        private const val JARVIS_DIR   = "/sdcard/jarvis"
        private const val TASK_FILE    = "$JARVIS_DIR/agent_task.txt"
        private const val RESULT_FILE  = "$JARVIS_DIR/agent_result.txt"
        private const val STATUS_FILE  = "$JARVIS_DIR/agent_status.txt"
        private const val CONF_FILE    = "$JARVIS_DIR/agent_bridge.conf"
        private const val TIMEOUT_MS   = 120_000L   // 2 minutes max per task
        private const val POLL_MS      = 500L
    }

    // ── Config ────────────────────────────────────────────────────────────────

    private data class BridgeConfig(
        val mode: String    = "file",   // "file" or "lan"
        val lanHost: String = "192.168.29.48",
        val lanPort: Int    = 8891
    )

    private fun loadConfig(): BridgeConfig {
        val f = File(CONF_FILE)
        if (!f.exists()) return BridgeConfig()
        val props = f.readLines()
            .filter { it.contains("=") }
            .associate { it.substringBefore("=").trim() to it.substringAfter("=").trim() }
        return BridgeConfig(
            mode    = props["mode"]     ?: "file",
            lanHost = props["lan_host"] ?: "192.168.29.48",
            lanPort = props["lan_port"]?.toIntOrNull() ?: 8891
        )
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Send a task to the agent loop and wait for the result.
     * Suspends until complete or timeout.
     *
     * @param task  Natural language task, e.g. "Open WhatsApp and message Mom"
     * @return      Human-readable result string for TTS
     */
    suspend fun sendTask(task: String): String = withContext(Dispatchers.IO) {
        val config = loadConfig()
        Log.i(TAG, "sendTask mode=${config.mode}: $task")

        return@withContext when (config.mode) {
            "lan"  -> sendTaskLan(task, config)
            else   -> sendTaskFile(task)
        }
    }

    // ── Mode A: File IPC ──────────────────────────────────────────────────────

    private suspend fun sendTaskFile(task: String): String {
        val dir = File(JARVIS_DIR)
        if (!dir.exists()) dir.mkdirs()

        val taskFile   = File(TASK_FILE)
        val resultFile = File(RESULT_FILE)
        val statusFile = File(STATUS_FILE)

        // Clear previous result
        resultFile.writeText("")
        statusFile.writeText("pending")

        // Write task — agent_controller.py polls this
        taskFile.writeText(task)
        Log.d(TAG, "Wrote task to $TASK_FILE")

        // Poll for result
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            delay(POLL_MS)

            if (resultFile.exists() && resultFile.length() > 0) {
                val content = resultFile.readText().trim()
                if (content.isNotEmpty() && content != "pending") {
                    Log.i(TAG, "Got file result: $content")
                    resultFile.writeText("")   // clear for next task
                    return content
                }
            }
        }

        return "Task timed out. The agent may still be running."
    }

    // ── Mode B: LAN HTTP ──────────────────────────────────────────────────────

    private suspend fun sendTaskLan(task: String, config: BridgeConfig): String {
        val url = "http://${config.lanHost}:${config.lanPort}/agent_task"
        Log.d(TAG, "POST $url")

        return try {
            val body = JSONObject().apply { put("task", task) }.toString()
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod  = "POST"
                connectTimeout = 5_000
                readTimeout    = TIMEOUT_MS.toInt()
                doOutput       = true
                setRequestProperty("Content-Type", "application/json")
            }
            conn.outputStream.use { it.write(body.toByteArray()) }

            val response = conn.inputStream.bufferedReader().readText()
            val json     = JSONObject(response)

            if (json.optBoolean("success", false)) {
                json.optString("result", "Done")
            } else {
                json.optString("error", "Agent returned an error")
            }
        } catch (e: Exception) {
            Log.e(TAG, "LAN bridge error: ${e.message}")
            "Could not reach agent on Phone A. Check WiFi."
        }
    }
}
