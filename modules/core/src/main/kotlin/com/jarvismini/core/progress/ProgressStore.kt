package com.jarvismini.core.progress

import android.content.Context
import java.text.SimpleDateFormat
import java.util.*

object ProgressStore {

    private const val PREF = "progress_store"

    fun register(context: Context, blockId: String, scheduledTime: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("reg_$blockId", true)
            .putString("time_$blockId", scheduledTime)
            .remove("doneTime_$blockId")
            .apply()
    }

    fun markComplete(context: Context, blockId: String) {
        val currentTime = getCurrentTime()
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("done_$blockId", true)
            .putString("doneTime_$blockId", currentTime)
            .apply()
    }

    fun markIncomplete(context: Context, blockId: String) {
        val currentTime = getCurrentTime()
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .remove("done_$blockId")
            .putString("doneTime_$blockId", currentTime)
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
            .getString("time_$blockId", "") ?: ""
    }

    fun getCompletedTime(context: Context, blockId: String): String? {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString("doneTime_$blockId", null)
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

    private fun getCurrentTime(): String {
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        return formatter.format(Date())
    }
}
