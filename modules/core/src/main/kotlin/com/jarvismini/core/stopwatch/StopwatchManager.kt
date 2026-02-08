package com.jarvismini.core.stopwatch

import android.content.Context
import android.os.SystemClock
import com.jarvismini.core.Logger
import com.jarvismini.core.tts.AssistantTTS
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Built-in stopwatch manager - no external clock app needed!
 * Starts a foreground service when running to ensure persistent notification.
 */
object StopwatchManager {

    private val TAG = "StopwatchManager"

    data class StopwatchState(
        val isRunning: Boolean = false,
        val elapsedTimeMs: Long = 0L,
        val startTimeMs: Long = 0L,
        val pausedAtMs: Long = 0L
    )

    private val _state = MutableStateFlow(StopwatchState())
    val state: StateFlow<StopwatchState> = _state.asStateFlow()

    private var tickerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    private const val PREF_KEY_START_TIME = "stopwatch_start_time"
    private const val PREF_KEY_PAUSED_AT = "stopwatch_paused_at"
    private const val PREF_KEY_IS_RUNNING = "stopwatch_is_running"

    /**
     * Initialize and restore state from preferences
     */
    fun init(context: Context) {
        val prefs = context.getSharedPreferences("jarvis_stopwatch", Context.MODE_PRIVATE)
        val isRunning = prefs.getBoolean(PREF_KEY_IS_RUNNING, false)
        val startTime = prefs.getLong(PREF_KEY_START_TIME, 0L)
        val pausedAt = prefs.getLong(PREF_KEY_PAUSED_AT, 0L)

        if (isRunning && startTime > 0) {
            _state.value = StopwatchState(
                isRunning = true,
                startTimeMs = startTime,
                pausedAtMs = 0L,
                elapsedTimeMs = SystemClock.elapsedRealtime() - startTime
            )
            startTicker()
            StopwatchService.start(context)
        } else if (pausedAt > 0) {
            _state.value = StopwatchState(
                isRunning = false,
                pausedAtMs = pausedAt,
                elapsedTimeMs = pausedAt
            )
        }
    }

    fun start(context: Context) {
        val currentState = _state.value
        if (currentState.isRunning) return

        val now = SystemClock.elapsedRealtime()
        val startTime = if (currentState.pausedAtMs > 0) now - currentState.pausedAtMs else now

        _state.value = StopwatchState(
            isRunning = true,
            startTimeMs = startTime,
            pausedAtMs = 0L,
            elapsedTimeMs = if (currentState.pausedAtMs > 0) currentState.pausedAtMs else 0L
        )

        saveState(context)
        startTicker()

        StopwatchService.start(context)

        AssistantTTS.speak(context, "Stopwatch started")
        Logger.d(TAG, "Stopwatch started")
    }

    fun pause(context: Context) {
        val currentState = _state.value
        if (!currentState.isRunning) return

        val elapsed = getCurrentElapsed()
        _state.value = currentState.copy(
            isRunning = false,
            pausedAtMs = elapsed,
            elapsedTimeMs = elapsed
        )

        stopTicker()
        saveState(context)
        StopwatchService.stop(context)

        val timeStr = formatElapsedTime(elapsed)
        AssistantTTS.speak(context, "Stopwatch paused at $timeStr")
        Logger.d(TAG, "Stopwatch paused at $timeStr")
    }

    fun stop(context: Context) {
        val elapsed = getCurrentElapsed()
        _state.value = StopwatchState(
            isRunning = false,
            elapsedTimeMs = elapsed
        )

        stopTicker()
        saveState(context)
        StopwatchService.stop(context)

        val timeStr = formatElapsedTime(elapsed)
        AssistantTTS.speak(context, "Stopwatch stopped. Total time: $timeStr")
        Logger.d(TAG, "Stopwatch stopped at $timeStr")
    }

    fun reset(context: Context) {
        _state.value = StopwatchState()
        stopTicker()
        saveState(context)
        StopwatchService.stop(context)

        AssistantTTS.speak(context, "Stopwatch reset")
        Logger.d(TAG, "Stopwatch reset")
    }

    fun toggle(context: Context) {
        if (_state.value.isRunning) pause(context) else start(context)
    }

    fun getCurrentElapsed(): Long {
        val currentState = _state.value
        return if (currentState.isRunning) SystemClock.elapsedRealtime() - currentState.startTimeMs
        else currentState.elapsedTimeMs
    }

    fun formatElapsedTime(ms: Long): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        val hours = (ms / (1000 * 60 * 60))

        return when {
            hours > 0 -> "%d hours %d minutes %d seconds".format(hours, minutes, seconds)
            minutes > 0 -> "%d minutes %d seconds".format(minutes, seconds)
            else -> "%d seconds".format(seconds)
        }
    }

    fun formatElapsedTimeShort(ms: Long): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        val hours = (ms / (1000 * 60 * 60))
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
        else "%02d:%02d".format(minutes, seconds)
    }

    private fun startTicker() {
        if (tickerJob?.isActive == true) return

        tickerJob = scope.launch {
            while (_state.value.isRunning) {
                _state.value = _state.value.copy(elapsedTimeMs = getCurrentElapsed())
                delay(100L)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun saveState(context: Context) {
        val prefs = context.getSharedPreferences("jarvis_stopwatch", Context.MODE_PRIVATE)
        val s = _state.value
        prefs.edit().apply {
            putBoolean(PREF_KEY_IS_RUNNING, s.isRunning)
            putLong(PREF_KEY_START_TIME, s.startTimeMs)
            putLong(PREF_KEY_PAUSED_AT, s.pausedAtMs)
            apply()
        }
    }
}
