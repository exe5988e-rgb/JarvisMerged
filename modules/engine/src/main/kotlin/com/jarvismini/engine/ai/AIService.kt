package com.jarvismini.engine.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.File

class AIService(private val context: Context) {
    private var handle: Long = 0
    private var isInitialized = false
    private var currentModelPath: String? = null

    companion object {
        private const val TAG = "AIService"
    }
    
    suspend fun initializeWithPath(
        modelPath: String,
        nCtx: Int = 2048,
        nThreads: Int = 4
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Initializing with: $modelPath")
            
            val modelFile = File(modelPath)
            if (!modelFile.exists()) {
                Log.e(TAG, "Model not found: $modelPath")
                return@withContext false
            }
            
            Log.d(TAG, "Model size: ${modelFile.length() / (1024 * 1024)} MB")
            
            handle = LlamaNative.nativeInit()
            
            if (handle == 0L) {
                Log.e(TAG, "Failed to create context")
                return@withContext false
            }
            
            Log.d(TAG, "Loading model (ctx=$nCtx, threads=$nThreads)...")
            
            // ✅ NEW: Add timeout to model loading
            val loaded = withTimeout(120000L) {  // 2 minutes max for loading
                LlamaNative.nativeLoad(handle, modelPath, nCtx, nThreads)
            }
            
            if (loaded) {
                isInitialized = true
                currentModelPath = modelPath
                Log.i(TAG, "✅ Model loaded: ${modelFile.name}")
            } else {
                Log.e(TAG, "❌ Load failed")
                LlamaNative.nativeRelease(handle)
                handle = 0
            }
            
            loaded
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "❌ Model loading timeout (2 min)")
            if (handle != 0L) {
                LlamaNative.nativeRelease(handle)
                handle = 0
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}", e)
            if (handle != 0L) {
                LlamaNative.nativeRelease(handle)
                handle = 0
            }
            false
        }
    }

    /**
     * ✅ IMPROVED: Better timeout handling for generation
     */
    suspend fun generate(prompt: String, maxTokens: Int = 256, temperature: Float = 0.7f): String =
        withContext(Dispatchers.IO) {
            if (!isInitialized || handle == 0L) {
                Log.e(TAG, "Not initialized")
                return@withContext ""
            }
            
            try {
                Log.d(TAG, "Generating (${prompt.take(30)}...)")
                
                // ✅ IMPROVED: Timeout wraps native call
                // Note: This may not interrupt native code, but prevents infinite Kotlin wait
                withTimeout(45000L) {  // 45 seconds for generation
                    val result = LlamaNative.nativeGenerate(handle, prompt, maxTokens, temperature)
                    Log.d(TAG, "Generation complete (${result.length} chars)")
                    result
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "⏱️ Generation timeout - native call may still be running")
                // Attempt to stop generation
                try {
                    LlamaNative.nativeStopGeneration(handle)
                } catch (stopError: Exception) {
                    Log.e(TAG, "Failed to stop generation: ${stopError.message}")
                }
                ""
            } catch (e: Exception) {
                Log.e(TAG, "Generation error: ${e.message}", e)
                ""
            }
        }
    
    fun stopGeneration() {
        if (handle != 0L) {
            try {
                LlamaNative.nativeStopGeneration(handle)
                Log.d(TAG, "Stop signal sent")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop: ${e.message}")
            }
        }
    }

    fun release() {
        if (handle != 0L) {
            Log.d(TAG, "Releasing model")
            LlamaNative.nativeRelease(handle)
            handle = 0
            isInitialized = false
            currentModelPath = null
        }
    }
    
    fun isReady(): Boolean = isInitialized && handle != 0L
    fun getCurrentModel(): String? = currentModelPath
}
