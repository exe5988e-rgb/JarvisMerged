package com.jarvismini.devtools.autobuild

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class FileManagerModule(private val context: Context) {

    companion object {
        private const val TAG               = "FileManagerModule"
        private const val AUTOMATION_DIR    = "/sdcard/ai-automation"
        private const val DOWNLOAD_PATH     = "/sdcard/Download/ai-output.txt"
        private const val AUTOMATION_AI_OUTPUT = "$AUTOMATION_DIR/ai-output.txt"
        private const val BUILD_STATUS_PATH = "$AUTOMATION_DIR/build_status.txt"
        private const val BUILD_COMPLETE_FLAG = "$AUTOMATION_DIR/build_complete.flag"
        private const val ERROR_REPORT_PATH = "$AUTOMATION_DIR/build-error-report.txt"

        // Agent loop paths (Session 20)
        const val AGENT_TASK_PATH       = "$AUTOMATION_DIR/agent_task.txt"
        const val AGENT_DUMP_DOWNLOAD   = "/sdcard/Download/new_dump.txt"
        const val AGENT_STAGED_DIR      = "/data/data/com.termux/files/home/staged"
        const val AGENT_STAGED_OUTPUT   = "$AGENT_STAGED_DIR/ai-output.txt"
    }

    init {
        File(AUTOMATION_DIR).mkdirs()
    }

    // ── Existing methods (unchanged) ──────────────────────────────────────

    suspend fun deleteOldAiOutput(): Boolean = withContext(Dispatchers.IO) {
        try {
            val downloadFile   = File(DOWNLOAD_PATH)
            val automationFile = File(AUTOMATION_AI_OUTPUT)
            if (downloadFile.exists())   { downloadFile.delete();   Log.d(TAG, "Deleted old ai-output.txt from Downloads") }
            if (automationFile.exists()) { automationFile.delete(); Log.d(TAG, "Deleted old ai-output.txt from automation dir") }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete old ai-output.txt", e)
            false
        }
    }

    suspend fun copyAiOutputToAutomationDir(): Boolean = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(DOWNLOAD_PATH)
            val destFile   = File(AUTOMATION_AI_OUTPUT)
            if (!sourceFile.exists()) {
                Log.e(TAG, "Source file does not exist: $DOWNLOAD_PATH")
                return@withContext false
            }
            sourceFile.copyTo(destFile, overwrite = true)
            Log.d(TAG, "Copied ai-output.txt to automation directory")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy ai-output.txt", e)
            false
        }
    }

    suspend fun readBuildStatus(): String = withContext(Dispatchers.IO) {
        try {
            val statusFile = File(BUILD_STATUS_PATH)
            if (!statusFile.exists()) return@withContext "UNKNOWN"
            statusFile.readText().trim().uppercase()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read build status", e)
            "ERROR"
        }
    }

    suspend fun clearBuildFlags(): Boolean = withContext(Dispatchers.IO) {
        try {
            File(BUILD_STATUS_PATH).delete()
            File(BUILD_COMPLETE_FLAG).delete()
            File(ERROR_REPORT_PATH).delete()
            Log.d(TAG, "Cleared build flags")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear build flags", e)
            false
        }
    }

    suspend fun errorReportExists(): Boolean = withContext(Dispatchers.IO) {
        File(ERROR_REPORT_PATH).exists()
    }

    // ── Agent loop methods (Session 20) ───────────────────────────────────

    /** Returns task string and dump filename if agent_task.txt exists, else null. */
    suspend fun readAgentTask(): Pair<String, String>? = withContext(Dispatchers.IO) {
        try {
            val f = File(AGENT_TASK_PATH)
            if (!f.exists()) return@withContext null
            val lines = f.readText().trim().lines()
            val task     = lines.getOrNull(0)?.trim() ?: return@withContext null
            val dumpName = lines.getOrNull(1)?.trim() ?: "new_dump.txt"
            if (task.isEmpty()) return@withContext null
            Pair(task, dumpName)
        } catch (e: Exception) {
            Log.e(TAG, "readAgentTask failed", e)
            null
        }
    }

    suspend fun deleteAgentTask(): Unit = withContext(Dispatchers.IO) {
        runCatching { File(AGENT_TASK_PATH).delete() }
        Log.d(TAG, "Deleted agent_task.txt")
    }

    suspend fun agentDumpExists(dumpName: String): Boolean = withContext(Dispatchers.IO) {
        File("/sdcard/Download/$dumpName").exists()
    }

    /** Delete stale downloaded ai-output.txt before a new agent download cycle. */
    suspend fun deleteOldAgentOutput(): Unit = withContext(Dispatchers.IO) {
        runCatching { File(DOWNLOAD_PATH).delete() }
        Log.d(TAG, "Deleted old agent ai-output.txt from Downloads")
    }

    /**
     * Copy downloaded ai-output.txt → ~/staged/ai-output.txt on Phone B
     * so Phone A can pull_from_bridge("ai-output.txt").
     */
    suspend fun stageOutputForPhoneA(): Boolean = withContext(Dispatchers.IO) {
        try {
            val src = File(DOWNLOAD_PATH)
            if (!src.exists()) {
                Log.e(TAG, "stageOutputForPhoneA: $DOWNLOAD_PATH not found")
                return@withContext false
            }
            File(AGENT_STAGED_DIR).mkdirs()
            val dst = File(AGENT_STAGED_OUTPUT)
            src.copyTo(dst, overwrite = true)
            Log.d(TAG, "Staged ai-output.txt → $AGENT_STAGED_OUTPUT (${dst.length()} bytes)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "stageOutputForPhoneA failed", e)
            false
        }
    }
}
