package com.jarvismini

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.jarvismini.automation.AutomationEngine

class AppAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Unified event handler — this covers notifications + chat changes
        AutomationEngine.handleEvent(this, event)
    }

    override fun onInterrupt() {}
}
