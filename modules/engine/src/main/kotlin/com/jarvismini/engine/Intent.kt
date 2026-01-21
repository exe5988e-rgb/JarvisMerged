package com.jarvismini.engine

sealed class Intent {
    data class SetAlarm(val timeMs: Long) : Intent()
    data class SetTimer(val durationMs: Long) : Intent()
    data class AddReminder(val text: String, val timeMs: Long) : Intent()
    data class ScheduleStudy(val subject: String, val timeMs: Long) : Intent()
    object Unknown : Intent()
}
