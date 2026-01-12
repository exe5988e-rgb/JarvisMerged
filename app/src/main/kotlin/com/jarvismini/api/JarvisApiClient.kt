package com.jarvismini.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object JarvisApiClient {

    private val client = OkHttpClient()
    private const val SERVER_BASE = "http://192.168.29.48:8080"

    suspend fun submitQueryAndWait(
        prompt: String,
        timeoutMs: Long = 10000
    ): String = withContext(Dispatchers.IO) {

        try {
            // ---- Submit query ----
            val jsonBody = JSONObject()
                .put("prompt", prompt)
                .toString()

            val request = Request.Builder()
                .url("$SERVER_BASE/query")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext "HTTP ${response.code}"
                }
            }

            // ---- Poll /response ----
            val startTime = System.currentTimeMillis()
            var lastResponse = ""

            while (System.currentTimeMillis() - startTime < timeoutMs) {
                val respRequest = Request.Builder()
                    .url("$SERVER_BASE/response")
                    .get()
                    .build()

                client.newCall(respRequest).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        return@withContext "HTTP ${resp.code}"
                    }

                    val body = resp.body?.string()
                        ?: return@withContext "Empty response"

                    val latest = JSONObject(body)
                        .optString("response", "No response field")

                    if (
                        latest != "Jarvis server running." &&
                        latest != lastResponse
                    ) {
                        return@withContext latest
                    }

                    lastResponse = latest
                }

                delay(500)
            }

            return@withContext "No response (timeout)"

        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext "EXCEPTION: ${e::class.java.simpleName}"
        }
    }
}
