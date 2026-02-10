package com.jarvismini.engine

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import com.jarvismini.engine.ai.AIService
import com.jarvismini.engine.ai.ModelConfig
import com.jarvismini.engine.ai.ModelType
import kotlinx.coroutines.runBlocking
import java.io.File

object LlamaLLMEngine : LLMEngine {
    
    private const val TAG = "LlamaLLMEngine"
    private var chatService: AIService? = null
    private var codeService: AIService? = null
    private var isInitialized = false
    private var appContext: Context? = null
    
    // ✅ CRITICAL FIX: Use function instead of val so path is evaluated with current permissions
    // This was the main bug - modelsDir was initialized before permissions were granted!
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
        
        // Store application context
        appContext = context.applicationContext
        
        val modelsDir = getModelsDir()  // Get it fresh each time with current permissions
        
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
        
        Log.d(TAG, "Initializing LlamaLLMEngine")
        
        // Load models asynchronously
        runBlocking {
            // Try to load chat model
            val chatModelPath = File(modelsDir, chatModelConfig.filename)
            Log.d(TAG, "Looking for chat model: ${chatModelPath.absolutePath}")
            Log.d(TAG, "  exists=${chatModelPath.exists()}, readable=${chatModelPath.canRead()}, size=${chatModelPath.length()}")
            
            if (chatModelPath.exists() && chatModelPath.canRead()) {
                try {
                    Log.i(TAG, "Loading chat model: ${chatModelConfig.name}")
                    val service = AIService(appContext!!)
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
            Log.d(TAG, "  exists=${codeModelPath.exists()}, readable=${codeModelPath.canRead()}, size=${codeModelPath.length()}")
            
            if (codeModelPath.exists() && codeModelPath.canRead()) {
                try {
                    Log.i(TAG, "Loading code model: ${codeModelConfig.name}")
                    val service = AIService(appContext!!)
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
        }
        
        isInitialized = true
        
        Log.d(TAG, "=== INITIALIZATION COMPLETE ===")
        Log.d(TAG, "Chat service: ${if (chatService != null) "LOADED ✅" else "NOT LOADED ❌"}")
        Log.d(TAG, "Code service: ${if (codeService != null) "LOADED ✅" else "NOT LOADED ❌"}")
        
        if (chatService == null && codeService == null) {
            Log.e(TAG, "❌❌❌ NO MODELS LOADED! ❌❌❌")
            Log.e(TAG, "Expected location: ${modelsDir.absolutePath}")
            Log.e(TAG, "Expected files: ${chatModelConfig.filename}, ${codeModelConfig.filename}")
        }
    }

    override fun generateReply(prompt: String): String {
        if (!isInitialized) {
            Log.e(TAG, "Not initialized")
            return "AI is initializing. Please try again."
        }
        
        // Determine if this is a code request
        val isCodeRequest = isCodeRelated(prompt)
        
        return runBlocking {
            try {
                if (isCodeRequest && codeService != null) {
                    Log.d(TAG, "Using code model for: ${prompt.take(50)}...")
                    val formattedPrompt = "### Instruction:\n$prompt\n\n### Response:\n"
                    codeService!!.generate(formattedPrompt, maxTokens = 512, temperature = 0.2f)
                } else if (chatService != null) {
                    Log.d(TAG, "Using chat model for: ${prompt.take(50)}...")
                    chatService!!.generate(prompt, maxTokens = 256, temperature = 0.7f)
                } else {
                    // Fallback to stub response
                    Log.w(TAG, "No model available for generation")
                    "I'm Jarvis. My AI models are not loaded. Please ensure models are in /sdcard/JarvisModels/"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error generating reply", e)
                "Error generating response: ${e.message}"
            }
        }
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
        appContext = null
        Log.d(TAG, "Released all models")
    }
}
