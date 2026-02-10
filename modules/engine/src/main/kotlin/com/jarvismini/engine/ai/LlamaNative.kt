package com.jarvismini.engine.ai

import android.util.Log

object LlamaNative {
    private const val TAG = "LlamaNative"
    private var backendInitialized = false
    
    init {
        System.loadLibrary("jarvis_native")
    }
    
    /**
     * Initialize backend ONCE for entire app
     * Call from Application.onCreate()
     */
    @Synchronized
    fun initBackend() {
        if (!backendInitialized) {
            Log.i(TAG, "Initializing llama backend")
            nativeBackendInit()
            backendInitialized = true
        }
    }
    
    /**
     * Free backend on app shutdown
     */
    @Synchronized
    fun freeBackend() {
        if (backendInitialized) {
            Log.i(TAG, "Freeing llama backend")
            nativeBackendFree()
            backendInitialized = false
        }
    }

    // Native methods
    external fun nativeBackendInit()
    external fun nativeBackendFree()
    external fun nativeInit(): Long
    external fun nativeLoad(handle: Long, modelPath: String, nCtx: Int, nThreads: Int): Boolean
    external fun nativeGenerate(handle: Long, prompt: String, maxTokens: Int, temp: Float): String
    external fun nativeStopGeneration(handle: Long)
    external fun nativeRelease(handle: Long)
}
