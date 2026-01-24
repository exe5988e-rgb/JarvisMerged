package com.jarvismini.core.progress

data class ProgressEntry(
    val routineId: String,
    val blockId: String,
    val timestamp: Long,
    val state: ProgressState
)
