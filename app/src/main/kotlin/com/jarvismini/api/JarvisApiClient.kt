package com.jarvismini.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object JarvisApiClient {

    private val client = OkHttpClient()
    private const val BASE_URL = "http://192.168.29.48:8080"

    private val JSON = "application/json".toMediaType()

    // -------------------------------
    // POST /query  (fire-and-forget)
    // -------------------------------
    suspend fun sendQuery(prompt: String) = withContext(Dispatchers.IO) {
        val bodyJson = JSONObject()
            .put("prompt", prompt)
            .toString()

        val request = Request.Builder()
            .url("$BASE_URL/query")
            .post(bodyJson.toRequestBody(JSON))
            .build()

        client.newCall(request).execute().close()
    }

    // -------------------------------
    // GET /response
    // -------------------------------
    suspend fun getResponse(): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/response")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return@withContext "HTTP ${response.code}"
            }

            val body = response.body?.string() ?: return@withContext "Empty response"
            val json = JSONObject(body)
            json.optString("response", "No response field")
        }
    }
}
