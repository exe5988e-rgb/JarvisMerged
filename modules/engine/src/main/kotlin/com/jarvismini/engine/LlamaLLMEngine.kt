package com.jarvismini.engine

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import com.jarvismini.engine.ai.AIService
import com.jarvismini.engine.ai.ModelConfig
import com.jarvismini.engine.ai.ModelType
import kotlinx.coroutines.*
import java.io.File

/**
 * UPDATED VERSION - Migrated to Qwen2.5-Coder-1.5B
 * 
 * CHANGES FROM PREVIOUS VERSION:
 * 1. Replaced Phi-2 with Qwen2.5-Coder (better performance, more stable)
 * 2. Replaced DeepSeek-Coder-1.3B with Qwen2.5-Coder (unified model for chat & code)
 * 3. Increased context size from 2048 to 4096 tokens
 * 4. Increased timeout from 30s to 120s for first-time model loading
 * 5. Optimized generation parameters for mobile devices
 */
object LlamaLLMEngine : LLMEngine {
    
    private const val TAG = "LlamaLLMEngine"
    private var chatService: AIService? = null
    private var codeService: AIService? = null
    private var isInitialized = false
    private var appContext: Context? = null
    
    // ✅ NEW: Track loading state
    var isLoading: Boolean = false
        private set
    
    // ✅ NEW: User's selected model preference
    var selectedModel: String = "auto"
    
    private val engineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private fun getModelsDir(): File {
        val externalStorage = Environment.getExternalStorageDirectory()
        return File(externalStorage, "JarvisModels")
    }
    
    // ✅ UPDATED: Qwen2.5-Coder for chat (better quality, faster, more stable)
    private val chatModelConfig = ModelConfig(
        type = ModelType.CHAT,
        name = "Qwen2.5-Coder",
        filename = "qwen2.5-coder-1.5b-instruct-q4_k_m.gguf",
        contextSize = 4096,  // Qwen2.5 supports longer context
        threads = 4
    )
    
    // ✅ UPDATED: Qwen2.5-Coder for code (same model, optimized for both tasks)
    private val codeModelConfig = ModelConfig(
        type = ModelType.CODE,
        name = "Qwen2.5-Coder",
        filename = "qwen2.5-coder-1.5b-instruct-q4_k_m.gguf",
        contextSize = 4096,  // Qwen2.5 supports longer context
        threads = 4
    )

    override fun init(context: Context) {
        if (isInitialized) {
            Log.d(TAG, "Already initialized")
            return
        }
        
        appContext = context.applicationContext
        isInitialized = true
        
        val modelsDir = getModelsDir()
        
        Log.d(TAG, "=== INITIALIZING JARVIS LLM ENGINE ===")
        Log.d(TAG, "Models directory: ${modelsDir.absolutePath}")
        Log.d(TAG, "Directory exists: ${modelsDir.exists()}")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val hasPermission = Environment.isExternalStorageManager()
            Log.d(TAG, "MANAGE_EXTERNAL_STORAGE: $hasPermission")
            if (!hasPermission) {
                Log.e(TAG, "❌ NO PERMISSION - Please grant file access in settings")
                return
            }
        }
        
        // Load models asynchronously
        engineScope.launch {
            loadModels(modelsDir)
        }
    }
    
    // ✅ NEW: Get current model status for UI
    fun getModelStatus(): ModelStatus {
        return ModelStatus(
            isLoading = isLoading,
            chatReady = chatService != null,
            codeReady = codeService != null
        )
    }
    
    private suspend fun loadModels(modelsDir: File) = withContext(Dispatchers.IO) {
        try {
            isLoading = true
            
            // Load chat model
            val chatModelPath = File(modelsDir, chatModelConfig.filename)
            if (chatModelPath.exists() && chatModelPath.canRead()) {
                try {
                    Log.i(TAG, "Loading chat model...")
                    val service = AIService(appContext!!)
                    
                    val success = service.initializeWithPath(
                        chatModelPath.absolutePath,
                        chatModelConfig.contextSize,
                        chatModelConfig.threads
                    )
                    
                    if (success) {
                        chatService = service
                        Log.i(TAG, "✅ Chat model loaded successfully")
                    } else {
                        Log.e(TAG, "❌ Chat model failed to load")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Chat model error: ${e.message}", e)
                }
            } else {
                Log.w(TAG, "Chat model not found: ${chatModelPath.absolutePath}")
            }
            
            // Small delay between loading models
            delay(1000)
            
            // Load code model (same file as chat for Qwen2.5-Coder)
            val codeModelPath = File(modelsDir, codeModelConfig.filename)
            if (codeModelPath.exists() && codeModelPath.canRead()) {
                try {
                    Log.i(TAG, "Loading code model...")
                    val service = AIService(appContext!!)
                    
                    val success = service.initializeWithPath(
                        codeModelPath.absolutePath,
                        codeModelConfig.contextSize,
                        codeModelConfig.threads
                    )
                    
                    if (success) {
                        codeService = service
                        Log.i(TAG, "✅ Code model loaded successfully")
                    } else {
                        Log.e(TAG, "❌ Code model failed to load")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Code model error: ${e.message}", e)
                }
            } else {
                Log.w(TAG, "Code model not found: ${codeModelPath.absolutePath}")
            }
            
            isLoading = false
            
            Log.d(TAG, "=== LOADING COMPLETE ===")
            Log.d(TAG, "Chat: ${chatService != null}, Code: ${codeService != null}")
            
            if (chatService == null && codeService == null) {
                Log.e(TAG, "⚠️ No models loaded. Please place .gguf files in ${modelsDir.absolutePath}")
            }
            Unit
            
        } catch (e: Exception) {
            isLoading = false
            Log.e(TAG, "❌ Fatal error during model loading", e)
        }
    }

    override suspend fun generateReply(prompt: String): String {
        if (!isInitialized) {
            Log.w(TAG, "Engine not initialized yet")
            return "AI engine is initializing... Please wait a moment."
        }
        
        if (chatService == null && codeService == null) {
            Log.e(TAG, "No models available")
            val modelsDir = getModelsDir()
            return "No AI models loaded. Please place model files in:\n${modelsDir.absolutePath}"
        }
        
        // ✅ NEW: Use selected model preference
        val shouldUseCode = when (selectedModel) {
            "code" -> true
            "chat" -> false
            else -> isCodeRelated(prompt) // "auto" mode
        }
        
        return withContext(Dispatchers.IO) {
            try {
                // ✅ INCREASED: Timeout from 30s to 120s for first-time generation
                withTimeout(120000L) {
                    when {
                        shouldUseCode && codeService != null -> {
                            Log.d(TAG, "Using code model for generation")
                            // ✅ UPDATED: Qwen2.5-Coder format (no special prompt formatting needed)
                            codeService!!.generate(prompt, maxTokens = 256, temperature = 0.2f)
                        }
                        chatService != null -> {
                            Log.d(TAG, "Using chat model for generation")
                            // ✅ UPDATED: Increased tokens from 128 to 256 for better responses
                            chatService!!.generate(prompt, maxTokens = 256, temperature = 0.7f)
                        }
                        else -> {
                            "Models are still loading... Please wait a few more seconds."
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Generation timeout after 120 seconds")
                "Request timed out. The model may still be loading. Please try again."
            } catch (e: CancellationException) {
                Log.w(TAG, "Generation cancelled")
                "Request cancelled."
            } catch (e: Exception) {
                Log.e(TAG, "Generation error: ${e.message}", e)
                "Error generating response: ${e.message ?: "Unknown error"}"
            }
        }
    }
    
    private fun isCodeRelated(prompt: String): Boolean {
        val codeKeywords = listOf(
            "code", "program", "function", "class", "method",
            "python", "java", "kotlin", "javascript", "c++",
            "algorithm", "implement", "debug", "fix", "refactor"
        )
        return codeKeywords.any { prompt.lowercase().contains(it) }
    }
    
    fun release() {
        try {
            chatService?.release()
            codeService?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Release error", e)
        }
        chatService = null
        codeService = null
        isInitialized = false
        appContext = null
        isLoading = false
    }
}
