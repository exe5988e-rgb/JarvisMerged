package com.jarvismini.agent

import android.util.Log
import com.jarvismini.core.JarvisPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object AgentRepository {

    private const val TAG          = "AgentRepo"
    private const val DEFAULT_HOST = "192.168.29.48"
    private const val AGENT_PORT   = 8891
    private const val TTS_PORT     = 8892

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun agentUrl(path: String): String {
        val host = JarvisPrefs.getString("agent_host") ?: DEFAULT_HOST
        return "http://$host:$AGENT_PORT$path"
    }

    private fun ttsUrl(path: String): String {
        val host = JarvisPrefs.getString("agent_host") ?: DEFAULT_HOST
        return "http://$host:$TTS_PORT$path"
    }

    suspend fun runTask(
        task: String,
        device: String,
        maxSteps: Int = 20
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("task", task)
                put("device", device)
                put("max_steps", maxSteps)
            }.toString().toRequestBody("application/json".toMediaType())

            val req = Request.Builder()
                .url(agentUrl("/run"))
                .post(body)
                .build()

            client.newCall(req).execute().use { resp ->
                val json = JSONObject(resp.body?.string() ?: "{}")
                if (resp.isSuccessful) {
                    Result.success(json.optString("task", task))
                } else {
                    Result.failure(Exception(json.optString("error", "start failed")))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "runTask failed", e)
            Result.failure(e)
        }
    }

    suspend fun stopAgent(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(agentUrl("/stop"))
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().close()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Session 17: pause / resume ────────────────────────────────────────────

    suspend fun pauseAgent(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(agentUrl("/pause"))
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().close()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "pauseAgent failed", e)
            Result.failure(e)
        }
    }

    suspend fun resumeAgent(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(agentUrl("/resume"))
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().close()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "resumeAgent failed", e)
            Result.failure(e)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    suspend fun getStatus(): AgentStatus = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(agentUrl("/status")).get().build()
            client.newCall(req).execute().use { resp ->
                val j = JSONObject(resp.body?.string() ?: "{}")
                AgentStatus(
                    running = j.optBoolean("running"),
                    task    = j.optString("task"),
                    step    = j.optInt("step"),
                    done    = j.optBoolean("done"),
                    error   = j.optString("error").ifBlank { null },
                    paused  = j.optBoolean("paused"),
                    lastLog = j.optString("last_log"),
                )
            }
        } catch (e: Exception) {
            AgentStatus(error = e.message)
        }
    }

    suspend fun fetchLogTail(n: Int = 50): List<String> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(agentUrl("/logs/tail?n=$n"))
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                val j   = JSONObject(resp.body?.string() ?: "{}")
                val arr = j.optJSONArray("lines") ?: return@use emptyList()
                List(arr.length()) { arr.getString(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchLogTail failed", e)
            emptyList()
        }
    }

    fun streamLogs(): Flow<String> = flow {
        try {
            val req = Request.Builder()
                .url(agentUrl("/logs"))
                .addHeader("Accept", "text/event-stream")
                .get()
                .build()

            val sseClient = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .build()

            sseClient.newCall(req).execute().use { resp ->
                val source = resp.body?.source() ?: return@use
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.startsWith("data: ")) {
                        val payload = line.removePrefix("data: ").trim()
                        if (payload.isNotBlank()) emit(payload)
                    }
                }
            }
        } catch (e: Exception) {
            emit("[connection lost: ${e.message}]")
        }
    }.flowOn(Dispatchers.IO)

    suspend fun speak(text: String, voiceId: String? = null): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("text", text)
                    if (voiceId != null) put("voice_id", voiceId)
                }.toString().toRequestBody("application/json".toMediaType())

                val req = Request.Builder()
                    .url(ttsUrl("/speak"))
                    .post(body)
                    .build()

                client.newCall(req).execute().close()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun isAgentServerReachable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(agentUrl("/health"))
                .get()
                .build()
            val quickClient = OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(3, TimeUnit.SECONDS)
                .build()
            quickClient.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }
}

data class AgentStatus(
    val running: Boolean = false,
    val task:    String  = "",
    val step:    Int     = 0,
    val done:    Boolean = false,
    val error:   String? = null,
    val paused:  Boolean = false,
    val lastLog: String  = "",
)
