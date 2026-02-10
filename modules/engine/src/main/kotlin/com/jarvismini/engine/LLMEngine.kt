package com.jarvismini.engine

import android.content.Context

/**
 * Interface for LLM engines that generate text responses.
 * 
 * This interface uses suspend functions to properly integrate with Kotlin coroutines,
 * avoiding the deadlock issues that occur with runBlocking.
 * 
 * Inspired by SmolChat-Android's architecture:
 * https://github.com/shubham0204/SmolChat-Android
 */
interface LLMEngine {
    /**
     * Initialize the LLM engine with application context.
     * This should be called once during app startup.
     * 
     * @param context Application context for accessing resources and storage
     */
    fun init(context: Context)
    
    /**
     * Generate a reply to the given prompt.
     * 
     * This is a suspend function that should be called from a coroutine context.
     * It will execute on an appropriate dispatcher (typically Dispatchers.IO) internally.
     * 
     * IMPORTANT: Do NOT call this from runBlocking as it can cause deadlocks.
     * Instead, call it from a proper coroutine scope:
     * 
     * ```kotlin
     * viewModelScope.launch {
     *     val reply = llmEngine.generateReply(prompt)
     *     // Update UI with reply
     * }
     * ```
     * 
     * @param prompt The user's input prompt
     * @return The generated text response
     */
    suspend fun generateReply(prompt: String): String
}
