package com.jarvismini.core.stopwatch

import android.content.Context
import android.os.SystemClock
import com.jarvismini.core.JarvisPrefs
import com.jarvismini.core.Logger
import com.jarvismini.core.tts.AssistantTTS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Built-in stopwatch manager - no external clock app needed!
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
            // Resume from saved state
            _state.value = StopwatchState(
                isRunning = true,
                startTimeMs = startTime,
                pausedAtMs = 0L,
                elapsedTimeMs = SystemClock.elapsedRealtime() - startTime
            )
            startTicker()
        } else if (pausedAt > 0) {
            // Restore paused state
            _state.value = StopwatchState(
                isRunning = false,
                pausedAtMs = pausedAt,
                elapsedTimeMs = pausedAt
            )
        }
    }

    /**
     * Start or resume the stopwatch
     */
    fun start(context: Context) {
        val currentState = _state.value
        
        if (currentState.isRunning) {
            Logger.d(TAG, "Stopwatch already running")
            return
        }

        val now = SystemClock.elapsedRealtime()
        val startTime = if (currentState.pausedAtMs > 0) {
            // Resume from pause
            now - currentState.pausedAtMs
        } else {
            // Fresh start
            now
        }

        _state.value = StopwatchState(
            isRunning = true,
            startTimeMs = startTime,
            pausedAtMs = 0L,
            elapsedTimeMs = if (currentState.pausedAtMs > 0) currentState.pausedAtMs else 0L
        )

        saveState(context)
        startTicker()
        
        AssistantTTS.speak(context, "Stopwatch started")
        Logger.d(TAG, "Stopwatch started")
    }

    /**
     * Pause the stopwatch
     */
    fun pause(context: Context) {
        val currentState = _state.value
        
        if (!currentState.isRunning) {
            Logger.d(TAG, "Stopwatch not running")
            return
        }

        val elapsed = getCurrentElapsed()
        
        _state.value = currentState.copy(
            isRunning = false,
            pausedAtMs = elapsed,
            elapsedTimeMs = elapsed
        )

        stopTicker()
        saveState(context)
        
        val timeStr = formatElapsedTime(elapsed)
        AssistantTTS.speak(context, "Stopwatch paused at $timeStr")
        Logger.d(TAG, "Stopwatch paused at $timeStr")
    }

    /**
     * Stop the stopwatch and announce total time
     */
    fun stop(context: Context) {
        val currentState = _state.value
        val elapsed = getCurrentElapsed()
        
        _state.value = StopwatchState(
            isRunning = false,
            elapsedTimeMs = elapsed
        )

        stopTicker()
        saveState(context)
        
        val timeStr = formatElapsedTime(elapsed)
        AssistantTTS.speak(context, "Stopwatch stopped. Total time: $timeStr")
        Logger.d(TAG, "Stopwatch stopped at $timeStr")
    }

    /**
     * Reset the stopwatch to zero
     */
    fun reset(context: Context) {
        _state.value = StopwatchState()
        stopTicker()
        saveState(context)
        
        AssistantTTS.speak(context, "Stopwatch reset")
        Logger.d(TAG, "Stopwatch reset")
    }

    /**
     * Toggle between start and pause
     */
    fun toggle(context: Context) {
        if (_state.value.isRunning) {
            pause(context)
        } else {
            start(context)
        }
    }

    /**
     * Get current elapsed time in milliseconds
     */
    fun getCurrentElapsed(): Long {
        val currentState = _state.value
        return if (currentState.isRunning) {
            SystemClock.elapsedRealtime() - currentState.startTimeMs
        } else {
            currentState.elapsedTimeMs
        }
    }

    /**
     * Format elapsed time as HH:MM:SS
     */
    fun formatElapsedTime(ms: Long): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        val hours = (ms / (1000 * 60 * 60))
        
        return if (hours > 0) {
            String.format("%d hours %d minutes %d seconds", hours, minutes, seconds)
        } else if (minutes > 0) {
            String.format("%d minutes %d seconds", minutes, seconds)
        } else {
            String.format("%d seconds", seconds)
        }
    }

    /**
     * Format for display (short form)
     */
    fun formatElapsedTimeShort(ms: Long): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        val hours = (ms / (1000 * 60 * 60))
        
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    /**
     * Start the ticker to update elapsed time
     */
    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        
        tickerJob = scope.launch {
            while (_state.value.isRunning) {
                val elapsed = getCurrentElapsed()
                _state.value = _state.value.copy(elapsedTimeMs = elapsed)
                delay(100L) // Update 10 times per second for smooth display
            }
        }
    }

    /**
     * Stop the ticker
     */
    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    /**
     * Save state to preferences for persistence across app restarts
     */
    private fun saveState(context: Context) {
        val prefs = context.getSharedPreferences("jarvis_stopwatch", Context.MODE_PRIVATE)
        val currentState = _state.value
        
        prefs.edit().apply {
            putBoolean(PREF_KEY_IS_RUNNING, currentState.isRunning)
            putLong(PREF_KEY_START_TIME, currentState.startTimeMs)
            putLong(PREF_KEY_PAUSED_AT, currentState.pausedAtMs)
            apply()
        }
    }

    /**
     * Clear saved state
     */
    fun clearSavedState(context: Context) {
        val prefs = context.getSharedPreferences("jarvis_stopwatch", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}
