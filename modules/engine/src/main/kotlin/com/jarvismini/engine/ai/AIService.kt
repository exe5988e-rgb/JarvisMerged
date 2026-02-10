package com.jarvismini.engine.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AIService {
    private var handle: Long = 0
    private var isInitialized = false

    suspend fun initialize(modelPath: String, nCtx: Int = 2048, nThreads: Int = 4): Boolean = 
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Initializing AIService")
                handle = LlamaNative.nativeInit()
                
                if (handle == 0L) {
                    Log.e(TAG, "Failed to create native context")
                    return@withContext false
                }
                
                Log.d(TAG, "Loading model: $modelPath")
                val loaded = LlamaNative.nativeLoad(handle, modelPath, nCtx, nThreads)
                
                if (loaded) {
                    isInitialized = true
                    Log.d(TAG, "Model loaded successfully")
                } else {
                    Log.e(TAG, "Failed to load model")
                    LlamaNative.nativeRelease(handle)
                    handle = 0
                }
                
                loaded
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing AIService", e)
                false
            }
        }

    suspend fun generate(prompt: String, maxTokens: Int = 256, temperature: Float = 0.7f): String =
        withContext(Dispatchers.IO) {
            if (!isInitialized || handle == 0L) {
                Log.e(TAG, "Service not initialized")
                return@withContext ""
            }
            
            try {
                Log.d(TAG, "Generating response for prompt: ${prompt.take(50)}...")
                LlamaNative.nativeGenerate(handle, prompt, maxTokens, temperature)
            } catch (e: Exception) {
                Log.e(TAG, "Error generating response", e)
                ""
            }
        }

    fun release() {
        if (handle != 0L) {
            Log.d(TAG, "Releasing AIService")
            LlamaNative.nativeRelease(handle)
            handle = 0
            isInitialized = false
        }
    }

    companion object {
        private const val TAG = "AIService"
    }
}
