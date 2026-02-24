package com.jarvismini.core.routine

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages active task timers across the app.
 *
 * Uses a FloatingTimerDelegate interface so the :core module doesn't need
 * Compose dependencies. The :app module registers the delegate at startup
 * via CoreApp, and FloatingTimerService (in :app) is started/stopped through it.
 *
 * Result: same single TIMER button starts both the notification timer
 * (TaskTimerService) AND the floating overlay (FloatingTimerService).
 */
object TaskTimerManager {

    data class TimerState(
        val taskId: String,
        val taskName: String,
        val totalSeconds: Long,
        val remainingSeconds: Long,
        val isActive: Boolean = true
    )

    /** Implemented in :app by FloatingTimerService companion / CoreApp */
    interface FloatingTimerDelegate {
        fun startFloating(context: Context, taskId: String, taskName: String, totalSeconds: Long)
        fun stopFloating(context: Context)
    }

    /** Set once from CoreApp.onCreate() */
    var floatingTimerDelegate: FloatingTimerDelegate? = null

    private val _activeTimers = MutableStateFlow<Map<String, TimerState>>(emptyMap())
    val activeTimers: StateFlow<Map<String, TimerState>> = _activeTimers.asStateFlow()

    /**
     * Start a new timer for a task.
     * Launches TaskTimerService (notification) AND FloatingTimerService (overlay)
     * via delegate — same single TIMER button, no extra UI needed.
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

        // Floating overlay via delegate (new) — no-op if delegate not set or permission missing
        floatingTimerDelegate?.startFloating(context, taskId, taskName, totalSeconds)
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
     * Stop and remove a timer. Also stops the floating overlay.
     */
    fun stopTimer(context: Context, taskId: String) {
        _activeTimers.value = _activeTimers.value - taskId
        context.stopService(Intent(context, TaskTimerService::class.java))
        floatingTimerDelegate?.stopFloating(context)
    }

    fun getTimerState(taskId: String): TimerState? = _activeTimers.value[taskId]

    fun hasActiveTimer(taskId: String): Boolean = _activeTimers.value.containsKey(taskId)

    fun clearAll() { _activeTimers.value = emptyMap() }
}
