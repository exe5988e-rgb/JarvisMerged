package com.jarvismini.core

import com.jarvismini.core.announce.AnnouncePriority
import com.jarvismini.core.announce.AnnouncementEngine
import com.jarvismini.core.events.EventLog
import com.jarvismini.core.events.EventType

/**
 * Lockdown Mode: block every incoming call except a small whitelist.
 * Enforced in JarvisCallScreeningService. Emergency calls are never routed
 * through call screening by Android, so they are not affected.
 */
object LockdownManager {

    const val MAX_WHITELIST = 4
    private const val KEY_ENABLED = "lockdown_enabled"
    private const val KEY_WHITELIST = "lockdown_whitelist"

    fun isEnabled(): Boolean = JarvisPrefs.getBoolean(KEY_ENABLED, false)

    /** [announce] = false when the caller speaks its own reply (voice command path). */
    fun enable(announce: Boolean = true) {
        JarvisPrefs.putBoolean(KEY_ENABLED, true)
        EventLog.add(EventType.MODE, "Lockdown mode activated")
        if (announce) {
            AnnouncementEngine.announce(
                "Lockdown mode activated. All calls are blocked except whitelisted contacts.",
                AnnouncePriority.CRITICAL,
                "lockdown_on"
            )
        }
    }

    fun disable(announce: Boolean = true) {
        JarvisPrefs.putBoolean(KEY_ENABLED, false)
        EventLog.add(EventType.MODE, "Lockdown mode deactivated")
        if (announce) {
            AnnouncementEngine.announce(
                "Lockdown mode deactivated. Calls are back to normal.",
                AnnouncePriority.CRITICAL,
                "lockdown_off"
            )
        }
    }

    fun whitelist(): List<String> =
        JarvisPrefs.getString(KEY_WHITELIST, "")?.split(",")?.filter { it.isNotBlank() } ?: emptyList()

    fun addToWhitelist(number: String): Boolean {
        val n = normalize(number)
        if (n.length < 7) return false
        val list = whitelist().toMutableList()
        if (list.contains(n)) return true
        if (list.size >= MAX_WHITELIST) return false
        list.add(n)
        save(list)
        return true
    }

    fun removeFromWhitelist(number: String): Boolean {
        val n = normalize(number)
        val list = whitelist().toMutableList()
        val removed = list.remove(n)
        if (removed) save(list)
        return removed
    }

    /** Unknown / hidden numbers are never allowed while lockdown is on. */
    fun isAllowed(number: String?): Boolean {
        if (number.isNullOrBlank()) return false
        val n = normalize(number)
        if (n.length < 7) return false
        return whitelist().contains(n)
    }

    private fun normalize(raw: String): String = raw.filter { it.isDigit() }.takeLast(10)

    private fun save(list: List<String>) {
        JarvisPrefs.putString(KEY_WHITELIST, list.joinToString(","))
    }
}
