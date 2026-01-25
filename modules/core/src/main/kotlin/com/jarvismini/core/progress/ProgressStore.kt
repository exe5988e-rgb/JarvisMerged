package com.jarvismini.core.progress

import android.content.Context
import java.text.SimpleDateFormat
import java.util.*

object ProgressStore {

    private const val PREF = "progress_store"

    // register routine with scheduled time
    fun register(context: Context, blockId: String, scheduledTime: String) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (!prefs.contains("reg_$blockId")) {
            prefs.edit()
                .putBoolean("reg_$blockId", true)
                .putString("sched_$blockId", scheduledTime)
                .putString("status_$blockId", Status.PENDING.name)
                .apply()
        }
    }

    // mark completed and store actual time
    fun markComplete(context: Context, blockId: String) {
        val actualTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            .format(Date())

        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putBoolean("done_$blockId", true)
            .putString("status_$blockId", Status.COMPLETED.name)
            .putString("actual_$blockId", actualTime)
            .apply()
    }

    // mark missed and store actual time
    fun markMissed(context: Context, blockId: String) {
        val actualTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            .format(Date())

        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString("status_$blockId", Status.MISSED.name)
            .putString("actual_$blockId", actualTime)
            .apply()
    }

    // get all registered routine IDs
    fun getRegisteredBlocks(context: Context): Set<String> {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .all.keys
            .filter { it.startsWith("reg_") }
            .map { it.removePrefix("reg_") }
            .toSet()
    }

    // get all completed routine IDs
    fun getCompletedBlocks(context: Context): Set<String> {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .all.keys
            .filter { it.startsWith("done_") }
            .map { it.removePrefix("done_") }
            .toSet()
    }

    // get today blocks with status and times
    fun getTodayBlocks(context: Context): List<ProgressBlock> {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val registered = getRegisteredBlocks(context)

        return registered.map { id ->
            val sched = prefs.getString("sched_$id", "") ?: ""
            val actual = prefs.getString("actual_$id", null)
            val status = prefs.getString("status_$id", Status.PENDING.name)
                ?.let { Status.valueOf(it) } ?: Status.PENDING

            ProgressBlock(
                id = id,
                scheduledTime = sched,
                actualTime = actual,
                status = status
            )
        }
    }
}
