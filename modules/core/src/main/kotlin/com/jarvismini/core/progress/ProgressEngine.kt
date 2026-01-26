package com.jarvismini.core.progress

import android.content.Context

object ProgressEngine {

    fun markComplete(context: Context, blockId: String) {
        ProgressRepository.markCompleted(context, blockId) // only 2 args
    }

    fun markIncomplete(context: Context, blockId: String) {
        ProgressRepository.markIncomplete(context, blockId) // only 2 args
    }

    fun getAllEntries(): List<ProgressEntry> = ProgressRepository.getAllEntries()
    fun getTodayEntries(): List<ProgressEntry> = ProgressRepository.getTodayEntries()
}
