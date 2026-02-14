package com.jarvismini.llm

import android.content.Context
import android.util.Log
import com.jarvismini.api.*
import com.jarvismini.executor.UnifiedExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

interface LlmBackend {
    suspend fun generate(prompt: String): String
    suspend fun chat(messages: List<ChatMessage>): String
}

class TermuxLlmBackend(
    private val context: Context,
    private val executor: UnifiedExecutor
) : LlmBackend {
    
    private val tag = "TermuxLlmBackend"
    
    override suspend fun generate(prompt: String): String = withContext(Dispatchers.IO) {
        try {
            val result = executor.execute(
                com.jarvismini.executor.ExecutionRequest(
                    command = "echo 'termux-llama generate: $prompt'",
                    source = RequestSource.TERMUX_LLM,
                    timeout = 30000L,
                    maxOutputSize = 10000
                )
            )
            
            if (result.success) {
                result.output
            } else {
                "Error: ${result.output}"
            }
            
        } catch (e: Exception) {
            Log.e(tag, "Generate failed", e)
            "Error: ${e.message}"
        }
    }
    
    override suspend fun chat(messages: List<ChatMessage>): String = withContext(Dispatchers.IO) {
        try {
            val lastMessage = messages.lastOrNull()?.content ?: return@withContext "No message"
            
            val result = executor.execute(
                com.jarvismini.executor.ExecutionRequest(
                    command = "echo 'termux-llama chat: $lastMessage'",
                    source = RequestSource.TERMUX_LLM,
                    timeout = 30000L,
                    maxOutputSize = 10000
                )
            )
            
            if (result.success) {
                result.output
            } else {
                "Error: ${result.output}"
            }
            
        } catch (e: Exception) {
            Log.e(tag, "Chat failed", e)
            "Error: ${e.message}"
        }
    }
}

class CloudApiBackend(
    private val context: Context,
    private val executor: UnifiedExecutor
) : LlmBackend {
    
    private val tag = "CloudApiBackend"
    private val prefs = context.getSharedPreferences("jarvis_llm", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    
    private fun getApiKey(): String = prefs.getString("cloud_api_key", "") ?: ""
    private fun getProvider(): String = prefs.getString("cloud_provider", "OPENAI") ?: "OPENAI"
    private fun getSelectedModel(): String = prefs.getString("selected_model", "gpt-4o-mini") ?: "gpt-4o-mini"
    
    override suspend fun generate(prompt: String): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isEmpty()) {
            return@withContext "Cloud API not configured. Add API key in settings."
        }
        
        try {
            when (getProvider()) {
                "OPENAI" -> generateOpenAI(prompt, apiKey)
                "ANTHROPIC" -> generateAnthropic(prompt, apiKey)
                else -> "Unknown provider"
            }
        } catch (e: Exception) {
            Log.e(tag, "Generate failed", e)
            "Error: ${e.message}"
        }
    }
    
    override suspend fun chat(messages: List<ChatMessage>): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isEmpty()) {
            return@withContext "Cloud API not configured"
        }
        
        try {
            when (getProvider()) {
                "OPENAI" -> chatOpenAI(messages, apiKey)
                "ANTHROPIC" -> chatAnthropic(messages, apiKey)
                else -> "Unknown provider"
            }
        } catch (e: Exception) {
            Log.e(tag, "Chat failed", e)
            "Error: ${e.message}"
        }
    }
    
    private fun generateOpenAI(prompt: String, apiKey: String): String {
        val model = getSelectedModel()
        val requestObj = OpenAIRequest(
            model = if (model.startsWith("gpt")) model else "gpt-4o-mini",
            messages = listOf(ChatMessage("user", prompt)),
            max_tokens = 150
        )
        
        val requestBody = json.encodeToString(OpenAIRequest.serializer(), requestObj)
        
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()
        
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("HTTP ${response.code}: ${response.body?.string()}")
        }
        
        val body = response.body?.string() ?: throw Exception("No response")
        val openAIResponse = json.decodeFromString<OpenAIResponse>(body)
        
        return openAIResponse.choices.firstOrNull()?.message?.content ?: "No result"
    }
    
    private fun chatOpenAI(messages: List<ChatMessage>, apiKey: String): String {
        val model = getSelectedModel()
        val requestObj = OpenAIRequest(
            model = if (model.startsWith("gpt")) model else "gpt-4o-mini",
            messages = messages,
            max_tokens = 500
        )
        
        val requestBody = json.encodeToString(OpenAIRequest.serializer(), requestObj)
        
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()
        
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("HTTP ${response.code}: ${response.body?.string()}")
        }
        
        val body = response.body?.string() ?: throw Exception("No response")
        val openAIResponse = json.decodeFromString<OpenAIResponse>(body)
        
        return openAIResponse.choices.firstOrNull()?.message?.content ?: "No result"
    }
    
    private fun generateAnthropic(prompt: String, apiKey: String): String {
        val model = getSelectedModel()
        val requestObj = AnthropicRequest(
            model = if (model.startsWith("claude")) model else "claude-sonnet-4-20250514",
            max_tokens = 150,
            messages = listOf(ChatMessage("user", prompt))
        )
        
        val requestBody = json.encodeToString(AnthropicRequest.serializer(), requestObj)
        
        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()
        
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("HTTP ${response.code}: ${response.body?.string()}")
        }
        
        val body = response.body?.string() ?: throw Exception("No response")
        val anthropicResponse = json.decodeFromString<AnthropicResponse>(body)
        
        return anthropicResponse.content.firstOrNull()?.text ?: "No result"
    }
    
    private fun chatAnthropic(messages: List<ChatMessage>, apiKey: String): String {
        val model = getSelectedModel()
        val requestObj = AnthropicRequest(
            model = if (model.startsWith("claude")) model else "claude-sonnet-4-20250514",
            max_tokens = 1024,
            messages = messages
        )
        
        val requestBody = json.encodeToString(AnthropicRequest.serializer(), requestObj)
        
        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()
        
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("HTTP ${response.code}: ${response.body?.string()}")
        }
        
        val body = response.body?.string() ?: throw Exception("No response")
        val anthropicResponse = json.decodeFromString<AnthropicResponse>(body)
        
        return anthropicResponse.content.firstOrNull()?.text ?: "No result"
    }
}

object LlmBackendFactory {
    fun getBackend(source: RequestSource, context: Context, executor: UnifiedExecutor): LlmBackend {
        return when (source) {
            RequestSource.LOCAL_LLM, RequestSource.TERMUX_LLM, RequestSource.LAN_CLIENT -> 
                TermuxLlmBackend(context, executor)
            RequestSource.CLOUD_API -> 
                CloudApiBackend(context, executor)
        }
    }
}
