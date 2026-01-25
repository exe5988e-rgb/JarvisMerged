package com.jarvismini.core.progress

import android.content.Context

/**
 * Engine for marking progress blocks complete/incomplete.
 */
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
        // TODO: persist completion, notify if context available
    }

    private fun markIncompleteInternal(context: Context?, blockId: String, blockName: String) {
        // TODO: persist incompletion, notify if context available
    }

    // ✅ New method for RoutineExecutor
    fun onBlockCompleted(blockId: String, blockName: String) {
        markComplete(blockId)
        // Could also schedule reminders or trigger events here
    }
}
