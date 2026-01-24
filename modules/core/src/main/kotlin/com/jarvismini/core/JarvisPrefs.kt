package com.jarvismini.core

import android.content.Context
import android.content.SharedPreferences

object JarvisPrefs {

    private const val PREFS_NAME = "jarvis_prefs"
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )
    }

    fun putLong(key: String, value: Long) =
        prefs.edit().putLong(key, value).apply()

    fun getLong(key: String, def: Long = 0L): Long =
        prefs.getLong(key, def)

    fun putString(key: String, value: String) =
        prefs.edit().putString(key, value).apply()

    fun getString(key: String, def: String? = null): String? =
        prefs.getString(key, def)

    fun remove(key: String) =
        prefs.edit().remove(key).apply()
}
