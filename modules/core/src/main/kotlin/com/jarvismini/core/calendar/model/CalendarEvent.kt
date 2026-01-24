package com.jarvismini.core.calendar.model

data class CalendarEvent(
    val id: String,
    val title: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val status: EventStatus,
    val meta: Map<String, String> = emptyMap()
)
