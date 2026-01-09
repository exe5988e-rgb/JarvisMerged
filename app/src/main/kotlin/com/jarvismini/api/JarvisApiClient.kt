package com.jarvismini.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object JarvisApiClient {

    private const val BASE_URL = "http://127.0.0.1:8080"

    suspend fun getResponse(): String = withContext(Dispatchers.IO) {
        val url = URL("$BASE_URL/response")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"

        try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    suspend fun sendQuery(text: String): String = withContext(Dispatchers.IO) {
        val url = URL("$BASE_URL/query")
        val conn = url.openConnection() as HttpURLConnection

        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")

        val body = """{ "text": "$text" }"""
        conn.outputStream.use { it.write(body.toByteArray()) }

        try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
