package com.jarvismini.smart

/**
 * CooldownManager: per-chat cooldown to avoid spam.
 *
 * Usage:
 *  if (!CooldownManager.canReply(sender)) return
 *  // reply...
 *  CooldownManager.markReplied(sender)
 */
object CooldownManager {
    private val lastReply = mutableMapOf<String, Long>()
    private const val DEFAULT_COOLDOWN_MS = 8_000L

    /** Check whether we can reply to this sender now (does not modify state). */
    fun canReply(sender: String, cooldownMs: Long = DEFAULT_COOLDOWN_MS): Boolean {
        val now = System.currentTimeMillis()
        val prev = lastReply[sender] ?: 0L
        return (now - prev) >= cooldownMs
    }

    /** Mark that we've replied to this sender now. */
    fun markReplied(sender: String) {
        lastReply[sender] = System.currentTimeMillis()
    }

    /** Force-reset cooldown for a sender (useful for tests / admin). */
    fun reset(sender: String) {
        lastReply.remove(sender)
    }

    /** Clear all tracked state (useful for tests). */
    fun clearAll() {
        lastReply.clear()
    }
}
