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
 * LLM Engine implementation using llama.cpp through JNI.
 * 
 * This implementation follows the architecture from SmolChat-Android:
 * https://github.com/shubham0204/SmolChat-Android
 * 
 * Key improvements:
 * 1. Uses suspend functions instead of runBlocking to avoid deadlocks
 * 2. Proper coroutine dispatcher management with Dispatchers.IO
 * 3. Timeout handling with withTimeout
 * 4. Async model loading to prevent blocking the UI thread
 */
object LlamaLLMEngine : LLMEngine {
    
    private const val TAG = "LlamaLLMEngine"
    private var chatService: AIService? = null
    private var codeService: AIService? = null
    private var isInitialized = false
    private var appContext: Context? = null
    
    // Supervisor job for managing model loading coroutines
    private val engineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Get the directory where GGUF models are stored.
     * Uses the standard external storage path: /storage/emulated/0/JarvisModels
     */
    private fun getModelsDir(): File {
        val externalStorage = Environment.getExternalStorageDirectory()
        return File(externalStorage, "JarvisModels")
    }
    
    // Model configurations
    // These can be adjusted based on your specific models and device capabilities
    private val chatModelConfig = ModelConfig(
        type = ModelType.CHAT,
        name = "Phi-2",
        filename = "phi-2.Q4_K_M.gguf",
        contextSize = 2048,
        threads = 4
    )
    
    private val codeModelConfig = ModelConfig(
        type = ModelType.CODE,
        name = "DeepSeek Coder",
        filename = "deepseek-coder-1.3b-instruct.Q4_K_M.gguf",
        contextSize = 2048,
        threads = 4
    )

    /**
     * Initialize the engine and start loading models asynchronously.
     * 
     * This method returns immediately and loads models in the background.
     * The engine will return "Models loading..." responses until models are ready.
     */
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
        
        // Check storage permissions on Android 11+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val hasPermission = Environment.isExternalStorageManager()
            Log.d(TAG, "MANAGE_EXTERNAL_STORAGE: $hasPermission")
            if (!hasPermission) {
                Log.e(TAG, "❌ NO STORAGE PERMISSION - Cannot load models")
                return
            }
        }
        
        // Load models asynchronously in background
        engineScope.launch {
            loadModels(modelsDir)
        }
    }
    
    /**
     * Load both chat and code models from disk.
     * 
     * This runs on Dispatchers.IO to avoid blocking the main thread.
     * Models are loaded sequentially with a small delay between them
     * to avoid resource contention.
     */
    private suspend fun loadModels(modelsDir: File) = withContext(Dispatchers.IO) {
        try {
            // Load chat model
            val chatModelPath = File(modelsDir, chatModelConfig.filename)
            if (chatModelPath.exists() && chatModelPath.canRead()) {
                try {
                    Log.i(TAG, "Loading chat model: ${chatModelPath.name}")
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
            
            // Small delay between loading models to reduce memory pressure
            delay(1000)
            
            // Load code model
            val codeModelPath = File(modelsDir, codeModelConfig.filename)
            if (codeModelPath.exists() && codeModelPath.canRead()) {
                try {
                    Log.i(TAG, "Loading code model: ${codeModelPath.name}")
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
            
            Log.d(TAG, "=== MODEL LOADING COMPLETE ===")
            Log.d(TAG, "Chat service ready: ${chatService?.isReady() == true}")
            Log.d(TAG, "Code service ready: ${codeService?.isReady() == true}")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Fatal error during model loading", e)
        }
    }

    /**
     * Generate a reply to the given prompt.
     * 
     * This is a suspend function that properly integrates with Kotlin coroutines.
     * It should be called from a coroutine scope, NOT from runBlocking.
     * 
     * Pattern from SmolChat-Android:
     * - Use withContext(Dispatchers.IO) for the blocking native calls
     * - Use withTimeout to prevent hanging
     * - Proper error handling with try-catch
     * 
     * Example usage:
     * ```kotlin
     * scope.launch {
     *     try {
     *         val reply = llmEngine.generateReply(prompt)
     *         // Update UI with reply
     *     } catch (e: Exception) {
     *         // Handle error
     *     }
     * }
     * ```
     */
    override suspend fun generateReply(prompt: String): String {
        if (!isInitialized) {
            return "Engine initializing... Please wait."
        }
        
        // Check if models are loaded
        if (chatService == null && codeService == null) {
            return "Models loading... Please try again in a few moments."
        }
        
        // Determine which model to use based on the prompt
        val isCodeRequest = isCodeRelated(prompt)
        
        // ✅ FIXED: Use withContext directly instead of runBlocking
        // This allows the coroutine dispatcher to manage the thread properly
        return withContext(Dispatchers.IO) {
            try {
                // Set a 30 second timeout for generation
                withTimeout(30000L) {
                    if (isCodeRequest && codeService != null) {
                        Log.d(TAG, "Using code model for request")
                        // Format prompt for code model
                        val formattedPrompt = "### Instruction:\n$prompt\n\n### Response:\n"
                        // AIService.generate is already a suspend function
                        codeService!!.generate(
                            formattedPrompt, 
                            maxTokens = 256, 
                            temperature = 0.2f
                        )
                    } else if (chatService != null) {
                        Log.d(TAG, "Using chat model for request")
                        // AIService.generate is already a suspend function
                        chatService!!.generate(
                            prompt, 
                            maxTokens = 128, 
                            temperature = 0.7f
                        )
                    } else {
                        "No models available. Please check model files in /storage/emulated/0/JarvisModels/"
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Generation timed out after 30 seconds")
                "Request timed out. Try a shorter prompt or simpler question."
            } catch (e: CancellationException) {
                Log.e(TAG, "Generation was cancelled")
                "Generation cancelled."
            } catch (e: Exception) {
                Log.e(TAG, "Generation error: ${e.message}", e)
                "Error generating response: ${e.message ?: "Unknown error"}"
            }
        }
    }
    
    /**
     * Determine if a prompt is code-related based on keywords.
     * 
     * This helps select the appropriate model (code vs chat).
     */
    private fun isCodeRelated(prompt: String): Boolean {
        val codeKeywords = listOf(
            "code", "program", "function", "class", "method",
            "python", "java", "kotlin", "javascript", "c++",
            "algorithm", "implement", "debug", "fix", "refactor",
            "compile", "syntax", "variable", "loop", "array"
        )
        val lowerPrompt = prompt.lowercase()
        return codeKeywords.any { lowerPrompt.contains(it) }
    }
    
    /**
     * Release resources and cleanup.
     * 
     * This should be called when the engine is no longer needed,
     * typically in Application.onTerminate() or Activity.onDestroy().
     */
    fun release() {
        try {
            Log.d(TAG, "Releasing LLM engine resources")
            chatService?.release()
            codeService?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error during release", e)
        }
        
        chatService = null
        codeService = null
        isInitialized = false
        appContext = null
        
        Log.d(TAG, "LLM engine released")
    }
}
