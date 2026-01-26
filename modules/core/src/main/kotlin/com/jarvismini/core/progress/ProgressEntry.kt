package com.jarvismini.core.progress

data class ProgressEntry(
    val routineId: String,
    val blockId: String,
    val timestamp: Long,
    val state: ProgressState,
    val scheduledAt: Long? = null,
    val completedAt: Long? = null,
    val lastUpdatedAt: Long? = null,
    val missedAt: Long? = null
)
