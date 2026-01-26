package com.jarvismini.core.progress

import android.content.Context
import kotlinx.coroutines.runBlocking

object ProgressEngine {

    fun markComplete(context: Context, blockId: String) = runBlocking {
        ProgressStore.markComplete(context, blockId)
    }

    fun markIncomplete(context: Context, blockId: String) = runBlocking {
        ProgressStore.markMissed(context, blockId)
    }
}
