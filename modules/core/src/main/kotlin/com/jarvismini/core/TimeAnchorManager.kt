package com.jarvismini.core

import android.os.SystemClock
import java.net.HttpURLConnection
import java.net.URL

object TimeAnchorManager {

    private const val KEY_OFFSET_MS = "time_offset_ms"
    private const val KEY_LAST_UPTIME = "last_uptime_ms"

    fun init() {
        // reserved
    }

    fun syncWithNetwork() {
        try {
            val url = URL("https://www.google.com")
            val conn = url.openConnection() as HttpURLConnection
            conn.connect()

            val serverTime = conn.date
            if (serverTime > 0) {
                val deviceTime = System.currentTimeMillis()
                val offset = serverTime - deviceTime

                JarvisPrefs.putLong(KEY_OFFSET_MS, offset)
                JarvisPrefs.putLong(KEY_LAST_UPTIME, SystemClock.elapsedRealtime())
            }

            conn.disconnect()
        } catch (_: Exception) {
        }
    }

    fun nowUtcMs(): Long {
        val offset = JarvisPrefs.getLong(KEY_OFFSET_MS, 0L)
        return System.currentTimeMillis() + offset
    }
}
