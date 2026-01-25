package com.jarvismini.core.progress

import android.content.Context

/**
 * Engine responsible for marking progress blocks as complete/incomplete.
 * Context-dependent methods for scheduling, notifications, and persistence.
 */
object ProgressEngine {

    // Context-free calls for UI (via ProgressRepository)
    fun markComplete(blockId: String) {
        // Call internal method with null context or default context if needed
        markCompleteInternal(null, blockId)
    }

    fun markIncomplete(blockId: String, blockName: String) {
        markIncompleteInternal(null, blockId, blockName)
    }

    // Context-aware internal methods for engine/scheduler usage
    fun markComplete(context: Context?, blockId: String) {
        markCompleteInternal(context, blockId)
    }

    fun markIncomplete(context: Context?, blockId: String, blockName: String) {
        markIncompleteInternal(context, blockId, blockName)
    }

    // Private internal implementation
    private fun markCompleteInternal(context: Context?, blockId: String) {
        // TODO: persist completion, notify if context available
    }

    private fun markIncompleteInternal(context: Context?, blockId: String, blockName: String) {
        // TODO: persist incompletion, notify if context available
    }
}
