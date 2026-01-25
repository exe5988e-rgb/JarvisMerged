package com.jarvismini.core.progress

import android.content.Context

object ProgressRepository {

    fun markCompleted(context: Context, blockId: String) {
        ProgressEngine.markComplete(context, blockId)
    }

    fun markIncomplete(context: Context, blockId: String, blockName: String) {
        ProgressEngine.markIncomplete(context, blockId, blockName)
   
}
}
