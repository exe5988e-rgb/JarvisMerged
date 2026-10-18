package com.jarvismini.core.announce

import com.jarvismini.core.AssistantProfile
import com.jarvismini.core.JarvisPrefs
import com.jarvismini.core.events.EventLog
import com.jarvismini.core.events.EventType

/** Builds the "what happened while you were away" digest from EventLog. */
object SleepSummary {

    private const val KEY_SLEEP_START = "sleep_started_at"

    private val PHRASES = listOf(
        Triple(EventType.MESSAGE, "message received", "messages received"),
        Triple(EventType.CALL_MISSED, "missed call", "missed calls"),
        Triple(EventType.CALL_BLOCKED, "call blocked", "calls blocked"),
        Triple(EventType.ALERT, "alert", "alerts"),
        Triple(EventType.BATTERY, "battery event", "battery events"),
        Triple(EventType.SERVICE, "background issue", "background issues")
    )

    fun markSleepStart() {
        JarvisPrefs.putLong(KEY_SLEEP_START, System.currentTimeMillis())
    }

    /** Short spoken-style summary of events since [since], or null if nothing to report. */
    fun build(since: Long): String? {
        val events = EventLog.since(since)
        if (events.isEmpty()) return null
        val parts = ArrayList<String>()
        for ((type, one, many) in PHRASES) {
            val n = events.count { it.type == type }
            if (n > 0) parts.add(if (n == 1) "1 $one" else "$n $many")
        }
        return if (parts.isEmpty()) null else parts.joinToString(", ")
    }

    /** Called when the user leaves SLEEP mode. Speaks the digest through the normal gate. */
    fun deliverOnWake(): Boolean {
        val since = JarvisPrefs.getLong(KEY_SLEEP_START, 0L)
        if (since <= 0L) return false
        JarvisPrefs.remove(KEY_SLEEP_START)
        val summary = build(since)
        val text = "Good morning ${AssistantProfile.addressUser()}. " +
            if (summary != null) "While you were asleep: $summary."
            else "Nothing needed your attention overnight."
        return AnnouncementEngine.announce(text, AnnouncePriority.HIGH, "wake_digest")
    }
}
