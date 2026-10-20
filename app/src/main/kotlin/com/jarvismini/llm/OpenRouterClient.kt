package com.jarvismini.llm

import android.content.Context
import android.util.Log
import com.jarvismini.core.JarvisPersona
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * On-device OpenRouter chat. Keys are NOT in the repo: they live in the app's
 * private SharedPreferences ("jarvis_llm"), provisioned over adb:
 *   openrouter_keys   = keys separated by newlines (rotated on 401/402/429)
 *   openrouter_models = models separated by newlines (tried in order), optional
 */
class OpenRouterClient(context: Context) {

    private val tag = "OpenRouterClient"
    private val prefs = context.applicationContext
        .getSharedPreferences("jarvis_llm", Context.MODE_PRIVATE)

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val history = mutableListOf<Pair<String, String>>() // role, content

    private fun keys(): List<String> =
        (prefs.getString("openrouter_keys", "") ?: "")
            .lines().map { it.trim() }.filter { it.isNotEmpty() }

    private fun models(): List<String> {
        val saved = (prefs.getString("openrouter_models", "") ?: "")
            .lines().map { it.trim() }.filter { it.isNotEmpty() }
        return saved.ifEmpty { DEFAULT_MODELS }
    }

    fun isConfigured(): Boolean = keys().isNotEmpty()

    private fun systemPrompt(): String {
        val style = when (JarvisPersona.current()) {
            JarvisPersona.SILENT_GUARDIAN -> "Be extremely brief. One short sentence."
            JarvisPersona.COMBAT -> "Be terse and tactical, like a status report."
            JarvisPersona.HUMOR -> "Be witty and playful, with dry humor, but still useful."
            JarvisPersona.STUDY -> "Be focused and clear, like a patient tutor. No small talk."
            JarvisPersona.CALM_WHISPER -> "Be soft, calm and very short."
            JarvisPersona.JARVIS -> "Speak like J.A.R.V.I.S. from the Iron Man films: warm, composed, dryly witty, a touch sarcastic, never robotic. Be talkative: usually three to five sentences. Add a relevant observation, a small quip, or offer a sensible next step. Keep it natural to hear aloud."
        }
        return "You are J.A.R.V.I.S, a personal assistant running on the user's Android phone. " +
            "Address the user as \"Sir\". Your replies are spoken aloud, so use plain sentences: " +
            "no markdown, no lists, no emojis. " + (if (JarvisPersona.current() == JarvisPersona.JARVIS || JarvisPersona.current() == JarvisPersona.HUMOR) "" else "At most 3 short sentences unless asked for more. ") +
            "If asked to do something on the phone that you cannot do from chat, say so briefly. " +
            style
    }

    suspend fun chat(userText: String): Result<String> = withContext(Dispatchers.IO) {
        val ks = keys()
        if (ks.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("No OpenRouter key configured on this device"))
        }
        val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", systemPrompt()))
        history.takeLast(8).forEach { (r, c) -> msgs.put(JSONObject().put("role", r).put("content", c)) }
        msgs.put(JSONObject().put("role", "user").put("content", userText))

        var lastErr = "unknown error"
        for (model in models()) {
            for (key in ks) {
                try {
                    val body = JSONObject()
                        .put("model", model)
                        .put("messages", msgs)
                        .put("max_tokens", 1500) // reasoning models spend tokens before answering
                        .put("temperature", 0.6)
                        .toString().toRequestBody("application/json".toMediaType())
                    val req = Request.Builder()
                        .url("https://openrouter.ai/api/v1/chat/completions")
                        .header("Authorization", "Bearer $key")
                        .header("X-Title", "Jarvis")
                        .post(body).build()
                    http.newCall(req).execute().use { resp ->
                        val text = resp.body?.string().orEmpty()
                        if (resp.isSuccessful) {
                            val content = JSONObject(text).optJSONArray("choices")
                                ?.optJSONObject(0)?.optJSONObject("message")
                                ?.optString("content", "")?.trim().orEmpty()
                            if (content.isNotEmpty() && content != "null") {
                                history.add("user" to userText)
                                history.add("assistant" to content)
                                return@withContext Result.success(content)
                            }
                            lastErr = "$model returned an empty reply"
                        } else {
                            lastErr = "$model HTTP ${resp.code}"
                            Log.w(tag, lastErr)
                            // 401/402/429: try next key; other errors: next model
                            if (resp.code !in listOf(401, 402, 429)) return@use
                        }
                    }
                } catch (e: Exception) {
                    lastErr = "$model ${e.javaClass.simpleName}: ${e.message}"
                    Log.w(tag, lastErr)
                    break // network problem: no point rotating keys
                }
            }
        }
        Result.failure(RuntimeException(lastErr))
    }

    /** One-shot completion with its own system prompt and no chat history (used for proactive lines). */
    suspend fun complete(system: String, user: String): Result<String> = withContext(Dispatchers.IO) {
        val ks = keys()
        if (ks.isEmpty()) return@withContext Result.failure(IllegalStateException("No OpenRouter key configured"))
        val msgs = JSONArray()
            .put(JSONObject().put("role", "system").put("content", system))
            .put(JSONObject().put("role", "user").put("content", user))
        var lastErr = "unknown error"
        for (model in models()) {
            for (key in ks) {
                try {
                    val body = JSONObject().put("model", model).put("messages", msgs)
                        .put("max_tokens", 1200).put("temperature", 0.8)
                        .toString().toRequestBody("application/json".toMediaType())
                    val req = Request.Builder()
                        .url("https://openrouter.ai/api/v1/chat/completions")
                        .header("Authorization", "Bearer $key").header("X-Title", "Jarvis")
                        .post(body).build()
                    http.newCall(req).execute().use { resp ->
                        val text = resp.body?.string().orEmpty()
                        if (resp.isSuccessful) {
                            val content = JSONObject(text).optJSONArray("choices")
                                ?.optJSONObject(0)?.optJSONObject("message")
                                ?.optString("content", "")?.trim().orEmpty()
                            if (content.isNotEmpty() && content != "null") return@withContext Result.success(content)
                            lastErr = "$model returned an empty reply"
                        } else {
                            lastErr = "$model HTTP ${resp.code}"
                            if (resp.code !in listOf(401, 402, 429)) return@use
                        }
                    }
                } catch (e: Exception) {
                    lastErr = "$model ${e.javaClass.simpleName}: ${e.message}"
                    break
                }
            }
        }
        Result.failure(RuntimeException(lastErr))
    }

    companion object {
        private val DEFAULT_MODELS = listOf("deepseek/deepseek-v4.1-flash-1017")
    }
}
