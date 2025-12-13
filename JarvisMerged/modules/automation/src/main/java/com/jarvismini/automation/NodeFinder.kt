package com.jarvismini.automation

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

/**
 * NodeFinder: Robust WhatsApp UI element detection.
 */
object NodeFinder {

    private const val TAG = "JarvisMini-NodeFinder"

    private val inputIds = listOf(
        "com.whatsapp:id/entry",
        "com.whatsapp:id/voice_note_text_input",
        "com.whatsapp:id/entry_container",
        "com.whatsapp:id/entry_text",
        "com.whatsapp:id/entry_input"
    )

    private val sendButtonIds = listOf(
        "com.whatsapp:id/send",
        "com.whatsapp:id/send_btn",
        "com.whatsapp:id/send_button",
        "com.whatsapp:id/send_icon",
        "com.whatsapp:id/send_message"
    )

    fun findInputField(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // 1️⃣ Try direct ID search
        for (id in inputIds) {
            safeFindById(root, id)?.firstOrNull()?.let {
                return it
            }
        }

        // 2️⃣ Try EditText fallback
        findByClass(root, "android.widget.EditText")?.let {
            return it
        }

        // 3️⃣ Try text input parents (some versions wrap it)
        for (id in inputIds) {
            safeFindById(root, id)?.firstOrNull()?.parent?.let { parent ->
                if (parent.className?.contains("EditText") == true)
                    return parent
            }
        }

        Log.w(TAG, "Input field not found")
        return null
    }

    fun findSendButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // 1️⃣ Direct ID search
        for (id in sendButtonIds) {
            safeFindById(root, id)?.firstOrNull()?.let {
                return it
            }
        }

        // 2️⃣ Fallback: ImageButton
        findByClass(root, "android.widget.ImageButton")?.let {
            return it
        }

        Log.w(TAG, "Send button not found")
        return null
    }

    private fun safeFindById(node: AccessibilityNodeInfo, id: String): List<AccessibilityNodeInfo>? {
        return try {
            node.findAccessibilityNodeInfosByViewId(id)
        } catch (_: Exception) {
            null
        }
    }

    private fun findByClass(node: AccessibilityNodeInfo, className: String): AccessibilityNodeInfo? {
        if (node.className == className) return node

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findByClass(child, className)?.let { return it }
        }
        return null
    }
}
