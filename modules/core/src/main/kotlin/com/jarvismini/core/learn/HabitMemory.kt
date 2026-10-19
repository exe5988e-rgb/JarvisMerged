package com.jarvismini.core.learn

import com.jarvismini.core.JarvisPrefs
import java.util.Calendar

/**
 * Spec section 3: Learning + Memory.
 * Lightweight on-device habit tracker — counts, not a model. Every count lives in
 * JarvisPrefs (SharedPreferences), so it survives restarts and needs no server.
 *
 * Tracks:
 *  - commands used (frequency) -> "frequently used commands"
 *  - apps opened (frequency) -> "apps opened"
 *  - what time of day the user is first active -> morning/night pattern
 *  - messaging activity by hour -> messaging behaviour
 *
 * Nothing here is spoken on its own; ProactiveEngine and chat read it back
 * (e.g. "most active" / "usual wake time") when asked or when deciding timing.
 */
object HabitMemory {

    private const val P_CMD = "habit_cmd_"
    private const val P_APP = "habit_app_"
    private const val P_WAKE_HOUR_SUM = "habit_wake_hour_sum"
    private const val P_WAKE_HOUR_N = "habit_wake_hour_n"
    private const val P_MSG_HOUR = "habit_msg_hour_"
    private const val P_LAST_ACTIVE_DAY = "habit_last_active_day"

    fun recordCommand(text: String) {
        val key = normalize(text) ?: return
        JarvisPrefs.incrementInt(P_CMD + key)
    }

    fun recordAppOpen(appName: String) {
        val key = normalize(appName) ?: return
        JarvisPrefs.incrementInt(P_APP + key)
    }

    fun recordMessageActivity() {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        JarvisPrefs.incrementInt(P_MSG_HOUR + h)
    }

    /** Call once per day on first interaction — builds the morning/night pattern. */
    fun recordFirstActivityOfDay() {
        val today = dayKey()
        if (JarvisPrefs.getString(P_LAST_ACTIVE_DAY) == today) return
        JarvisPrefs.putString(P_LAST_ACTIVE_DAY, today)
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        JarvisPrefs.putInt(P_WAKE_HOUR_SUM, JarvisPrefs.getInt(P_WAKE_HOUR_SUM) + h)
        JarvisPrefs.incrementInt(P_WAKE_HOUR_N)
    }

    fun usualWakeHour(): Int? {
        val n = JarvisPrefs.getInt(P_WAKE_HOUR_N)
        if (n == 0) return null
        return JarvisPrefs.getInt(P_WAKE_HOUR_SUM) / n
    }

    fun topCommands(limit: Int = 5): List<Pair<String, Int>> =
        top(P_CMD, limit)

    fun topApps(limit: Int = 5): List<Pair<String, Int>> =
        top(P_APP, limit)

    fun busiestMessageHour(): Int? {
        val hours = (0..23).map { it to JarvisPrefs.getInt(P_MSG_HOUR + it) }
        val best = hours.maxByOrNull { it.second } ?: return null
        return if (best.second == 0) null else best.first
    }

    private fun top(prefix: String, limit: Int): List<Pair<String, Int>> =
        JarvisPrefs.allKeys(prefix)
            .map { it.removePrefix(prefix) to JarvisPrefs.getInt(it) }
            .sortedByDescending { it.second }
            .take(limit)

    private fun normalize(s: String): String? {
        val t = s.trim().lowercase().take(40)
        return t.ifBlank { null }
    }

    private fun dayKey(): String {
        val c = Calendar.getInstance()
        return "${c.get(Calendar.YEAR)}-${c.get(Calendar.DAY_OF_YEAR)}"
    }

}
