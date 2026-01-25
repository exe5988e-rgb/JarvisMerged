package com.jarvismini.automation

import com.jarvismini.core.JarvisMode
import com.jarvismini.core.JarvisState

object ModeGuard {

    fun allowsReply(): Boolean {
        return when (JarvisState.currentMode) {
            JarvisMode.NORMAL -> true
            JarvisMode.WORK -> true
            JarvisMode.DRIVING -> true
            JarvisMode.SLEEP -> false
            JarvisMode.FOCUS -> false
        }
    }
}
