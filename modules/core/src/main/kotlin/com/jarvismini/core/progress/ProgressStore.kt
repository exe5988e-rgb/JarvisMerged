package com.jarvismini.core.progress

import android.content.Context

object ProgressStore {

    private const val PREF = "routine_progress"

    fun markComplete(context: Context, blockId: String) {
        prefs(context).edit()
            .putBoolean(blockId, true)
            .putLong("${blockId}_ts", System.currentTimeMillis())
            .apply()
    }

    fun markIncomplete(context: Context, blockId: String) {
        prefs(context).edit()
            .putBoolean(blockId, false)
            .apply()
    }

    fun isComplete(context: Context, blockId: String): Boolean =
        prefs(context).getBoolean(blockId, false)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
