package com.jarvismini.engine

import android.content.Context
import android.util.Log
import com.jarvismini.engine.ai.ModelPathManager
import java.io.File

class LlamaLLMEngine(private val context: Context) {

    companion object {
        private const val TAG = "LlamaLLMEngine"

        // Model name hints
        private const val CHAT_MODEL_HINT = "chat"
        private const val CODE_MODEL_HINT = "code"
    }

    private var chatModel: LlamaModel? = null
    private var codeModel: LlamaModel? = null

    /**
     * Initialize both models if available
     */
    fun initialize() {
        try {
            val models = ModelPathManager.listModelFiles(context)

            if (models.isEmpty()) {
                Log.e(TAG, "No models found.")
                return
            }

            var chatModelFile: File? = null
            var codeModelFile: File? = null

            for (model in models) {
                val name = model.name.lowercase()

                if (name.contains(CHAT_MODEL_HINT) && chatModelFile == null) {
                    chatModelFile = model
                } else if (name.contains(CODE_MODEL_HINT) && codeModelFile == null) {
                    codeModelFile = model
                }
            }

            // Fallback logic
            if (chatModelFile == null && models.isNotEmpty()) {
                chatModelFile = models[0]
            }

            if (codeModelFile == null && models.size > 1) {
                codeModelFile = models[1]
            }

            // Load chat model
            chatModelFile?.let {
                Log.d(TAG, "Loading chat model: ${it.name}")
                chatModel = LlamaModel(
                    modelPath = it.absolutePath
                )
            }

            // Load code model
            codeModelFile?.let {
                Log.d(TAG, "Loading code model: ${it.name}")
                codeModel = LlamaModel(
                    modelPath = it.absolutePath
                )
            }

            Log.d(TAG, "Models initialized successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Model initialization failed", e)
        }
    }

    /**
     * Generate response using appropriate model
     */
    fun generate(prompt: String): String {
        val model = selectModel(prompt)

        if (model == null) {
            Log.e(TAG, "No model available for generation")
            return "Model not loaded."
        }

        return try {
            model.generate(prompt)
        } catch (e: Exception) {
            Log.e(TAG, "Generation failed", e)
            "Error generating response."
        }
    }

    /**
     * Select chat or code model automatically
     */
    private fun selectModel(prompt: String): LlamaModel? {
        val lower = prompt.lowercase()

        val looksLikeCode = lower.contains("class ")
                || lower.contains("function ")
                || lower.contains("def ")
                || lower.contains("import ")
                || lower.contains("val ")
                || lower.contains("var ")
                || lower.contains("public ")
                || lower.contains("private ")
                || lower.contains("fun ")

        return if (looksLikeCode && codeModel != null) {
            codeModel
        } else {
            chatModel ?: codeModel
        }
    }

    /**
     * Check if at least one model is loaded
     */
    fun isReady(): Boolean {
        return chatModel != null || codeModel != null
    }

    /**
     * Release resources
     */
    fun release() {
        try {
            chatModel?.close()
            codeModel?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing models", e)
        }
    }
}
