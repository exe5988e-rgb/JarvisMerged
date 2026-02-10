package com.jarvismini.engine

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import com.jarvismini.engine.ai.AIService
import com.jarvismini.engine.ai.ModelConfig
import com.jarvismini.engine.ai.ModelType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

object LlamaLLMEngine : LLMEngine {
    
    private const val TAG = "LlamaLLMEngine"
    private var chatService: AIService? = null
    private var codeService: AIService? = null
    private var isInitialized = false
    private var appContext: Context? = null
    private var modelsLoadAttempted = false
    
    // ✅ Use function instead of val so path is evaluated with current permissions
    private fun getModelsDir(): File {
        val externalStorage = Environment.getExternalStorageDirectory()
        val dir = File(externalStorage, "JarvisModels")
        return dir
    }
    
    // Model configurations
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

    override fun init(context: Context) {
        if (isInitialized) {
            Log.d(TAG, "Already initialized")
            return
        }
        
        appContext = context.applicationContext
        val modelsDir = getModelsDir()
        
        Log.d(TAG, "=== INITIALIZING JARVIS LLM ENGINE ===")
        Log.d(TAG, "External storage: ${Environment.getExternalStorageDirectory().absolutePath}")
        Log.d(TAG, "Models directory: ${modelsDir.absolutePath}")
        Log.d(TAG, "Directory exists: ${modelsDir.exists()}")
        Log.d(TAG, "Directory readable: ${modelsDir.canRead()}")
        
        // Check permissions on Android 11+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val hasPermission = Environment.isExternalStorageManager()
            Log.d(TAG, "Android ${Build.VERSION.SDK_INT} - MANAGE_EXTERNAL_STORAGE: $hasPermission")
            if (!hasPermission) {
                Log.e(TAG, "❌ PERMISSION NOT GRANTED! Cannot access models.")
                Log.e(TAG, "Please grant 'All Files Access' in Settings → Apps → Jarvis")
                isInitialized = true
                return
            }
        }
        
        // List files for debugging
        if (modelsDir.exists() && modelsDir.isDirectory) {
            val files = modelsDir.listFiles()
            if (files != null && files.isNotEmpty()) {
                Log.d(TAG, "Found ${files.size} files in models directory:")
                files.forEach { file ->
                    val sizeMB = file.length() / (1024.0 * 1024.0)
                    Log.d(TAG, "  - ${file.name} (%.2f MB) readable=${file.canRead()}".format(sizeMB))
                }
            } else {
                Log.w(TAG, "Models directory is empty or not readable")
            }
        } else {
            Log.e(TAG, "Models directory does not exist!")
        }
        
        isInitialized = true
        Log.d(TAG, "=== INITIALIZATION COMPLETE (models will load on demand) ===")
    }
    
    /**
     * ✅ FIX: Load models lazily in background thread with proper suspend handling
     */
    private suspend fun ensureModelsLoaded() = withContext(Dispatchers.IO) {
        // If already loaded or attempted, skip
        if ((chatService != null || codeService != null) || modelsLoadAttempted) {
            return@withContext
        }
        
        modelsLoadAttempted = true
        val modelsDir = getModelsDir()
        
        // Try to load chat model
        val chatModelPath = File(modelsDir, chatModelConfig.filename)
        Log.d(TAG, "Looking for chat model: ${chatModelPath.absolutePath}")
        
        if (chatModelPath.exists() && chatModelPath.canRead()) {
            try {
                Log.i(TAG, "Loading chat model: ${chatModelConfig.name}")
                val service = AIService(appContext!!)
                
                // ✅ FIX: initializeWithPath is a suspend function, so we can call it directly
                val success = service.initializeWithPath(
                    chatModelPath.absolutePath,
                    chatModelConfig.contextSize,
                    chatModelConfig.threads
                )
                
                if (success) {
                    chatService = service
                    Log.i(TAG, "✅ Chat model loaded: ${chatModelConfig.name}")
                } else {
                    Log.e(TAG, "❌ Failed to load chat model")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error loading chat model: ${e.message}", e)
            }
        } else {
            Log.w(TAG, "❌ Chat model not found: ${chatModelPath.absolutePath}")
        }
        
        // Try to load code model
        val codeModelPath = File(modelsDir, codeModelConfig.filename)
        Log.d(TAG, "Looking for code model: ${codeModelPath.absolutePath}")
        
        if (codeModelPath.exists() && codeModelPath.canRead()) {
            try {
                Log.i(TAG, "Loading code model: ${codeModelConfig.name}")
                val service = AIService(appContext!!)
                
                // ✅ FIX: initializeWithPath is a suspend function
                val success = service.initializeWithPath(
                    codeModelPath.absolutePath,
                    codeModelConfig.contextSize,
                    codeModelConfig.threads
                )
                
                if (success) {
                    codeService = service
                    Log.i(TAG, "✅ Code model loaded: ${codeModelConfig.name}")
                } else {
                    Log.e(TAG, "❌ Failed to load code model")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error loading code model: ${e.message}", e)
            }
        } else {
            Log.w(TAG, "❌ Code model not found: ${codeModelPath.absolutePath}")
        }
        
        Log.d(TAG, "Chat service: ${if (chatService != null) "LOADED ✅" else "NOT LOADED ❌"}")
        Log.d(TAG, "Code service: ${if (codeService != null) "LOADED ✅" else "NOT LOADED ❌"}")
        
        if (chatService == null && codeService == null) {
            Log.e(TAG, "❌❌❌ NO MODELS LOADED! ❌❌❌")
            Log.e(TAG, "Expected location: ${modelsDir.absolutePath}")
            Log.e(TAG, "Expected files: ${chatModelConfig.filename}, ${codeModelConfig.filename}")
        }
    }

    /**
     * ✅ FIX: Async generation with timeout to prevent hanging
     */
    suspend fun generateReplyAsync(prompt: String): String {
        if (!isInitialized) {
            Log.e(TAG, "Not initialized")
            return "AI is initializing. Please try again."
        }
        
        return try {
            // Add timeout to prevent hanging forever (30 seconds)
            withTimeout(30000L) {
                Log.d(TAG, "Starting reply generation for: ${prompt.take(50)}...")
                
                // Ensure models are loaded
                ensureModelsLoaded()
                
                // Check if models loaded successfully
                if (chatService == null && codeService == null) {
                    return@withTimeout "AI models are not loaded. Please ensure models are in /sdcard/JarvisModels/"
                }
                
                // Determine if this is a code request
                val isCodeRequest = isCodeRelated(prompt)
                
                // Generate response
                withContext(Dispatchers.IO) {
                    try {
                        if (isCodeRequest && codeService != null) {
                            Log.d(TAG, "Using code model...")
                            val formattedPrompt = "### Instruction:\n$prompt\n\n### Response:\n"
                            val result = codeService!!.generate(formattedPrompt, maxTokens = 512, temperature = 0.2f)
                            Log.d(TAG, "Code model returned: ${result.take(50)}...")
                            result
                        } else if (chatService != null) {
                            Log.d(TAG, "Using chat model...")
                            val result = chatService!!.generate(prompt, maxTokens = 256, temperature = 0.7f)
                            Log.d(TAG, "Chat model returned: ${result.take(50)}...")
                            result
                        } else {
                            Log.w(TAG, "No model available")
                            "I'm Jarvis. My AI models are not loaded. Please ensure models are in /sdcard/JarvisModels/"
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during generation", e)
                        "Error generating response: ${e.message}"
                    }
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.e(TAG, "Generation timed out after 30 seconds")
            "Response generation timed out. The model may be too slow or stuck."
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in generateReplyAsync", e)
            "Error: ${e.message}"
        }
    }
    
    /**
     * Keep the old interface for backward compatibility
     */
    override fun generateReply(prompt: String): String {
        Log.w(TAG, "⚠️ Using blocking generateReply - use generateReplyAsync instead")
        return "Please use async version"
    }
    
    /**
     * Detect if the prompt is code-related
     */
    private fun isCodeRelated(prompt: String): Boolean {
        val codeKeywords = listOf(
            "code", "program", "function", "class", "method",
            "python", "java", "kotlin", "javascript", "c++",
            "algorithm", "implement", "debug", "fix", "refactor",
            "write code", "create a", "develop"
        )
        
        val lowerPrompt = prompt.lowercase()
        return codeKeywords.any { lowerPrompt.contains(it) }
    }
    
    /**
     * Release models
     */
    fun release() {
        chatService?.release()
        codeService?.release()
        chatService = null
        codeService = null
        isInitialized = false
        modelsLoadAttempted = false
    }
}
