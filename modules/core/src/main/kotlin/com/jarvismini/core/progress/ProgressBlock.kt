package com.jarvismini.core.progress

data class ProgressBlock(
    val id: String,
    val completed: Boolean,
    val scheduledTime: String,       // official routine time
    val completedTime: String? = null // actual completion/missed timestamp
)
