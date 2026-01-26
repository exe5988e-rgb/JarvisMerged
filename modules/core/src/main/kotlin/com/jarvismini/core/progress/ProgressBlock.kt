package com.jarvismini.core.progress

data class ProgressBlock(
    val id: String,
    val completed: Boolean,
    val scheduledAt: Long,
    val completedAt: Long? = null,
    val missedAt: Long? = null
)
