package com.jarvismini.engine

import android.content.Context

/**
 * ✅ FIXED: Changed generateReply to suspend function
 * 
 * This is the ROOT CAUSE of the crash - the old interface had a blocking function
 * which caused UI freezes when called from coroutines
 */
interface LLMEngine {
    fun init(context: Context)
    
    /**
     * ✅ CHANGED from blocking to suspend function
     * This allows proper coroutine usage without blocking threads
     */
    suspend fun generateReply(prompt: String): String
}
