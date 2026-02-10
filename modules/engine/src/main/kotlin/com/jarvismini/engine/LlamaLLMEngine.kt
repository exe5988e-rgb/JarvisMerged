package com.jarvismini.engine

import android.content.Context
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
    
    private val modelsDir = File(Environment.getExternalStorageDirectory(), "JarvisModels")
    
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
        
        Log.d(TAG, "Initializing LlamaLLMEngine")
        
        // Load models asynchronously
        runBlocking {
            // Try to load chat model
            val chatModelPath = File(modelsDir, chatModelConfig.filename)
            if (chatModelPath.exists() && chatModelPath.canRead()) {
                try {
                    val service = AIService(appContext!!)
                    val success = service.initialize(
                        chatModelPath.absolutePath,
                        chatModelConfig.contextSize,
                        chatModelConfig.threads
                    )
                    
                    if (success) {
                        chatService = service
                        Log.i(TAG, "Chat model loaded: ${chatModelConfig.name}")
                    } else {
                        Log.e(TAG, "Failed to load chat model")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading chat model", e)
                }
            } else {
                Log.w(TAG, "Chat model not found at: ${chatModelPath.absolutePath}")
            }
            
            // Try to load code model
            val codeModelPath = File(modelsDir, codeModelConfig.filename)
            if (codeModelPath.exists() && codeModelPath.canRead()) {
                try {
                    val service = AIService(appContext!!)
                    val success = service.initialize(
                        codeModelPath.absolutePath,
                        codeModelConfig.contextSize,
                        codeModelConfig.threads
                    )
                    
                    if (success) {
                        codeService = service
                        Log.i(TAG, "Code model loaded: ${codeModelConfig.name}")
                    } else {
                        Log.e(TAG, "Failed to load code model")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading code model", e)
                }
            } else {
                Log.w(TAG, "Code model not found at: ${codeModelPath.absolutePath}")
            }
        }
        
        isInitialized = true
        
        if (chatService == null && codeService == null) {
            Log.w(TAG, "No models loaded. Place models in: ${modelsDir.absolutePath}")
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
