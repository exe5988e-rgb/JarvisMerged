package com.jarvismini.core.progress

enum class Status { PENDING, COMPLETED, MISSED }

data class ProgressBlock(
    val id: String,
    val scheduledTime: String,
    val actualTime: String?,  // null if not done/missed yet
    val status: Status
)
