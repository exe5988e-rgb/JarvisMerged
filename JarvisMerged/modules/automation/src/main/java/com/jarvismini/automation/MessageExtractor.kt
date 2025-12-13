package com.jarvismini.automation

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Extracts sender name + last received message text from WhatsApp.
 */
object MessageExtractor {

    private val chatTitleIds = listOf(
        "com.whatsapp:id/conversation_contact_name",
        "com.whatsapp:id/contact_name",
        "com.whatsapp:id/toolbar_title"
    )

    private val incomingMessageIds = listOf(
        "com.whatsapp:id/message_text",
        "com.whatsapp:id/message_text_in"
    )

    fun getSenderName(root: AccessibilityNodeInfo): String {
        chatTitleIds.forEach { id ->
            try {
                val list = root.findAccessibilityNodeInfosByViewId(id)
                if (!list.isNullOrEmpty()) {
                    return list.first().text?.toString()?.trim() ?: ""
                }
            } catch (_: Exception) {}
        }
        return ""
    }

    fun getLastIncomingMessage(root: AccessibilityNodeInfo): String {
        for (id in incomingMessageIds) {
            try {
                val nodes = root.findAccessibilityNodeInfosByViewId(id)
                if (!nodes.isNullOrEmpty()) {
                    // last element is latest chat message
                    return nodes.last().text?.toString() ?: ""
                }
            } catch (_: Exception) {}
        }
        return ""
    }
}
