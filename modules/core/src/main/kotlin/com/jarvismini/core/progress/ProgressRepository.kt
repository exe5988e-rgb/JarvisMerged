package com.jarvismini.core.progress

import android.content.Context
import kotlinx.coroutines.runBlocking

object ProgressRepository {

    private var hydrated = false

    fun hydrate(context: Context) = runBlocking {
        if (hydrated) return@runBlocking
        ProgressStore.init(context)
        hydrated = true
    }

    fun register(context: Context, entry: ProgressEntry) = runBlocking {
        ProgressStore.register(context, entry)
    }

    fun markCompleted(context: Context, blockId: String) = runBlocking {
        ProgressStore.markComplete(context, blockId, blockId)
    }

    fun markIncomplete(context: Context, blockId: String) = runBlocking {
        ProgressStore.markIncomplete(context, blockId, blockId)
    }

    fun getAllEntries(): List<ProgressEntry> = ProgressStore.getAllEntries()
    fun getTodayEntries(): List<ProgressEntry> = ProgressStore.getTodayEntries()
    fun getTodayBlocks(context: Context): List<ProgressBlock> = ProgressStore.getTodayBlocks()
}
