package com.jarvismini.engine.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AIService(private val context: Context) {
    private var handle: Long = 0
    private var isInitialized = false
    private var currentModelPath: String? = null

    /**
     * Initialize with automatic model detection
     */
    suspend fun initialize(
        modelName: String? = null,
        nCtx: Int = 2048, 
        nThreads: Int = 4
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Initializing AIService")
            
            // Check if models exist
            if (!ModelPathManager.hasModels(context)) {
                Log.e(TAG, ModelPathManager.getNoModelsErrorMessage(context))
                return@withContext false
            }
            
            // Get model file
            val modelFile = if (modelName != null) {
                ModelPathManager.findModel(context, modelName)
            } else {
                ModelPathManager.getDefaultModel(context)
            }
            
            if (modelFile == null || !modelFile.exists()) {
                Log.e(TAG, "Model file not found: $modelName")
                Log.e(TAG, "Available models: ${ModelPathManager.listModelFiles(context).joinToString { it.name }}")
                return@withContext false
            }
            
            return@withContext initializeWithPath(modelFile.absolutePath, nCtx, nThreads)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing AIService", e)
            false
        }
    }
    
    /**
     * Initialize with explicit model path (for advanced use)
     */
    suspend fun initializeWithPath(
        modelPath: String,
        nCtx: Int = 2048,
        nThreads: Int = 4
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Initializing AIService with path: $modelPath")
            
            // Verify file exists
            val modelFile = File(modelPath)
            if (!modelFile.exists()) {
                Log.e(TAG, "Model file does not exist: $modelPath")
                return@withContext false
            }
            
            Log.d(TAG, "Model file size: ${modelFile.length() / 1024 / 1024}MB")
            
            handle = LlamaNative.nativeInit()
            
            if (handle == 0L) {
                Log.e(TAG, "Failed to create native context")
                return@withContext false
            }
            
            Log.d(TAG, "Loading model: $modelPath (ctx=$nCtx, threads=$nThreads)")
            val loaded = LlamaNative.nativeLoad(handle, modelPath, nCtx, nThreads)
            
            if (loaded) {
                isInitialized = true
                currentModelPath = modelPath
                Log.d(TAG, "Model loaded successfully: ${modelFile.name}")
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
            currentModelPath = null
        }
    }
    
    fun isReady(): Boolean = isInitialized && handle != 0L
    
    fun getCurrentModel(): String? = currentModelPath

    companion object {
        private const val TAG = "AIService"
    }
}
