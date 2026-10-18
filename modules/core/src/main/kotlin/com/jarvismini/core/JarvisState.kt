package com.jarvismini.core

import android.content.Context
import com.jarvismini.core.announce.SleepSummary

object JarvisState {

    private const val PREFS = "jarvis_state"
    private const val KEY_MODE = "mode"

    var currentMode: JarvisMode = JarvisMode.NORMAL
        private set

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_MODE, JarvisMode.NORMAL.name)
        currentMode = JarvisMode.valueOf(saved!!)
    }

    fun setMode(context: Context, mode: JarvisMode) {
        val previous = currentMode
        currentMode = mode
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, mode.name)
            .apply()

        if (previous != mode) {
            if (mode == JarvisMode.SLEEP) {
                SleepSummary.markSleepStart()
            } else if (previous == JarvisMode.SLEEP) {
                SleepSummary.deliverOnWake()
            }
        }
    }
}
