package com.jarvismini.core.stopwatch

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages stopwatch state and timing
 * 
 * Improved version with better service integration
 */
object StopwatchManager {
    
    private val _state = MutableStateFlow(StopwatchState())
    val state: StateFlow<StopwatchState> = _state.asStateFlow()
    
    private var startTimeMs: Long = 0L
    private var pausedTimeMs: Long = 0L
    private var totalElapsedBeforePause: Long = 0L
    
    /**
     * Start or resume the stopwatch
     */
    fun start(context: Context) {
        android.util.Log.d("StopwatchManager", "start() called, current state: ${_state.value}")
        
        if (!_state.value.isRunning) {
            startTimeMs = SystemClock.elapsedRealtime()
            
            // Update state BEFORE starting service
            _state.value = _state.value.copy(isRunning = true)
            
            android.util.Log.d("StopwatchManager", "Starting service...")
            
            // Start the foreground service
            StopwatchService.start(context)
        }
    }
    
    /**
     * Pause the stopwatch
     */
    fun pause(context: Context) {
        android.util.Log.d("StopwatchManager", "pause() called")
        
        if (_state.value.isRunning) {
            pausedTimeMs = SystemClock.elapsedRealtime()
            totalElapsedBeforePause += (pausedTimeMs - startTimeMs)
            
            _state.value = _state.value.copy(isRunning = false)
            
            // Stop the service when paused
            StopwatchService.stop(context)
        }
    }
    
    /**
     * Reset the stopwatch to zero
     */
    fun reset(context: Context) {
        android.util.Log.d("StopwatchManager", "reset() called")
        
        startTimeMs = 0L
        pausedTimeMs = 0L
        totalElapsedBeforePause = 0L
        
        _state.value = StopwatchState(isRunning = false)
        
        // Stop the service when reset
        StopwatchService.stop(context)
    }
    
    /**
     * Stop the stopwatch (pause and reset)
     */
    fun stop(context: Context) {
        android.util.Log.d("StopwatchManager", "stop() called")
        reset(context)
    }
    
    /**
     * Get current elapsed time in milliseconds
     */
    fun getCurrentElapsed(): Long {
        return if (_state.value.isRunning) {
            totalElapsedBeforePause + (SystemClock.elapsedRealtime() - startTimeMs)
        } else {
            totalElapsedBeforePause
        }
    }
    
    /**
     * Format elapsed time as MM:SS for notification
     */
    fun formatElapsedTimeShort(elapsedMs: Long): String {
        val totalSeconds = elapsedMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
    
    /**
     * Format elapsed time as HH:MM:SS for UI display
     */
    fun formatElapsedTimeLong(elapsedMs: Long): String {
        val totalSeconds = elapsedMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
}

/**
 * Represents the current state of the stopwatch
 */
data class StopwatchState(
    val isRunning: Boolean = false
)
