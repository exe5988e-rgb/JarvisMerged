package com.jarvismini.core.progress

// ✅ Single correct ProgressStats definition
data class ProgressStats(
    val date: String,              // e.g., "2026-01-24", can be set dynamically later
    val totalBlocks: Int,
    val completedBlocks: Int
) {
    val completionPercent: Int
        get() = if (totalBlocks == 0) 0 else (completedBlocks * 100) / totalBlocks
}
