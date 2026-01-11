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
            val body = response.body?.string()
                ?: return "Empty response from server"

            // 🔴 THIS WAS THE BUG
            val json = JSONObject(body)
            return json.optString("response", "No response field")
        }
    }
}
