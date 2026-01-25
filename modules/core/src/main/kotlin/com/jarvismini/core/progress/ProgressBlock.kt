package com.jarvismini.core.progress

data class ProgressBlock(
    val id: String,
    val completed: Boolean,
    val scheduledTime: String,
    val completedTime: String? = null
)
