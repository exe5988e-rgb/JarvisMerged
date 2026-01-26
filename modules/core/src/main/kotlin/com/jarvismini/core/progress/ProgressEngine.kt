package com.jarvismini.core.progress

import android.content.Context

object ProgressEngine {

    fun markComplete(context: Context, blockId: String) {
        ProgressRepository.markCompleted(context, blockId)
    }

    fun markIncomplete(context: Context, blockId: String) {
        ProgressRepository.markIncomplete(context, blockId)
    }
}
