package com.jarvismini.core.progress

import android.content.Context

object ProgressEngine {

    fun markComplete(context: Context, blockId: String) {
        ProgressStore.markComplete(context, blockId)
    }

    fun markIncomplete(context: Context, blockId: String) {
        ProgressStore.markIncomplete(context, blockId)
    }
}
