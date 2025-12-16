package com.jarvismini.smart

/**
 * GroupDetection: helper to determine group by sender name heuristics.
 * This is a lightweight fallback when AccessibilityNode root not available.
 */
object GroupDetection {

    private val groupNameIndicators = listOf("group", "team", "family", "friends", "community")

    /** Return true if the sender name strongly suggests a group. */
    fun isGroupByName(senderName: String?): Boolean {
        if (senderName == null) return false
        val s = senderName.lowercase().trim()
        if (s.isEmpty()) return false
        // typical "X (10)" style or presence of keywords
        if (s.contains("(") && s.contains(")")) return true
        return groupNameIndicators.any { s.contains(it) }
    }
}
