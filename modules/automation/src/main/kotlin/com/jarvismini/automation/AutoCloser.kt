package com.jarvismini.automation

import android.view.accessibility.AccessibilityNodeInfo

object AutoCloser {
    fun closeChat(root: AccessibilityNodeInfo?) {
        val back = try {
            root?.findAccessibilityNodeInfosByViewId("com.whatsapp:id/back")
        } catch (_: Exception) { null }

        if (!back.isNullOrEmpty()) {
            back.first().performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }
}