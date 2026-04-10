package com.jarvismini.devtools.autobuild

import android.content.Context
import android.util.Log
import com.jarvismini.devtools.autobuild.models.AutoBuildState
import com.jarvismini.devtools.autobuild.models.BuildResult
import kotlinx.coroutines.delay

class OrchestrationController(
    private val context: Context,
    private val uiWatcher: UIWatcherModule,
    private val fileManager: FileManagerModule,
    private val termuxBridge: TermuxBridgeModule,
    private val notifier: BuildNotifier
) {
    companion object {
        private const val TAG           = "OrchestrationController"
        private const val MAX_ITERATIONS = 50
    }

    @Volatile private var stopRequested = false

    fun requestStop() { stopRequested = true }

    // ── Existing autobuild loop (unchanged) ───────────────────────────────

    suspend fun runLoop() {
        var iteration = 0
        var state = AutoBuildState.WAITING_FOR_RESPONSE
        log("Loop started")
        updateState(iteration, state)

        while (iteration < MAX_ITERATIONS && !stopRequested) {
            log("Iteration $iteration - State: $state")
            state = when (state) {
                AutoBuildState.WAITING_FOR_RESPONSE -> { delay(2000); AutoBuildState.DOWNLOAD_AI_OUTPUT }
                AutoBuildState.DOWNLOAD_AI_OUTPUT -> {
                    fileManager.deleteOldAiOutput()
                    val success = uiWatcher.handleDownloadAiOutput()
                    if (success) AutoBuildState.COPY_TO_AUTOMATION_DIR
                    else { delay(2000); AutoBuildState.DOWNLOAD_AI_OUTPUT }
                }
                AutoBuildState.COPY_TO_AUTOMATION_DIR -> {
                    val success = fileManager.copyAiOutputToAutomationDir()
                    if (success) AutoBuildState.TRIGGER_BUILD
                    else { delay(2000); AutoBuildState.COPY_TO_AUTOMATION_DIR }
                }
                AutoBuildState.TRIGGER_BUILD -> AutoBuildState.WAITING_FOR_BUILD
                AutoBuildState.WAITING_FOR_BUILD -> {
                    when (termuxBridge.triggerBuildAndWait(fileManager)) {
                        BuildResult.SUCCESS -> AutoBuildState.BUILD_SUCCEEDED
                        BuildResult.FAILURE -> AutoBuildState.ATTACHING_ERROR_REPORT
                        BuildResult.TIMEOUT -> { notifier.error("Build timeout"); return }
                        BuildResult.ERROR   -> { notifier.error("Build error");   return }
                    }
                }
                AutoBuildState.BUILD_SUCCEEDED -> { log("Pipeline completed after $iteration iterations"); notifier.success(iteration); return }
                AutoBuildState.ATTACHING_ERROR_REPORT -> {
                    val success = uiWatcher.handleBuildFailure()
                    if (success) { iteration++; AutoBuildState.WAITING_FOR_RESPONSE }
                    else { delay(2000); AutoBuildState.ATTACHING_ERROR_REPORT }
                }
                else -> { log("Unknown state: $state", isError = true); notifier.error("Unknown state"); return }
            }
            updateState(iteration, state)
            delay(500)
        }
        if (stopRequested) { log("Loop stopped by user"); notifier.error("Stopped by user") }
        else { log("Max iterations reached", isError = true); notifier.error("Max iterations reached") }
    }

    // ── Session 20: Agent loop ─────────────────────────────────────────────
    // Triggered when AutoBuildService detects agent_task.txt on Phone B sdcard.
    // Flow: AGENT_SENDING_DUMP → AGENT_WAITING_FOR_RESPONSE →
    //       AGENT_DOWNLOAD_OUTPUT → AGENT_STAGING_OUTPUT → AGENT_LOOP_DONE
    // Single-shot — no iteration loop. Phone A polls pull_from_bridge after.

    suspend fun runAgentLoop(task: String, dumpName: String) {
        log("[agent] Starting agent loop: task=${task.take(60)} dump=$dumpName")
        var state = AutoBuildState.AGENT_SENDING_DUMP
        updateState(0, state)

        while (!stopRequested) {
            log("[agent] State: $state")
            state = when (state) {

                AutoBuildState.AGENT_SENDING_DUMP -> {
                    if (!fileManager.agentDumpExists(dumpName)) {
                        log("[agent] Dump file not found: $dumpName — waiting...", isError = true)
                        delay(2000)
                        AutoBuildState.AGENT_SENDING_DUMP
                    } else {
                        val success = uiWatcher.handleSendDump(task, dumpName)
                        if (success) {
                            log("[agent] Dump sent to Claude, waiting for response...")
                            AutoBuildState.AGENT_WAITING_FOR_RESPONSE
                        } else {
                            log("[agent] Failed to send dump, retrying...", isError = true)
                            delay(3000)
                            AutoBuildState.AGENT_SENDING_DUMP
                        }
                    }
                }

                AutoBuildState.AGENT_WAITING_FOR_RESPONSE -> {
                    // Give Claude time to process and generate the output file
                    log("[agent] Waiting for Claude to generate output...")
                    delay(5000)
                    AutoBuildState.AGENT_DOWNLOAD_OUTPUT
                }

                AutoBuildState.AGENT_DOWNLOAD_OUTPUT -> {
                    fileManager.deleteOldAgentOutput()
                    log("[agent] Downloading ai-output.txt...")
                    val success = uiWatcher.handleDownloadAiOutput()
                    if (success) {
                        AutoBuildState.AGENT_STAGING_OUTPUT
                    } else {
                        log("[agent] Download failed, retrying...", isError = true)
                        delay(3000)
                        AutoBuildState.AGENT_DOWNLOAD_OUTPUT
                    }
                }

                AutoBuildState.AGENT_STAGING_OUTPUT -> {
                    log("[agent] Staging output for Phone A...")
                    val success = fileManager.stageOutputForPhoneA()
                    if (success) {
                        AutoBuildState.AGENT_LOOP_DONE
                    } else {
                        log("[agent] Staging failed, retrying...", isError = true)
                        delay(2000)
                        AutoBuildState.AGENT_STAGING_OUTPUT
                    }
                }

                AutoBuildState.AGENT_LOOP_DONE -> {
                    log("[agent] ✅ Done — ai-output.txt staged for Phone A")
                    notifier.agentSuccess()
                    return
                }

                else -> {
                    log("[agent] Unexpected state: $state", isError = true)
                    notifier.error("[agent] Unexpected state")
                    return
                }
            }
            updateState(0, state)
            delay(500)
        }
        log("[agent] Stopped by user request")
        notifier.error("[agent] Stopped by user")
    }

    private fun updateState(iteration: Int, state: AutoBuildState) {
        AutoBuildService.currentState     = state
        AutoBuildService.currentIteration = iteration
        AutoBuildService.onStatusUpdate?.invoke(iteration, state)
        notifier.update(iteration, state)
    }

    private fun log(msg: String, isError: Boolean = false) {
        if (isError) Log.e(TAG, msg) else Log.d(TAG, msg)
        AutoBuildService.onLogLine?.invoke(msg, isError)
    }
}
