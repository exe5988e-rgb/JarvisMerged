package com.jarvismini.automation

import android.view.accessibility.AccessibilityNodeInfo

object MessageSender {
    fun sendMessage(root: AccessibilityNodeInfo?, message: String) {
        root ?: return
        try {
            val inputField = NodeFinder.findInputField(root)
            inputField?.let {
                val args = android.os.Bundle()
                args.putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, message)
                it.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                it.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
        } catch (_: Exception) {}
    }
}
