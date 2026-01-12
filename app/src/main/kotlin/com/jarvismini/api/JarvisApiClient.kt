package com.jarvismini.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

object JarvisApiClient {

    private val client = OkHttpClient()

    suspend fun getResponse(prompt: String): String =
        withContext(Dispatchers.IO) {

            val request = Request.Builder()
                .url("http://192.168.29.48:8080/response")
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext "HTTP ${response.code}"
                    }

                    val body = response.body?.string()
                        ?: return@withContext "Empty response"

                    val json = JSONObject(body)
                    return@withContext json.optString(
                        "response",
                        "No response field"
                    )
                }
            } catch (e: IOException) {
                return@withContext "Network error"
            }
        }
}
