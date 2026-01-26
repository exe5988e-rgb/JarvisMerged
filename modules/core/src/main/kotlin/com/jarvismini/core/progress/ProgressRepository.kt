package com.jarvismini.core.progress

import android.content.Context
import kotlinx.coroutines.runBlocking

object ProgressRepository {

    fun hydrate(context: Context) = runBlocking {
        ProgressStore.init(context)
        ProgressStore.updateMissedTasks(context)
    }

    fun register(context: Context, entry: ProgressEntry) = runBlocking {
        ProgressStore.register(context, entry)
    }

    fun markCompleted(context: Context, routineId: String, blockId: String) =
        runBlocking { ProgressStore.markComplete(context, routineId, blockId) }

    fun markIncomplete(context: Context, routineId: String, blockId: String) =
        runBlocking { ProgressStore.markIncomplete(context, routineId, blockId) }

    fun getTodayBlocks(): List<ProgressBlock> = ProgressStore.getTodayBlocks()
}
