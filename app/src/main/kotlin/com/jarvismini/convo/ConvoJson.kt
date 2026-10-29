package com.jarvismini.convo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Pure JSON <-> model mapping for the Convo admin API (no Android classes, JVM-testable). */
object ConvoJson {

    private fun JsonElement?.prim(): JsonPrimitive? =
        (this as? JsonPrimitive)?.takeIf { it !is JsonNull }

    private fun JsonElement?.str(): String? = prim()?.contentOrNull
    private fun JsonElement?.lng(): Long? = prim()?.longOrNull
    private fun JsonElement?.bool(): Boolean? = prim()?.booleanOrNull

    fun parseAgents(body: String): List<ConvoAgent> {
        val arr = Json.parseToJsonElement(body).jsonObject["agents"]?.jsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val id = o["agent_id"].str() ?: return@mapNotNull null
            ConvoAgent(
                id = id,
                displayName = o["display_name"].str() ?: id,
                role = o["role"].str() ?: "agent",
                lastSeen = o["last_seen"].lng(),
                online = o["online"].bool() ?: false,
            )
        }
    }

    fun parsePage(body: String): MessagePage {
        val root = Json.parseToJsonElement(body).jsonObject
        val arr = root["messages"]?.jsonArray
        val messages = arr?.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val id = o["id"].lng() ?: return@mapNotNull null
            val payload = o["payload"] as? JsonObject
            ConvoMessage(
                id = id,
                uuid = o["message_uuid"].str() ?: "",
                senderId = o["sender_id"].str() ?: "?",
                recipientId = o["recipient_id"].str() ?: "?",
                type = o["message_type"].str() ?: "chat",
                text = payload?.get("text").str() ?: payload?.toString() ?: "",
                broadcastId = payload?.get("broadcast_id").str(),
                createdAt = o["created_at"].lng() ?: 0L,
                readAt = o["read_at"].lng(),
            )
        } ?: emptyList()
        return MessagePage(
            messages = messages,
            nextCursor = root["next_cursor"].lng(),
            firstId = root["first_id"].lng(),
        )
    }

    /** recipients == null means "all". */
    fun broadcastBody(text: String, recipients: List<String>?, conversationId: String = "main"): String =
        buildJsonObject {
            put("text", text)
            put("conversation_id", conversationId)
            if (recipients == null) {
                put("recipients", "all")
            } else {
                put("recipients", buildJsonArray { recipients.forEach { add(it) } })
            }
        }.toString()
}
