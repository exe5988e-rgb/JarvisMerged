package com.jarvismini.core.progress

import android.content.Context
import kotlinx.coroutines.runBlocking

object ProgressRepository {

    fun hydrate(context: Context) = runBlocking {
        ProgressStore.init(context)
        ProgressStore.updateMissedTasks(context)
    }

    fun register(context: Context, entry: ProgressEntry) =
        runBlocking { ProgressStore.register(context, entry) }

    fun markCompleted(context: Context, blockId: String) =
        runBlocking { ProgressStore.markComplete(context, blockId) }

    fun markIncomplete(context: Context, blockId: String) =
        runBlocking { ProgressStore.markIncomplete(context, blockId) }

    fun getAllEntries(): List<ProgressEntry> =
        ProgressStore.getAllEntries()

    fun getTodayEntries(): List<ProgressEntry> =
        ProgressStore.getTodayEntries()

    fun getTodayBlocks(): List<ProgressBlock> =
        ProgressStore.getTodayBlocks()
}
