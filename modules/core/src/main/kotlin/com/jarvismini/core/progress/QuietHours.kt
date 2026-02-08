package com.jarvismini.core.progress

import java.util.Calendar

/**
 * Quiet-hours evaluator.
 *
 * Replaces java.time.LocalTime (API 26+) with Calendar (API 1).
 */
object QuietHours {

    // ✅ Existing API (kept)
    fun isQuietNow(config: ProgressConfig): Boolean {
        val nowMinutes = currentMinutes()

        val startMinutes = parseToMinutes(config.quietStart)
        val endMinutes = parseToMinutes(config.quietEnd)

        return if (startMinutes < endMinutes) {
            // Same-day window (e.g. 13:00 → 18:00)
            nowMinutes in startMinutes until endMinutes
        } else {
            // Overnight window (e.g. 22:00 → 07:00)
            nowMinutes >= startMinutes || nowMinutes < endMinutes
        }
    }

    // 🔧 Compatibility API for ProgressEngine
    fun isQuietTime(quietStart: String, quietEnd: String): Boolean {
        val nowMinutes = currentMinutes()

        val startMinutes = parseToMinutes(quietStart)
        val endMinutes = parseToMinutes(quietEnd)

        return if (startMinutes < endMinutes) {
            nowMinutes in startMinutes until endMinutes
        } else {
            nowMinutes >= startMinutes || nowMinutes < endMinutes
        }
    }

    private fun currentMinutes(): Int {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    /**
     * Parses "HH:mm" into minutes since midnight.
     */
    private fun parseToMinutes(time: String): Int {
        val parts = time.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return hour * 60 + minute
    }
}
