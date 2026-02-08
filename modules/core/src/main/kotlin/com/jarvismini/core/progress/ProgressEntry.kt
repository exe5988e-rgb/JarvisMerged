package com.jarvismini.core.progress

data class ProgressEntry(
val routineId: String,
val blockId: String,
val scheduledAt: Long,
val state: ProgressState = ProgressState.PENDING,
val completedAt: Long? = null,
val missedAt: Long? = null
)
