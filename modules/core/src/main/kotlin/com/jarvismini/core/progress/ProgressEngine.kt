package com.jarvismini.core.progress

import android.content.Context
import kotlinx.coroutines.runBlocking

/**
 * Core progress engine.
 * Forwards all progress actions to the persistent ProgressRepository.
 * No change to action logic or engine behavior.
 */
object ProgressEngine {

    /** Mark a block as complete */
    fun markComplete(context: Context, blockId: String) {
        runBlocking {
            ProgressRepository.markCompleted(context, blockId, blockId)
        }
    }

    /** Mark a block as incomplete */
    fun markIncomplete(context: Context, blockId: String, blockName: String) {
        runBlocking {
            ProgressRepository.markIncomplete(context, blockId, blockId)
        }
    }

    /** Retrieve all progress entries */
    fun getAllEntries(): List<ProgressEntry> = ProgressRepository.getAllEntries()

    /** Retrieve today's progress entries */
    fun getTodayEntries(): List<ProgressEntry> = ProgressRepository.getTodayEntries()
}
