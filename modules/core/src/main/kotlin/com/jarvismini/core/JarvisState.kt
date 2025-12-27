package com.jarvismini.core

import android.content.Context

object JarvisState {

    @Volatile
    var currentMode: JarvisMode = JarvisMode.NORMAL
        private set

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("jarvis", Context.MODE_PRIVATE)
        currentMode = JarvisMode.valueOf(
            prefs.getString("mode", JarvisMode.NORMAL.name)!!
        )
    }

    fun setMode(context: Context, mode: JarvisMode) {
        currentMode = mode
        context.getSharedPreferences("jarvis", Context.MODE_PRIVATE)
            .edit()
            .putString("mode", mode.name)
            .apply()
    }
}
