package com.jarvismini.core.events

import com.google.gson.Gson
import com.jarvismini.core.JarvisPrefs

object EventType {
    const val MESSAGE = "MESSAGE"
    const val CALL_BLOCKED = "CALL_BLOCKED"
    const val CALL_MISSED = "CALL_MISSED"
    const val BATTERY = "BATTERY"
    const val SERVICE = "SERVICE"
    const val ALERT = "ALERT"
    const val MODE = "MODE"
}

data class JarvisEvent(
    val ts: Long,
    val type: String,
    val title: String,
    val detail: String
)

/**
 * Small persistent ring buffer of things that happened while Jarvis was
 * running in the background. Feeds the wake-up summary and "what did I miss".
 * Stores sender names / titles only, never message bodies.
 */
object EventLog {

    private const val KEY = "event_log_v1"
    private const val MAX_EVENTS = 200
    private val gson = Gson()

    @Synchronized
    fun add(type: String, title: String, detail: String = "") {
        val list = load().toMutableList()
        list.add(JarvisEvent(System.currentTimeMillis(), type, title, detail))
        val trimmed = if (list.size > MAX_EVENTS) list.takeLast(MAX_EVENTS) else list
        JarvisPrefs.putString(KEY, gson.toJson(trimmed))
    }

    @Synchronized
    fun since(ts: Long): List<JarvisEvent> = load().filter { it.ts >= ts }

    @Synchronized
    fun clear() {
        JarvisPrefs.remove(KEY)
    }

    private fun load(): List<JarvisEvent> {
        return try {
            val json = JarvisPrefs.getString(KEY) ?: return emptyList()
            gson.fromJson(json, Array<JarvisEvent>::class.java)?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
