package com.jarvismini.core.progress

/**
 * Represents a single progress entry for a routine block.
 * Now supports persistent, time-aware tracking.
 *
 * @property routineId The unique ID of the routine
 * @property blockId The unique ID of the progress block
 * @property timestamp The creation timestamp of this entry
 * @property state Current progress state
 *
 * Time-aware fields for persistence and progress tracking:
 * @property scheduledAt Scheduled time for this block (from ProgressConfig)
 * @property startedAt Actual start time, null if not started yet
 * @property completedAt Completion time, null if not completed yet
 * @property missedAt Missed time if the window expired without completion
 * @property lastUpdatedAt Last mutation timestamp, used for merging / idempotency
 */
data class ProgressEntry(
    val routineId: String,
    val blockId: String,
    val timestamp: Long,
    val state: ProgressState,

    // Time-aware fields
    val scheduledAt: Long,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val missedAt: Long? = null,
    val lastUpdatedAt: Long = timestamp
)
