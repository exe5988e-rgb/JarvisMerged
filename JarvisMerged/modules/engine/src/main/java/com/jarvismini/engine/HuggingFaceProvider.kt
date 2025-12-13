package com.jarvismini.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HuggingFaceProvider : LLMProvider {
    override suspend fun generateReply(prompt: String): String = withContext(Dispatchers.IO) {
        "Processing... (HF fallback active)"
    }
}