package com.jarvismini.core.routine

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages active task timers across the app.
 *
 * FIX: startTimer() now passes taskId to TaskTimerService so the service
 * can call updateTimer(taskId, remaining) every second.
 */
object TaskTimerManager {

    data class TimerState(
        val taskId: String,
        val taskName: String,
        val totalSeconds: Long,
        val remainingSeconds: Long,
        val isActive: Boolean = true
    )

    interface FloatingTimerDelegate {
        fun startFloating(context: Context, taskId: String, taskName: String, totalSeconds: Long)
        fun stopFloating(context: Context)
    }

    var floatingTimerDelegate: FloatingTimerDelegate? = null

    private val _activeTimers = MutableStateFlow<Map<String, TimerState>>(emptyMap())
    val activeTimers: StateFlow<Map<String, TimerState>> = _activeTimers.asStateFlow()

    fun startTimer(context: Context, taskId: String, taskName: String, durationMinutes: Long) {
        val totalSeconds = durationMinutes * 60

        _activeTimers.value = _activeTimers.value + (taskId to TimerState(
            taskId           = taskId,
            taskName         = taskName,
            totalSeconds     = totalSeconds,
            remainingSeconds = totalSeconds,
            isActive         = true
        ))

        // Pass taskId so service can call updateTimer() every second
        TaskTimerService.start(context, taskId, taskName, durationMinutes)

        // Floating overlay
        floatingTimerDelegate?.startFloating(context, taskId, taskName, totalSeconds)
    }

    /** Called by TaskTimerService every second — drives the floating overlay */
    fun updateTimer(taskId: String, remainingSeconds: Long) {
        val current = _activeTimers.value[taskId] ?: return
        _activeTimers.value = _activeTimers.value + (taskId to current.copy(
            remainingSeconds = remainingSeconds
        ))
    }

    fun stopTimer(context: Context, taskId: String) {
        _activeTimers.value = _activeTimers.value - taskId
        context.stopService(Intent(context, TaskTimerService::class.java))
        floatingTimerDelegate?.stopFloating(context)
    }

    fun getTimerState(taskId: String): TimerState? = _activeTimers.value[taskId]
    fun hasActiveTimer(taskId: String): Boolean    = _activeTimers.value.containsKey(taskId)
    fun clearAll()                                 { _activeTimers.value = emptyMap() }
}
