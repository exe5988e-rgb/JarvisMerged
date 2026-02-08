package com.jarvismini.core.progress

import android.content.Context

object TaskTimerStore {

    private const val PREFS = "jarvis_task_timers"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getElapsed(context: Context, blockId: String): Long {
        return prefs(context).getLong("${blockId}_elapsed", 0L)
    }

    fun setElapsed(context: Context, blockId: String, value: Long) {
        prefs(context).edit()
            .putLong("${blockId}_elapsed", value)
            .apply()
    }

    fun getStartTime(context: Context, blockId: String): Long {
        return prefs(context).getLong("${blockId}_start", 0L)
    }

    fun setStartTime(context: Context, blockId: String, value: Long) {
        prefs(context).edit()
            .putLong("${blockId}_start", value)
            .apply()
    }

    fun isRunning(context: Context, blockId: String): Boolean {
        return prefs(context).getBoolean("${blockId}_running", false)
    }

    fun setRunning(context: Context, blockId: String, running: Boolean) {
        prefs(context).edit()
            .putBoolean("${blockId}_running", running)
            .apply()
    }

    fun clear(context: Context, blockId: String) {
        prefs(context).edit()
            .remove("${blockId}_elapsed")
            .remove("${blockId}_start")
            .remove("${blockId}_running")
            .apply()
    }
}
