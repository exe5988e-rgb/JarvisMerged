package com.jarvismini.automation

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo

object AccessibilityServiceHelper {
    lateinit var service: AccessibilityService

    fun init(s: AccessibilityService) {
        service = s
    }

    fun getRootNode(): AccessibilityNodeInfo? {
        return service.rootInActiveWindow
    }
}
