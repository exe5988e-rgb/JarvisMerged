package com.jarvismini.core.progress

import android.content.Context
import java.text.SimpleDateFormat
import java.util.*

object ProgressStore {

    private const val PREF = "progress_store"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    fun register(context: Context, blockId: String, scheduledTime: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString("sched_$blockId", scheduledTime)
            .putBoolean("reg_$blockId", true)
            .apply()
    }

    fun markComplete(context: Context, blockId: String) {
        val now = dateFormat.format(Date())
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("done_$blockId", true)
            .putString("time_$blockId", now)
            .apply()
    }

    fun markIncomplete(context: Context, blockId: String) {
        val now = dateFormat.format(Date())
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .remove("done_$blockId")
            .putString("time_$blockId", now)
            .apply()
    }

    fun getRegisteredBlocks(context: Context): Set<String> {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .all
            .keys
            .filter { it.startsWith("reg_") }
            .map { it.removePrefix("reg_") }
            .toSet()
    }

    fun getCompletedBlocks(context: Context): Set<String> {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .all
            .keys
            .filter { it.startsWith("done_") }
            .map { it.removePrefix("done_") }
            .toSet()
    }

    fun getScheduledTime(context: Context, blockId: String): String {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString("sched_$blockId", "N/A") ?: "N/A"
    }

    fun getCompletedTime(context: Context, blockId: String): String? {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString("time_$blockId", null)
    }

    fun getTodayBlocks(context: Context): List<ProgressBlock> {
        val registered = getRegisteredBlocks(context)
        val completed = getCompletedBlocks(context)

        return registered.map { id ->
            ProgressBlock(
                id = id,
                completed = completed.contains(id),
                scheduledTime = getScheduledTime(context, id),
                completedTime = getCompletedTime(context, id)
            )
        }
    }
}
