package com.jarvismini.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class PuterProvider(private val model: String) : LLMProvider {

    private val client = OkHttpClient()

    override suspend fun generateReply(prompt: String): String =
        withContext(Dispatchers.IO) {

            val url = "https://api.puter.com/v1/chat/completions"

            try {
                val json = JSONObject().apply {
                    put("model", model)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", "You are Jarvis, reply short and helpful.")
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        })
                    })
                }

                val body = json.toString().toRequestBody("application/json".toMediaType())

                val req = Request.Builder()
                    .url(url)
                    .post(body)
                    .build()

                val resp = client.newCall(req).execute()
                val raw = resp.body?.string() ?: return@withContext fallback()

                val root = JSONObject(raw)
                val choices = root.optJSONArray("choices") ?: return@withContext fallback()
                val msgObj = choices.optJSONObject(0)?.optJSONObject("message")
                val content = msgObj?.optString("content")

                return@withContext content ?: fallback()

            } catch (e: Exception) {
                return@withContext fallback()
            }
        }

    private fun fallback() = "I'm here! How can I help?"
}