package com.jarvismini.automation

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo
import com.jarvismini.core.JarvisMode
import com.jarvismini.core.JarvisState
import java.util.ArrayDeque

object AccessibilityServiceHelper {

    lateinit var service: AccessibilityService

    fun init(s: AccessibilityService) {
        service = s
    }

    fun getRootNode(): AccessibilityNodeInfo? {
        return service.rootInActiveWindow
    }

    fun handleOnePlusClock() {
        val root = getRootNode() ?: return

        if (JarvisState.currentMode == JarvisMode.WORK) {
            clickStart(root)
        } else {
            clickStop(root)
        }
    }

    // ▶️ START
    private fun clickStart(root: AccessibilityNodeInfo) {
        val nodes = root.findAccessibilityNodeInfosByText("Start")
        for (n in nodes) {
            if (n.isClickable) {
                n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return
            }
        }
    }

    // ⏹ STOP (Pause)
    private fun clickStop(root: AccessibilityNodeInfo) {
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)

        while (stack.isNotEmpty()) {
            val node = stack.removeFirst()

            val text = node.text?.toString()?.lowercase()
            if (
                node.isClickable &&
                (text == "pause" || text == "stop")
            ) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { stack.add(it) }
            }
        }
    }
}
