package com.jarvismini.core.progress

import android.content.Context

object ProgressStore {
    private const val PREF = "progress_store"

    fun register(context: Context, blockId: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("reg_$blockId", true)
            .apply()
    }

    fun markComplete(context: Context, blockId: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("done_$blockId", true)
            .apply()
    }

    fun markIncomplete(context: Context, blockId: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .remove("done_$blockId")
            .apply()
    }

    fun getCompletedBlocks(context: Context): Set<String> =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .all
            .filterKeys { it.startsWith("done_") }
            .keys
            .map { it.removePrefix("done_") }
            .toSet()

    fun getRegisteredBlocks(context: Context): Set<String> =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .all
            .filterKeys { it.startsWith("reg_") }
            .keys
            .map { it.removePrefix("reg_") }
            .toSet()
}
