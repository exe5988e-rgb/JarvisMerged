package com.jarvismini.core.progress

import android.content.Context

object ProgressEngine {

    fun markComplete(context: Context, blockId: String) {
        ProgressRepository.markCompleted(context, blockId, blockId)
    }

    fun markIncomplete(context: Context, blockId: String) {
        ProgressRepository.markIncomplete(context, blockId, blockId)
    }

    fun getAllEntries(): List<ProgressEntry> =
        ProgressRepository.getAllEntries()

    fun getTodayEntries(): List<ProgressEntry> =
        ProgressRepository.getTodayEntries()
}
