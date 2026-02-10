//===== FILE: modules/engine/src/main/kotlin/com/jarvismini/engine/LlamaLLMEngine.kt =====
package com.jarvismini.engine

import android.content.Context
import android.util.Log
import com.jarvismini.engine.ai.AIService
import kotlinx.coroutines.runBlocking
import java.io.File

object LlamaLLMEngine : LLMEngine {

    private const val TAG = "LlamaLLMEngine"
    private var chatService: AIService? = null
    private var codeService: AIService? = null
    private var isInitialized = false
    private var appContext: Context? = null

    private lateinit var modelsDir: File

    override fun init(context: Context) {
        if (isInitialized) {
            Log.d(TAG, "Already initialized")
            return
        }

        appContext = context.applicationContext

        // Modern Android-safe path (no permissions required)
        modelsDir = File(
            context.getExternalFilesDir(null),
            "JarvisModels"
        )

        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }

        Log.d(TAG, "Models directory: ${modelsDir.absolutePath}")

        runBlocking {
            val modelFiles = modelsDir.listFiles { file ->
                file.isFile &&
                file.extension.equals("gguf", ignoreCase = true) &&
                !file.name.startsWith("ggml-vocab", ignoreCase = true)
            }?.toList() ?: emptyList()

            Log.d(TAG, "Found ${modelFiles.size} model files")

            modelFiles.forEach {
                Log.d(TAG, "Model: ${it.name} (${it.length() / 1024 / 1024} MB)")
            }

            // Load models
            for (file in modelFiles) {
                try {
                    val service = AIService(appContext!!)
                    val success = service.initialize(
                        file.absolutePath,
                        contextSize = 2048,
                        threads = 4
                    )

                    if (success) {
                        if (file.name.contains("coder", ignoreCase = true)) {
                            codeService = service
                            Log.i(TAG, "Loaded code model: ${file.name}")
                        } else {
                            chatService = service
                            Log.i(TAG, "Loaded chat model: ${file.name}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading model: ${file.name}", e)
                }
            }
        }

        isInitialized = true

        if (chatService == null && codeService == null) {
            Log.w(TAG, "No models loaded.")
            Log.w(TAG, "Place models in: ${modelsDir.absolutePath}")
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
                    val formattedPrompt =
                        "### Instruction:\n$prompt\n\n### Response:\n"
                    codeService!!.generate(
                        formattedPrompt,
                        maxTokens = 512,
                        temperature = 0.2f
                    )
                } else if (chatService != null) {
                    Log.d(TAG, "Using chat model")
                    chatService!!.generate(
                        prompt,
                        maxTokens = 256,
                        temperature = 0.7f
                    )
                } else {
                    "No AI model loaded. Place models in:\n${modelsDir.absolutePath}"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error generating reply", e)
                "Error generating response: ${e.message}"
            }
        }
    }

    private fun isCodeRelated(prompt: String): Boolean {
        val codeKeywords = listOf(
            "code", "program", "function", "class", "method",
            "python", "java", "kotlin", "javascript", "c++",
            "algorithm", "implement", "debug", "fix", "refactor"
        )

        val lowerPrompt = prompt.lowercase()
        return codeKeywords.any { lowerPrompt.contains(it) }
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
