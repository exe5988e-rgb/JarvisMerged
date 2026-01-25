package com.jarvismini.core.progress

import android.content.Context

object ProgressEngine {

    fun markComplete(blockId: String) {
        markCompleteInternal(null, blockId)
    }

    fun markIncomplete(blockId: String, blockName: String) {
        markIncompleteInternal(null, blockId, blockName)
    }

    fun markComplete(context: Context?, blockId: String) {
        markCompleteInternal(context, blockId)
    }

    fun markIncomplete(context: Context?, blockId: String, blockName: String) {
        markIncompleteInternal(context, blockId, blockName)
    }

    private fun markCompleteInternal(context: Context?, blockId: String) {
        context?.let { ProgressStore.markComplete(it, blockId) }
    }

    private fun markIncompleteInternal(context: Context?, blockId: String, blockName: String) {
        context?.let { ProgressStore.markIncomplete(it, blockId) }
    }

    fun onBlockCompleted(blockId: String, blockName: String) {
        markComplete(blockId)
    }
}
