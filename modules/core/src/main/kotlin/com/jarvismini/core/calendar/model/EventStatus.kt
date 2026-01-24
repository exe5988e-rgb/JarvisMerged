package com.jarvismini.core.calendar.model

// ✅ Added missing INCOMPLETE and RETRY_SCHEDULED
enum class EventStatus {
    SCHEDULED,
    COMPLETED,
    INCOMPLETE,
    RETRY_SCHEDULED
}
