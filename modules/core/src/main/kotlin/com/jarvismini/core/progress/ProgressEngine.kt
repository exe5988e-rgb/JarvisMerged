package com.jarvismini.core.progress

import android.content.Context

/**
 * Core progress logic.
 * NO engine / alarm dependencies here.
 */
object ProgressEngine {

    fun markComplete(context: Context, blockId: String) {
        ProgressStore.markComplete(context, blockId)
    }

    fun markIncomplete(context: Context, blockId: String, blockName: String) {
        ProgressStore.markIncomplete(context, blockId)
    
}
}
