package com.jarvismini.convo

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class ConvoException(message: String) : Exception(message)

interface ConvoRepository {
    suspend fun agents(): Result<List<ConvoAgent>>

    /** afterId: only newer; beforeId: latest page older than it; both null: oldest page. */
    suspend fun messages(afterId: Long? = null, beforeId: Long? = null, limit: Int = 100): Result<MessagePage>

    /** recipients == null means all agents. */
    suspend fun broadcast(text: String, recipients: List<String>?): Result<Unit>
}

class HttpConvoRepository(
    private val baseUrl: () -> String,
    private val token: () -> String,
    private val client: OkHttpClient = defaultClient,
) : ConvoRepository {

    override suspend fun agents(): Result<List<ConvoAgent>> =
        execute("/v1/admin/agents", emptyMap(), null).mapCatching { ConvoJson.parseAgents(it) }

    override suspend fun messages(afterId: Long?, beforeId: Long?, limit: Int): Result<MessagePage> {
        val q = linkedMapOf("conversation_id" to "main", "limit" to limit.toString())
        if (afterId != null) q["after_id"] = afterId.toString()
        if (beforeId != null) q["before_id"] = beforeId.toString()
        return execute("/v1/admin/messages", q, null).mapCatching { ConvoJson.parsePage(it) }
    }

    override suspend fun broadcast(text: String, recipients: List<String>?): Result<Unit> =
        execute("/v1/admin/broadcast", emptyMap(), ConvoJson.broadcastBody(text, recipients)).map { }

    private suspend fun execute(path: String, query: Map<String, String>, body: String?): Result<String> =
        withContext<Result<String>>(Dispatchers.IO) {
            val tok = token().trim()
            if (tok.isEmpty()) {
                return@withContext Result.failure(ConvoException("Set the admin token in Convo settings"))
            }
            try {
                val base = normalizeBaseUrl(baseUrl())
                val url = (base + path).toHttpUrlOrNull()
                    ?: return@withContext Result.failure(ConvoException("Invalid server URL"))
                val ub = url.newBuilder()
                query.forEach { (k, v) -> ub.addQueryParameter(k, v) }
                val rb = Request.Builder().url(ub.build()).header("Authorization", "Bearer $tok")
                if (body != null) {
                    rb.post(body.toRequestBody("application/json".toMediaType()))
                } else {
                    rb.get()
                }
                client.newCall(rb.build()).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    when {
                        resp.isSuccessful -> Result.success(text)
                        resp.code == 401 -> Result.failure(ConvoException("Token rejected (401)"))
                        resp.code == 403 -> Result.failure(ConvoException("Admin token required (403)"))
                        else -> Result.failure(ConvoException("Server error (HTTP ${resp.code})"))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Result.failure(ConvoException("Cannot reach Convo server"))
            } catch (e: Exception) {
                Result.failure(ConvoException("Request failed: ${e.javaClass.simpleName}"))
            }
        }

    companion object {
        val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build()
        }
    }
}

/** In-memory repository for unit tests and previews; mimics the server's paging and broadcast rules. */
class FakeConvoRepository(
    var agentList: List<ConvoAgent> = emptyList(),
    initial: List<ConvoMessage> = emptyList(),
) : ConvoRepository {

    val stored = initial.toMutableList()
    val sent = mutableListOf<Pair<String, List<String>?>>()
    var failing: Throwable? = null
    var messageCalls = 0
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1L

    override suspend fun agents(): Result<List<ConvoAgent>> =
        failing?.let { Result.failure(it) } ?: Result.success(agentList)

    override suspend fun messages(afterId: Long?, beforeId: Long?, limit: Int): Result<MessagePage> {
        messageCalls++
        failing?.let { return Result.failure(it) }
        val sorted = stored.sortedBy { it.id }
        val page = when {
            beforeId != null -> sorted.filter { it.id < beforeId }.takeLast(limit)
            afterId != null -> sorted.filter { it.id > afterId }.take(limit)
            else -> sorted.take(limit)
        }
        return Result.success(MessagePage(page, page.lastOrNull()?.id ?: afterId, page.firstOrNull()?.id))
    }

    override suspend fun broadcast(text: String, recipients: List<String>?): Result<Unit> {
        failing?.let { return Result.failure(it) }
        sent.add(text to recipients)
        val targets = recipients ?: agentList.messageable().map { it.id }
        val bid = "bc-${nextId}"
        for (r in targets) {
            stored.add(
                ConvoMessage(
                    id = nextId++, uuid = "u-$nextId", senderId = ADMIN_ID, recipientId = r,
                    type = "chat", text = text, broadcastId = bid, createdAt = nextId * 1000L, readAt = null,
                ),
            )
        }
        return Result.success(Unit)
    }

    fun markRead(id: Long, at: Long) {
        val i = stored.indexOfFirst { it.id == id }
        if (i >= 0) stored[i] = stored[i].copy(readAt = at)
    }
}
