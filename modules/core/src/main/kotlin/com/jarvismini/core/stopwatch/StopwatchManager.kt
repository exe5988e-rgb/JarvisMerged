package com.jarvismini.core.stopwatch

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * FIXED StopwatchManager - pause now keeps notification visible
 */
object StopwatchManager {
    
    private val _state = MutableStateFlow(StopwatchState())
    val state: StateFlow<StopwatchState> = _state.asStateFlow()
    
    private var startTimeMs: Long = 0L
    private var pausedTimeMs: Long = 0L
    private var totalElapsedBeforePause: Long = 0L
    
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
    
    fun pause(context: Context) {
        android.util.Log.d("StopwatchManager", "pause() called")
        
        if (_state.value.isRunning) {
            pausedTimeMs = SystemClock.elapsedRealtime()
            totalElapsedBeforePause += (pausedTimeMs - startTimeMs)
            
            _state.value = _state.value.copy(isRunning = false)
            
            // DON'T stop service - just update notification to paused state
            // Service will update notification to show "Paused"
            android.util.Log.d("StopwatchManager", "Keeping service running, updating to paused state")
        }
    }
    
    fun reset(context: Context) {
        android.util.Log.d("StopwatchManager", "reset() called")
        
        startTimeMs = 0L
        pausedTimeMs = 0L
        totalElapsedBeforePause = 0L
        
        _state.value = StopwatchState(isRunning = false)
        
        // Stop the service when reset
        StopwatchService.stop(context)
    }
    
    fun stop(context: Context) {
        android.util.Log.d("StopwatchManager", "stop() called")
        reset(context)
    }
    
    fun getCurrentElapsed(): Long {
        return if (_state.value.isRunning) {
            totalElapsedBeforePause + (SystemClock.elapsedRealtime() - startTimeMs)
        } else {
            totalElapsedBeforePause
        }
    }
    
    fun formatElapsedTimeShort(elapsedMs: Long): String {
        val totalSeconds = elapsedMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
    
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

data class StopwatchState(
    val isRunning: Boolean = false
)
