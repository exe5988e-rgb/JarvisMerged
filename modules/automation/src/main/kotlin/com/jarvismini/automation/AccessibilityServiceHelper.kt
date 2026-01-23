package com.jarvismini.automation

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

object AccessibilityServiceHelper {

    lateinit var service: AccessibilityService

    fun init(s: AccessibilityService) {
        service = s
    }

    /**
     * Handles OnePlus Clock automation:
     * - Navigates to Stopwatch tab
     * - Clicks Start if found
     * - Clicks Pause if found
     */
    fun handleOnePlusClock() {
        val root = service.rootInActiveWindow ?: return

        // Navigate to Stopwatch tab first
        clickByText(root, "Stopwatch")

        // Try clicking Pause first (if running)
        if (clickByText(root, "Pause") || clickByText(root, "Stop")) return

        // Otherwise click Start
        clickByText(root, "Start")
    }

    /**
     * Recursive clickable search by text
     */
    private fun clickByText(root: AccessibilityNodeInfo, target: String): Boolean {
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)

        while (stack.isNotEmpty()) {
            val node = stack.removeFirst()
            val text = node.text?.toString()?.lowercase()

            if (node.isClickable && text == target.lowercase()) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { stack.add(it) }
            }
        }
        return false
    }
}
