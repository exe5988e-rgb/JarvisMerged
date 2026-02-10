package com.jarvis.ai.engine

enum class ModelType {
    CHAT, CODE
}

data class ModelConfig(
    val type: ModelType,
    val name: String,
    val filename: String,
    val contextSize: Int = 2048,
    val threads: Int = 4
)
