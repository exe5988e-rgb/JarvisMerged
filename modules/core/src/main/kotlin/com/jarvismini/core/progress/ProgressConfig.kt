package com.jarvismini.core.progress

data class ProgressConfig(
    val enabled: Boolean,
    val retryEnabled: Boolean,
    val retryDelayMs: Long,
    val ttsEnabled: Boolean,
    val ttsRepeat: Boolean,
    val quietStart: String,
    val quietEnd: String
)
