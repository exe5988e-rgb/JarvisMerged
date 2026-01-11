package com.jarvismini.api

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

object JarvisApiClient {

    private val client = OkHttpClient()

    suspend fun getResponse(prompt: String): String {
        val encodedPrompt = URLEncoder.encode(prompt, "UTF-8")

        val request = Request.Builder()
            .url("http://127.0.0.1:8080/response?prompt=$encodedPrompt")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}")
            }

            val body = response.body?.string()
                ?: throw Exception("Empty response")

            // Server returns JSON → extract safely
            return try {
                JSONObject(body).optString("response", body)
            } catch (_: Exception) {
                body // fallback if server ever returns plain text
            }
        }
    }
}
