package com.jarvismini.api

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlinx.coroutines.delay

object JarvisApiClient {

    private val client = OkHttpClient()
    private const val SERVER_BASE = "http://192.168.29.48:8080"

    suspend fun submitQueryAndWait(prompt: String, timeoutMs: Long = 10000): String {
        // ---- Submit query ----
        val jsonBody = JSONObject().put("prompt", prompt).toString()
        val request = Request.Builder()
            .url("$SERVER_BASE/query")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return "HTTP ${response.code}"
        }

        // ---- Poll /response until updated ----
        val startTime = System.currentTimeMillis()
        var lastResponse = ""
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val respRequest = Request.Builder()
                .url("$SERVER_BASE/response")
                .get()
                .build()

            client.newCall(respRequest).execute().use { resp ->
                if (!resp.isSuccessful) return "HTTP ${resp.code}"
                val body = resp.body?.string() ?: return "Empty response"
                val json = JSONObject(body)
                val latest = json.optString("response", "No response field")

                // Check if server updated response for this prompt
                if (latest != "Jarvis server running." && latest != lastResponse) {
                    return latest
                }
                lastResponse = latest
            }

            delay(500) // wait 500ms before retry
        }

        return "No response (timeout)"
    }
}
