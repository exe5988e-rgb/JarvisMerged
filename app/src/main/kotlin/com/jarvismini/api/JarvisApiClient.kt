package com.jarvismini.api

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object JarvisApiClient {

    private val client = OkHttpClient()

    suspend fun getResponse(prompt: String): String {
        val request = Request.Builder()
            .url("http://192.168.29.48:8080/response")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return "HTTP ${response.code}"
            }

            val body = response.body?.string()
                ?: return "Empty response"

            // ✅ THIS is the missing piece
            val json = JSONObject(body)
            return json.optString("response", "No response field")
        }
    }
}
