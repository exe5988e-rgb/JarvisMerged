package com.jarvismini.core.progress

data class ProgressBlock(
    val id: String,
    val completed: Boolean = false,

    // User-defined countdown duration
    val durationMinutes: Int = 0,

    // Timestamp when countdown started
    val startTimestamp: Long = 0L
)
