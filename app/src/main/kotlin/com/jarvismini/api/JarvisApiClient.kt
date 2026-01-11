package com.jarvismini.api

import okhttp3.OkHttpClient
import okhttp3.Request

object JarvisApiClient {

    private val client = OkHttpClient()

    suspend fun getResponse(prompt: String): String {
        val request = Request.Builder()
            .url("http://192.168.29.48:8080/response")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}")
            }
            return response.body?.string() ?: "Empty response"
        }
    }
}
