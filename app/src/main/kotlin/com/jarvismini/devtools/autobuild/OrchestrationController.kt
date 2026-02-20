package com.jarvismini.devtools.autobuild

import android.content.Context
import android.util.Log
import com.jarvismini.devtools.autobuild.models.AutoBuildState
import com.jarvismini.devtools.autobuild.models.BuildResult
import com.jarvismini.devtools.autobuild.models.ErrorLogBundle
import com.jarvismini.devtools.autobuild.models.LoopState
import kotlinx.coroutines.delay

/**
 * State machine + main loop.
 *
 * Full state flow:
 *
 *   WAITING_FOR_RESPONSE
 *     → EXTRACTING_CODE
 *     → WRITING_OUTPUT             (writes /sdcard/ai-automation/ai-output.txt)
 *     → TRIGGERING_BUILD           (Termux git push → Ai-codegen → main → Apk.yml)
 *     → WAITING_FOR_BUILD          (polls build_complete.flag)
 *         ├── SUCCESS → BUILD_SUCCEEDED → exit
 *         └── FAILURE → CHECKING_ERROR_FRESHNESS
 *                          ├── STALE  → SUBMITTING_PROMPT (no attachment, nudge prompt)
 *                          └── NEW    → READING_ERROR_LOGS (git pull already done by Termux)
 *                                         → ATTACHING_FILES (error_summary + error_files)
 *                                         → SUBMITTING_PROMPT
 *                                             → WAITING_FOR_RESPONSE  (loop)
 */
class OrchestrationController(
    context: Context,
    private val uiWatcher: UIWatcherModule
) {
    companion object {
        private const val TAG = "DevTools:Orchestrator"

        const val LOOP_MAX_ITERATIONS    = 50
        const val MAX_RETRIES_PER_STATE  = 3
        const val BETWEEN_ITER_DELAY_MS  = 500L

        // Prompt when stale errors: same logs as last time, no file attachment
        const val STALE_ERROR_PROMPT =
            "The build is still failing with the same errors as the previous attempt. " +
            "Please try a different approach or check for structural issues."

        // Prompt when fresh errors are being attached
        const val FRESH_ERROR_PROMPT =
            "The build failed. The error logs are attached below. " +
            "Please analyze the errors and provide corrected code."
    }

    private val fm       = FileManagerModule()
    private val termux   = TermuxBridgeModule(context)
    private val notifier = BuildNotifier(context)

    private var retryCount  = 0
    private var errorBundle: ErrorLogBundle? = null

    @Volatile private var stopRequested = false

    fun requestStop() { stopRequested = true }

    // ── Main loop ─────────────────────────────────────────────────────────────

    suspend fun runLoop() {
        val checkpoint = fm.loadState()
        var state      = checkpoint?.state    ?: AutoBuildState.WAITING_FOR_RESPONSE
        var iteration  = checkpoint?.iteration ?: 0
        stopRequested  = false

        log("Loop started — state=$state iter=$iteration")
        notifier.update(iteration, state)

        while (iteration < LOOP_MAX_ITERATIONS && !stopRequested) {
            fm.saveState(LoopState(state, iteration))

            state = when (state) {

                AutoBuildState.IDLE ->
                    AutoBuildState.WAITING_FOR_RESPONSE

                AutoBuildState.WAITING_FOR_RESPONSE -> {
                    val done = uiWatcher.waitForResponseComplete()
                    if (done) AutoBuildState.EXTRACTING_CODE
                    else      timeout(state)
                }

                AutoBuildState.EXTRACTING_CODE -> {
                    val root   = uiWatcher.service.rootInActiveWindow
                    val blocks = uiWatcher.extractCodeBlocks(root)
                    if (blocks.isNotEmpty()) {
                        fm.writeAiOutput(
                            blocks.joinToString("\n\n// ===== next block =====\n\n")
                        )
                        log("Code extracted (${blocks.size} block(s)) → ai-output.txt written")
                        AutoBuildState.TRIGGERING_BUILD
                    } else {
                        retry(state)
                    }
                }

                // Explicit state kept for crash-recovery resume
                AutoBuildState.WRITING_OUTPUT ->
                    AutoBuildState.TRIGGERING_BUILD

                AutoBuildState.TRIGGERING_BUILD -> {
                    log("Triggering build (iteration=$iteration) via Termux…")
                    termux.triggerBuild()
                    retryCount = 0
                    AutoBuildState.WAITING_FOR_BUILD
                }

                AutoBuildState.WAITING_FOR_BUILD -> {
                    log("Polling for Apk.yml completion…")
                    when (termux.pollForCompletion()) {
                        BuildResult.SUCCESS -> AutoBuildState.BUILD_SUCCEEDED
                        BuildResult.FAILURE -> AutoBuildState.CHECKING_ERROR_FRESHNESS
                        BuildResult.TIMEOUT -> timeout(state)
                    }
                }

                AutoBuildState.BUILD_SUCCEEDED -> {
                    log("✅ Build succeeded after $iteration iteration(s)")
                    notifier.success(iteration)
                    return
                }

                // ── Freshness gate ────────────────────────────────────────────
                // Error logs are already on disk (git pull done by build_runner.sh).
                // Only forward to READING_ERROR_LOGS if they are genuinely new.
                AutoBuildState.CHECKING_ERROR_FRESHNESS -> {
                    if (fm.hasNewErrors(iteration)) {
                        log("New error logs detected — reading logs")
                        AutoBuildState.READING_ERROR_LOGS
                    } else {
                        log("Error logs unchanged — skipping attachment, sending nudge prompt")
                        uiWatcher.fillPromptField(STALE_ERROR_PROMPT)
                        iteration++
                        AutoBuildState.SUBMITTING_PROMPT
                    }
                }
                // ─────────────────────────────────────────────────────────────

                AutoBuildState.READING_ERROR_LOGS -> {
                    val bundle = fm.readErrorLogs()
                    if (bundle != null) {
                        // Save fingerprint NOW — before next build could overwrite files
                        fm.saveErrorFingerprint(fm.computeErrorFingerprint(iteration))
                        errorBundle = bundle
                        log("Error logs read (summary=${bundle.errorSummaryContent.length} chars)")
                        AutoBuildState.ATTACHING_FILES
                    } else {
                        log("Error logs missing after FAILURE signal — retrying", isError = true)
                        timeout(state)
                    }
                }

                AutoBuildState.ATTACHING_FILES -> {
                    val ok = attachErrorFiles()
                    if (ok) {
                        iteration++
                        AutoBuildState.SUBMITTING_PROMPT
                    } else {
                        retry(state)
                    }
                }

                AutoBuildState.SUBMITTING_PROMPT -> {
                    uiWatcher.tapSendButton()
                    delay(UIWatcherModule.POST_TAP_DELAY_MS)
                    AutoBuildState.WAITING_FOR_RESPONSE
                }

                AutoBuildState.TIMEOUT_ERROR -> {
                    val backoff = 1_000L * (1 shl minOf(retryCount, 3))
                    log("Timeout recovery — waiting ${backoff}ms (retry=$retryCount)", isError = true)
                    delay(backoff)
                    if (++retryCount > MAX_RETRIES_PER_STATE) {
                        notifier.error("Too many retries — stopped at $state")
                        return
                    }
                    AutoBuildState.WAITING_FOR_RESPONSE
                }
            }

            // Update static snapshot BEFORE invoking the UI callback so that
            // if MainActivity resumes during this exact window it sees the new state.
            AutoBuildService.currentState     = state
            AutoBuildService.currentIteration = iteration

            notifier.update(iteration, state)
            AutoBuildService.onStatusUpdate?.invoke(iteration, state)
            delay(BETWEEN_ITER_DELAY_MS)
        }

        if (stopRequested) log("Loop stopped by user request")
        else log("Max iterations ($LOOP_MAX_ITERATIONS) reached — stopping", isError = true)
        notifier.error(if (stopRequested) "Stopped by user" else "Max iterations reached")
    }

    // ── File attachment ───────────────────────────────────────────────────────

    private suspend fun attachErrorFiles(): Boolean {
        // Attach error_files.txt
        if (!uiWatcher.tapAddFilesButton()) { log("tapAddFilesButton failed (1)", isError = true); return false }
        if (!uiWatcher.selectFileInPicker(FileManagerModule.ERROR_FILES_FILE.name)) {
            log("selectFileInPicker(error_files.txt) failed", isError = true); return false
        }
        delay(UIWatcherModule.POST_TAP_DELAY_MS)

        // Attach error_summary.txt
        if (!uiWatcher.tapAddFilesButton()) { log("tapAddFilesButton failed (2)", isError = true); return false }
        if (!uiWatcher.selectFileInPicker(FileManagerModule.ERROR_SUMMARY_FILE.name)) {
            log("selectFileInPicker(error_summary.txt) failed", isError = true); return false
        }
        delay(UIWatcherModule.POST_TAP_DELAY_MS)

        // Fill the prompt text
        uiWatcher.fillPromptField(FRESH_ERROR_PROMPT)
        log("Both error files attached, prompt filled")
        return true
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun timeout(state: AutoBuildState): AutoBuildState {
        log("Timeout in $state", isError = true)
        return AutoBuildState.TIMEOUT_ERROR
    }

    private fun retry(state: AutoBuildState): AutoBuildState {
        return if (++retryCount <= MAX_RETRIES_PER_STATE) {
            log("Retrying $state (attempt $retryCount)")
            state
        } else {
            log("Max retries exceeded for $state", isError = true)
            AutoBuildState.TIMEOUT_ERROR
        }
    }

    private fun log(msg: String, isError: Boolean = false) {
        if (isError) Log.e(TAG, msg) else Log.d(TAG, msg)
        AutoBuildService.onLogLine?.invoke(msg, isError)
    }
}
