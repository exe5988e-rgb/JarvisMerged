package com.jarvismini.automation

import com.jarvismini.engine.ReplyReason
import com.jarvismini.core.JarvisMode

data class AutoReplyContext(
    val reason: ReplyReason,
    val currentMode: JarvisMode,
    val isImportant: Boolean = false
)
