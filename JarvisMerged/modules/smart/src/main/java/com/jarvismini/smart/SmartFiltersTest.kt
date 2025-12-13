package com.jarvismini.smart

import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmartFiltersTest {

    // Dummy root (SmartFilters requires non-null root)
    private fun fakeRoot(): AccessibilityNodeInfo {
        return AccessibilityNodeInfo.obtain()
    }

    @Test
    fun test_personalChat_allowsNormalMessage() {
        val allowed = SmartFilters.isChatAllowed(
            root = fakeRoot(),
            sender = "Alice",
            lastMsg = "Hey, how are you?"
        )

        assertTrue(allowed)
    }

    @Test
    fun test_groupChat_detectedByName_blocks() {
        val allowed = SmartFilters.isChatAllowed(
            root = fakeRoot(),
            sender = "Family Group",
            lastMsg = "Hello"
        )

        assertFalse(allowed)
    }

    @Test
    fun test_emptyMessage_isBlocked() {
        val allowed = SmartFilters.isChatAllowed(
            root = fakeRoot(),
            sender = "Bob",
            lastMsg = ""
        )

        assertFalse(allowed)
    }

    @Test
    fun test_emojiOnlyMessage_isBlocked() {
        val allowed = SmartFilters.isChatAllowed(
            root = fakeRoot(),
            sender = "Charlie",
            lastMsg = ""
        )

        assertFalse(allowed)
    }
}
