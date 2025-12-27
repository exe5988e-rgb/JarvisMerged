package com.jarvismini.core

import android.content.Context

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
        currentMode = mode
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, mode.name)
            .apply()
    }
}
