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

    private fun isReady(): Boolean = ::prefs.isInitialized

    fun putLong(key: String, value: Long) {
        if (isReady()) prefs.edit().putLong(key, value).apply()
    }

    fun getLong(key: String, def: Long = 0L): Long =
        if (isReady()) prefs.getLong(key, def) else def

    fun putString(key: String, value: String) {
        if (isReady()) prefs.edit().putString(key, value).apply()
    }

    fun getString(key: String, def: String? = null): String? =
        if (isReady()) prefs.getString(key, def) else def

    fun putBoolean(key: String, value: Boolean) {
        if (isReady()) prefs.edit().putBoolean(key, value).apply()
    }

    fun getBoolean(key: String, def: Boolean = false): Boolean =
        if (isReady()) prefs.getBoolean(key, def) else def

    fun remove(key: String) {
        if (isReady()) prefs.edit().remove(key).apply()
    }

    fun putInt(key: String, value: Int) {
        if (isReady()) prefs.edit().putInt(key, value).apply()
    }

    fun incrementInt(key: String): Int {
        if (!isReady()) return 0
        val v = prefs.getInt(key, 0) + 1
        prefs.edit().putInt(key, v).apply()
        return v
    }

    fun getInt(key: String, def: Int = 0): Int = if (isReady()) prefs.getInt(key, def) else def

    fun allKeys(prefix: String): List<String> =
        if (isReady()) prefs.all.keys.filter { it.startsWith(prefix) } else emptyList()
}
