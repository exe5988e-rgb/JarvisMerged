package com.jarvismini.devtools

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import androidx.appcompat.app.AppCompatActivity
import com.jarvismini.devtools.autobuild.AutoBuildService
import com.jarvismini.devtools.autobuild.models.AutoBuildState
import com.jarvismini.devtools.databinding.ActivityMainBinding

/**
 * Minimal UI: start/stop buttons, status dot, scrolling log.
 *
 * The AutoBuildService is an AccessibilityService — it cannot be started
 * programmatically. The Start button opens Accessibility Settings so the user
 * can toggle it once. After that the service self-manages via the loop.
 *
 * Status updates are received via AutoBuildService.onStatusUpdate, which is
 * a static lambda set here and cleared in onDestroy.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val logBuilder = SpannableStringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupButtons()
        registerCallbacks()
        refreshServiceState()
    }

    override fun onResume() {
        super.onResume()
        refreshServiceState()
    }

    override fun onDestroy() {
        super.onDestroy()
        AutoBuildService.onStatusUpdate = null
        AutoBuildService.onLogLine = null
    }

    // ── Buttons ───────────────────────────────────────────────────────────────

    private fun setupButtons() {
        // "Start" = open Accessibility Settings so user can enable the service
        binding.btnStart.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.btnStop.setOnClickListener {
            AutoBuildService.requestStop()
            appendLog("Stop requested by user.", Color.YELLOW)
            binding.btnStop.isEnabled = false
        }

        binding.btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    // ── Service callbacks ─────────────────────────────────────────────────────

    private fun registerCallbacks() {
        AutoBuildService.onStatusUpdate = { iteration, state ->
            runOnUiThread { updateStatus(iteration, state) }
        }
        AutoBuildService.onLogLine = { line, isError ->
            runOnUiThread { appendLog(line, if (isError) Color.RED else Color.WHITE) }
        }
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private fun refreshServiceState() {
        val enabled = isAccessibilityServiceEnabled()
        if (enabled) {
            binding.tvAccessibilityHint.visibility = android.view.View.GONE
            binding.btnStart.isEnabled = false
            binding.btnStop.isEnabled = true

            // If the loop is already running (e.g. we returned from another app),
            // immediately sync the dot and status text from the live state snapshot
            // rather than leaving them at the layout default ("Idle — enable service…").
            if (AutoBuildService.isLoopRunning) {
                updateStatus(
                    AutoBuildService.currentIteration,
                    AutoBuildService.currentState
                )
            }
        } else {
            binding.tvAccessibilityHint.visibility = android.view.View.VISIBLE
            binding.btnStart.isEnabled = true
            binding.btnStop.isEnabled = false
            setStatusDot(Color.GRAY)
            binding.tvStatus.text = getString(R.string.status_idle)
        }
    }

    private fun updateStatus(iteration: Int, state: AutoBuildState) {
        binding.tvIteration.text = "Iter: $iteration"
        binding.tvStatus.text = state.displayName()
        setStatusDot(state.dotColor())
    }

    private fun setStatusDot(color: Int) {
        binding.statusDot.backgroundTintList =
            android.content.res.ColorStateList.valueOf(color)
    }

    private fun appendLog(line: String, color: Int) {
        val start = logBuilder.length
        logBuilder.append(line).append("\n")
        logBuilder.setSpan(
            ForegroundColorSpan(color),
            start, logBuilder.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        binding.tvLog.text = logBuilder
        binding.scrollLog.post {
            binding.scrollLog.fullScroll(android.view.View.FOCUS_DOWN)
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        // Android may store either the short form   "com.pkg/.Service"
        // or the fully-qualified form               "com.pkg/com.pkg.path.Service"
        // and may pad entries with whitespace. Match both to be safe.
        val shortComponent = "${packageName}/.autobuild.AutoBuildService"
        val fullComponent  = "${packageName}/com.jarvismini.devtools.autobuild.AutoBuildService"

        return enabledServices.split(":").any { entry ->
            val trimmed = entry.trim()
            trimmed.equals(shortComponent, ignoreCase = true) ||
            trimmed.equals(fullComponent,  ignoreCase = true)
        }
    }

    // ── State display helpers ─────────────────────────────────────────────────

    private fun AutoBuildState.displayName() = when (this) {
        AutoBuildState.IDLE                      -> "Idle"
        AutoBuildState.WAITING_FOR_RESPONSE      -> "Waiting for Claude response…"
        AutoBuildState.EXTRACTING_CODE           -> "Extracting code blocks…"
        AutoBuildState.WRITING_OUTPUT            -> "Writing ai-output.txt…"
        AutoBuildState.TRIGGERING_BUILD          -> "Pushing to GitHub via Termux…"
        AutoBuildState.WAITING_FOR_BUILD         -> "Waiting for Apk.yml to finish…"
        AutoBuildState.CHECKING_ERROR_FRESHNESS  -> "Checking if errors are new…"
        AutoBuildState.READING_ERROR_LOGS        -> "Pulling error logs (git pull)…"
        AutoBuildState.ATTACHING_FILES           -> "Attaching error logs to Claude…"
        AutoBuildState.SUBMITTING_PROMPT         -> "Submitting prompt to Claude…"
        AutoBuildState.BUILD_SUCCEEDED           -> "✅ Build succeeded!"
        AutoBuildState.TIMEOUT_ERROR             -> "⚠ Timeout — retrying…"
    }

    private fun AutoBuildState.dotColor() = when (this) {
        AutoBuildState.BUILD_SUCCEEDED           -> Color.GREEN
        AutoBuildState.TIMEOUT_ERROR             -> Color.RED
        AutoBuildState.IDLE                      -> Color.GRAY
        AutoBuildState.WAITING_FOR_BUILD,
        AutoBuildState.TRIGGERING_BUILD          -> Color.YELLOW
        else                                     -> Color.CYAN
    }
}
