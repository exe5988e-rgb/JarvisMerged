package com.jarvismini.automation

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

object ChatOpener {

    private const val TAG = "JarvisMini-ChatOpener"

    private val unreadBadgeIds = listOf(
        "com.whatsapp:id/unread_indicator",
        "com.whatsapp:id/unread_count",
        "com.whatsapp:id/message_count"
    )

    fun openChat(service: AccessibilityService, root: AccessibilityNodeInfo?) {
        root ?: return

        // Try “new message”
        val nodes = root.findAccessibilityNodeInfosByText("new message")
        if (!nodes.isNullOrEmpty()) {
            nodes.first().performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.i(TAG, "Opened chat via 'new message'")
            return
        }

        // Try unread badge
        for (id in unreadBadgeIds) {
            try {
                val badges = root.findAccessibilityNodeInfosByViewId(id)
                if (!badges.isNullOrEmpty()) {
                    badges.first().performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Log.i(TAG, "Opened chat via unread badge: $id")
                    return
                }
            } catch (_: Exception) {}
        }
    }
}