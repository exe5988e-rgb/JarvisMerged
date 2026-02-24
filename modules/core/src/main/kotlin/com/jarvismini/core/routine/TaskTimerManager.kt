package com.jarvismini.core.routine

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages active task timers across the app.
 * Tracks which tasks have active timers and their remaining time.
 *
 * CHANGED: startTimer() now also launches FloatingTimerService so the
 * floating overlay starts automatically from the same TIMER button.
 * stopTimer() stops the floating overlay as well.
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
     * Start a new timer for a task.
     * Launches TaskTimerService (notification) AND FloatingTimerService (overlay)
     * from the same call — no extra button required.
     */
    fun startTimer(context: Context, taskId: String, taskName: String, durationMinutes: Long) {
        val totalSeconds = durationMinutes * 60

        _activeTimers.value = _activeTimers.value + (taskId to TimerState(
            taskId           = taskId,
            taskName         = taskName,
            totalSeconds     = totalSeconds,
            remainingSeconds = totalSeconds,
            isActive         = true
        ))

        // Notification-based countdown (existing)
        TaskTimerService.start(context, taskName, durationMinutes)

        // Floating overlay (new) — silently skipped if SYSTEM_ALERT_WINDOW not granted
        FloatingTimerService.start(context, taskId, taskName, totalSeconds)
    }

    /**
     * Update timer remaining time (called by TaskTimerService every second).
     */
    fun updateTimer(taskId: String, remainingSeconds: Long) {
        val current = _activeTimers.value[taskId] ?: return
        _activeTimers.value = _activeTimers.value + (taskId to current.copy(
            remainingSeconds = remainingSeconds
        ))
    }

    /**
     * Stop and remove a timer.
     * Also stops the floating overlay.
     */
    fun stopTimer(context: Context, taskId: String) {
        _activeTimers.value = _activeTimers.value - taskId

        val intent = Intent(context, TaskTimerService::class.java)
        context.stopService(intent)

        // Stop floating overlay
        FloatingTimerService.stop(context)
    }

    fun getTimerState(taskId: String): TimerState? = _activeTimers.value[taskId]

    fun hasActiveTimer(taskId: String): Boolean = _activeTimers.value.containsKey(taskId)

    fun clearAll() {
        _activeTimers.value = emptyMap()
    }
}
