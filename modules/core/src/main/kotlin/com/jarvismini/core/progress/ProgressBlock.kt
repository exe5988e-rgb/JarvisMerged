package com.jarvismini.core.progress

/**
 * Data class representing a progress block for today.
 */
data class ProgressBlock(
    val id: String,
    val name: String,
    val completed: Boolean = false
)
