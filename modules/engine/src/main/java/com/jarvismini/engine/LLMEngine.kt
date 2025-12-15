package com.jarvismini.engine

import android.content.Context

interface LLMEngine {
    fun init(context: Context)
    fun generateReply(prompt: String): String
}
