package com.jarvismini

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class AppAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // TODO: process event here
    }

    override fun onInterrupt() {
        // Required override
    }
}
