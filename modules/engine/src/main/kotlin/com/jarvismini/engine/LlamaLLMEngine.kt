package com.jarvismini.engine

import android.content.Context
import android.util.Log
import com.jarvismini.engine.ai.AIService
import com.jarvismini.engine.ai.ModelPathManager
import kotlinx.coroutines.runBlocking
import java.io.File

object LlamaLLMEngine : LLMEngine {

    private const val TAG = "LlamaLLMEngine"

    private var chatService: AIService? = null
    private var codeService: AIService? = null
    private var isInitialized = false
    private var appContext: Context? = null

    override fun init(context: Context) {
        if (isInitialized) {
            Log.d(TAG, "Already initialized")
            return
        }

        appContext = context.applicationContext
        Log.d(TAG, "Initializing LlamaLLMEngine")

        runBlocking {
            val modelFiles = ModelPathManager.listModelFiles(appContext!!)

            if (modelFiles.isEmpty()) {
                Log.w(TAG, ModelPathManager.getNoModelsErrorMessage(appContext!!))
                return@runBlocking
            }

            // Load first model as chat model
            try {
                val chatModel = modelFiles[0]
                val service = AIService(appContext!!)
                val success = service.initialize(chatModel.absolutePath)

                if (success) {
                    chatService = service
                    Log.i(TAG, "Chat model loaded: ${chatModel.name}")
                } else {
                    Log.e(TAG, "Failed to load chat model")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading chat model", e)
            }

            // Load second model (if present) as code model
            if (modelFiles.size > 1) {
                try {
                    val codeModel = modelFiles[1]
                    val service = AIService(appContext!!)
                    val success = service.initialize(codeModel.absolutePath)

                    if (success) {
                        codeService = service
                        Log.i(TAG, "Code model loaded: ${codeModel.name}")
                    } else {
                        Log.e(TAG, "Failed to load code model")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading code model", e)
                }
            }
        }

        isInitialized = true

        if (chatService == null && codeService == null) {
            Log.w(TAG, "No models loaded.")
        }
    }

    override fun generateReply(prompt: String): String {
        if (!isInitialized) {
            Log.e(TAG, "Not initialized")
            return "AI is initializing. Please try again."
        }

        val isCodeRequest = isCodeRelated(prompt)

        return runBlocking {
            try {
                if (isCodeRequest && codeService != null) {
                    Log.d(TAG, "Using code model")
                    codeService!!.generate(prompt)
                } else if (chatService != null) {
                    Log.d(TAG, "Using chat model")
                    chatService!!.generate(prompt)
                } else {
                    Log.w(TAG, "No model available")
                    "AI models not loaded."
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error generating reply", e)
                "Error: ${e.message}"
            }
        }
    }

    private fun isCodeRelated(prompt: String): Boolean {
        val codeKeywords = listOf(
            "code", "program", "function", "class", "method",
            "python", "java", "kotlin", "javascript", "c++",
            "algorithm", "implement", "debug", "fix", "refactor"
        )
        val lower = prompt.lowercase()
        return codeKeywords.any { lower.contains(it) }
    }

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
