package com.jarvismini.core.progress

import android.content.Context
import kotlinx.coroutines.runBlocking

object ProgressRepository {

    private var hydrated = false

    // Idempotent hydration
    fun hydrate(context: Context) = runBlocking {
        if (hydrated) return@runBlocking
        ProgressStore.init(context)
        hydrated = true
    }

    fun register(context: Context, entry: ProgressEntry) = runBlocking {
        ProgressStore.register(context, entry)
    }

    fun markCompleted(context: Context, routineId: String, blockId: String) = runBlocking {
        ProgressStore.markComplete(context, routineId, blockId)
    }

    fun markIncomplete(context: Context, routineId: String, blockId: String) = runBlocking {
        ProgressStore.markIncomplete(context, routineId, blockId)
    }

    fun getAllEntries(): List<ProgressEntry> = ProgressStore.getAllEntries()
    fun getTodayEntries(): List<ProgressEntry> = ProgressStore.getTodayEntries()
}
