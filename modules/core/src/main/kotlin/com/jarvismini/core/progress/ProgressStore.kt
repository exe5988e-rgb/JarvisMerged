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
}
