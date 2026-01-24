package com.jarvismini.core.progress

data class ProgressStats(
    val date: String,
    val totalBlocks: Int,
    val completedBlocks: Int
) {
    val completionPercent: Int
        get() = if (totalBlocks == 0) 0 else (completedBlocks * 100) / totalBlocks
}
