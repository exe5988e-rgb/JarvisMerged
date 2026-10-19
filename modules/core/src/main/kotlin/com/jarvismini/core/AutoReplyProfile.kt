package com.jarvismini.core

/**
 * Per-owner auto-reply settings: a free-text status ("at college till 6") and the small set of
 * contacts allowed to chat with the AI while the owner is unavailable.
 * AI contacts are matched by saved name, or by phone number when the sender shows up as a
 * number and it is on the lockdown whitelist.
 */
object AutoReplyProfile {

    private const val KEY_STATUS = "autoreply_status"
    private const val KEY_AI_CONTACTS = "autoreply_ai_contacts"
    const val MAX_AI_CONTACTS = 8

    private const val KEY_SHARE_LOCATION = "autoreply_share_location"

    /** Whether AI-chat contacts may be sent the owner's location when they ask. On by default. */
    fun shareLocation(): Boolean = JarvisPrefs.getBoolean(KEY_SHARE_LOCATION, true)
    fun setShareLocation(on: Boolean) = JarvisPrefs.putBoolean(KEY_SHARE_LOCATION, on)

    fun status(): String = JarvisPrefs.getString(KEY_STATUS) ?: ""

    fun setStatus(text: String) {
        val t = text.trim().take(200)
        if (t.isEmpty()) JarvisPrefs.remove(KEY_STATUS) else JarvisPrefs.putString(KEY_STATUS, t)
    }

    fun aiContacts(): List<String> =
        (JarvisPrefs.getString(KEY_AI_CONTACTS) ?: "").split("\n").map { it.trim() }.filter { it.isNotEmpty() }

    fun addAiContact(name: String): Boolean {
        val n = name.trim().lowercase()
        if (n.length < 2) return false
        val list = aiContacts().toMutableList()
        if (n in list) return true
        if (list.size >= MAX_AI_CONTACTS) return false
        list.add(n)
        JarvisPrefs.putString(KEY_AI_CONTACTS, list.joinToString("\n"))
        return true
    }

    fun removeAiContact(name: String): Boolean {
        val n = name.trim().lowercase()
        val list = aiContacts().toMutableList()
        if (!list.remove(n)) return false
        JarvisPrefs.putString(KEY_AI_CONTACTS, list.joinToString("\n"))
        return true
    }

    fun isAiContact(sender: String?): Boolean {
        val s = sender?.trim()?.lowercase().orEmpty()
        if (s.isEmpty()) return false
        if (aiContacts().any { it == s || (it.length >= 3 && s.contains(it)) }) return true
        val digits = s.filter { it.isDigit() }
        if (digits.length >= 10) {
            val tail = digits.takeLast(10)
            if (LockdownManager.whitelist().any { it.filter { c -> c.isDigit() }.takeLast(10) == tail }) return true
        }
        return false
    }
}
