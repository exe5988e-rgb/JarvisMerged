package com.jarvismini.core.routine

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages active task timers across the app
 * Tracks which tasks have active timers and their remaining time
 */
object TaskTimerManager {
    
    data class TimerState(
        val taskId: String,
        val taskName: String,
        val totalSeconds: Long,
        val remainingSeconds: Long,
        val isActive: Boolean = true
    )
    
    private val _activeTimers = MutableStateFlow<Map<String, TimerState>>(emptyMap())
    val activeTimers: StateFlow<Map<String, TimerState>> = _activeTimers.asStateFlow()
    
    /**
     * Start a new timer for a task
     */
    fun startTimer(context: Context, taskId: String, taskName: String, durationMinutes: Long) {
        val totalSeconds = durationMinutes * 60
        
        _activeTimers.value = _activeTimers.value + (taskId to TimerState(
            taskId = taskId,
            taskName = taskName,
            totalSeconds = totalSeconds,
            remainingSeconds = totalSeconds,
            isActive = true
        ))
        
        // Start the service
        TaskTimerService.start(context, taskId, taskName, durationMinutes)
    }
    
    /**
     * Update timer remaining time (called by service)
     */
    fun updateTimer(taskId: String, remainingSeconds: Long) {
        val current = _activeTimers.value[taskId] ?: return
        _activeTimers.value = _activeTimers.value + (taskId to current.copy(
            remainingSeconds = remainingSeconds
        ))
    }
    
    /**
     * Stop and remove a timer
     */
    fun stopTimer(context: Context, taskId: String) {
        _activeTimers.value = _activeTimers.value - taskId
        TaskTimerService.stop(context, taskId)
    }
    
    /**
     * Get timer state for a specific task
     */
    fun getTimerState(taskId: String): TimerState? {
        return _activeTimers.value[taskId]
    }
    
    /**
     * Check if task has active timer
     */
    fun hasActiveTimer(taskId: String): Boolean {
        return _activeTimers.value.containsKey(taskId)
    }
    
    /**
     * Clear all timers
     */
    fun clearAll() {
        _activeTimers.value = emptyMap()
    }
}
