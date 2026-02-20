package com.jarvismini.devtools.autobuild

import android.util.Log
import com.jarvismini.devtools.autobuild.models.ErrorFingerprint
import com.jarvismini.devtools.autobuild.models.ErrorLogBundle
import com.jarvismini.devtools.autobuild.models.LoopState
import java.io.File
import java.security.MessageDigest

/**
 * All file I/O for the AutoBuild loop.
 *
 * Shared storage layout (all under BASE_DIR):
 *
 *   /sdcard/ai-automation/
 *     ai-output.txt                  Claude code response → pushed by Termux to JarvisMini repo
 *     loop_state.json                Crash-recovery checkpoint
 *     last_error_fingerprint.json    SHA-256 + mtime of last processed error_summary.txt
 *     build_error_logs/
 *       error_summary.txt            Written by Apk.yml on failure, committed + pushed to main
 *       error_files.txt              Written by Apk.yml on failure, committed + pushed to main
 *
 * Termux pulls both files from the JarvisMini repo via `git pull` after each
 * failed Apk.yml run. clearBuildFlags() wipes them before a new build starts
 * but intentionally preserves last_error_fingerprint.json for the freshness check.
 */
class FileManagerModule {

    companion object {
        private const val TAG = "DevTools:FileManager"

        val BASE_DIR             = File("/sdcard/ai-automation")
        val LOG_DIR              = File(BASE_DIR, "build_error_logs")
        val AI_OUTPUT_FILE       = File(BASE_DIR, "ai-output.txt")
        val LOOP_STATE_FILE      = File(BASE_DIR, "loop_state.json")
        val FINGERPRINT_FILE     = File(BASE_DIR, "last_error_fingerprint.json")
        val ERROR_SUMMARY_FILE   = File(LOG_DIR,  "error_summary.txt")
        val ERROR_FILES_FILE     = File(LOG_DIR,  "error_files.txt")
    }

    // ── ai-output.txt ─────────────────────────────────────────────────────────

    /** Atomically write Claude's extracted code to ai-output.txt. */
    fun writeAiOutput(content: String) {
        BASE_DIR.mkdirs()
        val tmp = File(BASE_DIR, "ai-output.tmp")
        tmp.writeText(content)
        tmp.renameTo(AI_OUTPUT_FILE)
        Log.d(TAG, "ai-output.txt written (${content.length} chars)")
    }

    // ── Error log reads ───────────────────────────────────────────────────────

    /**
     * Reads error_summary.txt and error_files.txt from build_error_logs/.
     * These files are committed by Apk.yml on failure and pulled by Termux.
     * Returns null if either file is missing.
     */
    fun readErrorLogs(): ErrorLogBundle? {
        if (!ERROR_SUMMARY_FILE.exists() || !ERROR_FILES_FILE.exists()) {
            Log.w(TAG, "readErrorLogs: files not present in ${LOG_DIR.absolutePath}")
            return null
        }
        return ErrorLogBundle(
            errorFilesContent   = ERROR_FILES_FILE.readText(),
            errorSummaryContent = ERROR_SUMMARY_FILE.readText()
        )
    }

    /** True when error_summary.txt exists — Apk.yml only writes it on failure. */
    fun buildFailed(): Boolean = ERROR_SUMMARY_FILE.exists()

    // ── Build flag management ─────────────────────────────────────────────────

    /**
     * Clears error log files before a new build trigger.
     * Does NOT delete last_error_fingerprint.json — it must survive across
     * build iterations so CHECKING_ERROR_FRESHNESS can compare correctly.
     */
    fun clearBuildFlags() {
        LOG_DIR.listFiles()?.forEach { it.delete() }
        LOG_DIR.mkdirs()
        Log.d(TAG, "build_error_logs/ cleared (fingerprint preserved)")
    }

    /**
     * Deletes the crash-recovery checkpoint so the loop always starts fresh
     * from WAITING_FOR_RESPONSE on the next service connect, rather than
     * resuming from a stale mid-build state (e.g. WAITING_FOR_BUILD) that
     * would poll forever for a flag that will never arrive.
     *
     * Called by AutoBuildService.onServiceConnected().
     */
    fun clearCheckpoint() {
        if (LOOP_STATE_FILE.delete()) {
            Log.d(TAG, "loop_state.json cleared — loop will start fresh")
        }
        // Also clear the build_complete flag so pollForCompletion starts clean
        TermuxBridgeModule.COMPLETE_FLAG.delete()
    }

    // ── Error fingerprint ─────────────────────────────────────────────────────

    fun computeErrorFingerprint(iteration: Int): ErrorFingerprint {
        val mtime = if (ERROR_SUMMARY_FILE.exists()) ERROR_SUMMARY_FILE.lastModified() else 0L
        val bytes = runCatching { ERROR_SUMMARY_FILE.readBytes() }.getOrElse { ByteArray(0) }
        val hash  = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
        return ErrorFingerprint(mtime, hash, iteration)
    }

    fun readErrorFingerprint(): ErrorFingerprint? {
        if (!FINGERPRINT_FILE.exists()) return null
        return ErrorFingerprint.fromJson(FINGERPRINT_FILE.readText())
    }

    fun saveErrorFingerprint(fp: ErrorFingerprint) {
        val tmp = File(BASE_DIR, "fingerprint.tmp")
        tmp.writeText(fp.toJson())
        tmp.renameTo(FINGERPRINT_FILE)
        Log.d(TAG, "Fingerprint saved iter=${fp.buildIteration} hash=${fp.contentHash.take(8)}…")
    }

    /**
     * Returns true when error_summary.txt is genuinely new compared to the
     * stored fingerprint (different mtime OR different SHA-256).
     * Always true on first ever failure (no stored fingerprint).
     */
    fun hasNewErrors(iteration: Int): Boolean {
        if (!ERROR_SUMMARY_FILE.exists()) return false
        val stored  = readErrorFingerprint() ?: return true   // first failure
        val current = computeErrorFingerprint(iteration)
        val isNew   = current.lastModifiedMs != stored.lastModifiedMs ||
                      current.contentHash    != stored.contentHash
        Log.d(TAG, "hasNewErrors=$isNew (stored=${stored.contentHash.take(8)}… current=${current.contentHash.take(8)}…)")
        return isNew
    }

    // ── Loop state ────────────────────────────────────────────────────────────

    fun saveState(state: LoopState) {
        runCatching {
            BASE_DIR.mkdirs()
            LOOP_STATE_FILE.writeText(state.toJson())
        }.onFailure { Log.e(TAG, "saveState failed", it) }
    }

    fun loadState(): LoopState? = runCatching {
        if (!LOOP_STATE_FILE.exists()) null
        else LoopState.fromJson(LOOP_STATE_FILE.readText())
    }.getOrNull()
}
