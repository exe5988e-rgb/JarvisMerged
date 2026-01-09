package com.jarvismini.api

import okhttp3.OkHttpClient
import okhttp3.Request

object JarvisApiClient {

    private val client = OkHttpClient()

    suspend fun getResponse(prompt: String): String {
        val request = Request.Builder()
            .url("http://127.0.0.1:8080/response?prompt=$prompt")
            .build()

        return client.newCall(request).execute().body!!.string()
    }
}
