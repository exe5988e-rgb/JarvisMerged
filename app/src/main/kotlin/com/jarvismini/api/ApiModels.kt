package com.jarvismini.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val json = Json { 
    ignoreUnknownKeys = true
    prettyPrint = false
    encodeDefaults = true
}

@Serializable
data class ApiRequest(
    val endpoint: String,
    val source: RequestSource,
    val body: Map<String, String>,
    val auth: AuthContext? = null
)

@Serializable
enum class RequestSource {
    LOCAL_LLM,
    TERMUX_LLM,
    CLOUD_API,
    LAN_CLIENT
}

@Serializable
data class AuthContext(
    val deviceToken: String?,
    val ipAddress: String,
    val timestamp: Long
)

@Serializable
data class ApiResponse(
    val success: Boolean,
    val data: Map<String, String>? = null,
    val error: String? = null,
    val statusCode: Int
) {
    fun toJson(): String = json.encodeToString(this)
}

// Request/Response models for LAN API
@Serializable
data class PairRequest(
    val device_name: String
)

@Serializable
data class PairResponse(
    val success: Boolean,
    val device_id: String? = null,
    val token: String? = null,
    val error: String? = null
)

@Serializable
data class UnpairRequest(
    val device_id: String
)

@Serializable
data class ExecuteRequest(
    val command: String
)

@Serializable
data class ExecuteResponse(
    val success: Boolean,
    val output: String? = null,
    val exit_code: Int? = null,
    val execution_time: Long? = null,
    val error: String? = null
)

@Serializable
data class GenerateRequest(
    val query: String
)

@Serializable
data class GenerateResponse(
    val success: Boolean,
    val command: String? = null,
    val response: String? = null,
    val error: String? = null
)

@Serializable
data class ChatRequest(
    val query: String? = null,
    val messages: List<ChatMessage>? = null
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class ChatResponse(
    val success: Boolean,
    val response: String? = null,
    val error: String? = null
)

@Serializable
data class DeviceInfo(
    val id: String,
    val name: String,
    val ip_address: String,
    val paired_at: Long,
    val last_seen: Long
)

@Serializable
data class DevicesResponse(
    val success: Boolean,
    val devices: List<DeviceInfo>? = null,
    val error: String? = null
)

@Serializable
data class HealthResponse(
    val status: String,
    val backends: List<String>,
    val executor: String
)

// Cloud API models
@Serializable
data class OpenAIRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val max_tokens: Int,
    val temperature: Double = 0.7
)

@Serializable
data class OpenAIResponse(
    val choices: List<OpenAIChoice>
)

@Serializable
data class OpenAIChoice(
    val message: ChatMessage
)

@Serializable
data class AnthropicRequest(
    val model: String,
    val max_tokens: Int,
    val messages: List<ChatMessage>
)

@Serializable
data class AnthropicResponse(
    val content: List<AnthropicContent>
)

@Serializable
data class AnthropicContent(
    val type: String,
    val text: String
)
