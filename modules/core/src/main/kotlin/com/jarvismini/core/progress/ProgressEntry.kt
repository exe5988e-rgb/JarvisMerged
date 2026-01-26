package com.jarvismini.core.progress

data class ProgressEntry(
    val routineId: String,
    val blockId: String,
    val timestamp: Long,
    val state: ProgressState = ProgressState.PENDING,
    val scheduledAt: Long? = null,
    val completedAt: Long? = null,
    val lastUpdatedAt: Long = System.currentTimeMillis(),
    val missedAt: Long? = null
)
