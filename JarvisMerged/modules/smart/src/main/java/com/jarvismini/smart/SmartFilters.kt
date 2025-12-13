object SmartFilters {

    fun isChatAllowed(root: AccessibilityNodeInfo?, sender: String, lastMsg: String): Boolean {
        if (root == null) return false

        // 1. block if detected as group chat
        if (ChatTypeDetector.isGroupChat(root)) return false
        if (GroupDetection.isGroupByName(sender)) return false

        // 2. block empty/emoji-only messages
        if (lastMsg.isBlank()) return false
        if (lastMsg.length <= 2 && !lastMsg.any { it.isLetterOrDigit() }) return false

        return true
    }
}
