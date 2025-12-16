package com.jarvismini.smart

import android.view.accessibility.AccessibilityNodeInfo

/**
 * ChatTypeDetector: heuristics to detect group vs personal chat from AccessibilityNodeInfo.
 *
 * This tries several strategies:
 *  - check common WhatsApp view ids for group indicators
 *  - read toolbar/title node and look for participant patterns
 *  - scan visible text for keywords like "participants", "admins", etc.
 */
object ChatTypeDetector {

    private val groupKeywords = listOf(
        "participants",
        "admins",
        "member",
        "you joined",
        "added",
        "left",
        "Group"
    )

    /**
     * Return true if given window root looks like a group chat.
     * Accepts null root (returns false).
     */
    fun isGroupChat(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false

        // 1. Try toolbar title nodes (common WhatsApp IDs)
        val toolbarIds = listOf(
            "com.whatsapp:id/conversation_contact_name",
            "com.whatsapp:id/contact_name",
            "com.whatsapp:id/toolbar_title"
        )

        for (id in toolbarIds) {
            try {
                val nodes = root.findAccessibilityNodeInfosByViewId(id)
                if (!nodes.isNullOrEmpty()) {
                    val txt = nodes.first().text?.toString() ?: ""
                    if (looksLikeGroupTitle(txt)) return true
                }
            } catch (_: Exception) {
                // ignore and continue
            }
        }

        // 2. Check for explicit group info container
        try {
            val groupInfo = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/group_info_container")
            if (!groupInfo.isNullOrEmpty()) return true
        } catch (_: Exception) { }

        // 3. Scan visible text for group keywords
        val texts = collectVisibleTexts(root, 200) // limit to avoid huge searches
        for (t in texts) {
            if (groupKeywords.any { kw -> t.contains(kw, ignoreCase = true) }) return true
        }

        return false
    }

    private fun looksLikeGroupTitle(title: String): Boolean {
        if (title.isBlank()) return false
        // groups often have count like "Family (10)" or include keywords
        if (title.contains("(") && title.contains(")")) return true
        return groupKeywords.any { title.contains(it, ignoreCase = true) }
    }

    private fun collectVisibleTexts(root: AccessibilityNodeInfo, limit: Int): List<String> {
        val out = mutableListOf<String>()
        fun walk(n: AccessibilityNodeInfo?) {
            if (n == null || out.size >= limit) return
            n.text?.let { if (it.isNotBlank()) out.add(it.toString()) }
            for (i in 0 until n.childCount) {
                val c = n.getChild(i) ?: continue
                walk(c)
            }
        }
        walk(root)
        return out
    }
}
