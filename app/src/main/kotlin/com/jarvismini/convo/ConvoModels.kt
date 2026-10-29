package com.jarvismini.convo

const val DEFAULT_CONVO_BASE_URL = "http://127.0.0.1:8900"
const val ADMIN_ID = "sir"

data class ConvoAgent(
    val id: String,
    val displayName: String,
    val role: String,
    val lastSeen: Long?,
    val online: Boolean,
)

data class ConvoMessage(
    val id: Long,
    val uuid: String,
    val senderId: String,
    val recipientId: String,
    val type: String,
    val text: String,
    val broadcastId: String?,
    val createdAt: Long,
    val readAt: Long?,
)

data class MessagePage(
    val messages: List<ConvoMessage>,
    val nextCursor: Long?,
    val firstId: Long?,
)

data class Receipt(val agentId: String, val readAt: Long?) {
    val isRead: Boolean get() = readAt != null
}

enum class ReceiptState { Delivered, Read }

/** One timeline bubble. A to-all broadcast is a single bubble with one receipt per recipient. */
data class ConvoBubble(
    val key: String,
    val sortId: Long,
    val senderId: String,
    val text: String,
    val createdAt: Long,
    val receipts: List<Receipt>,
    val isBroadcast: Boolean,
) {
    val readBy: List<String> get() = receipts.filter { it.isRead }.map { it.agentId }
    val unreadBy: List<String> get() = receipts.filter { !it.isRead }.map { it.agentId }
    val hasUnread: Boolean get() = receipts.any { !it.isRead }

    /** One check = delivered, two checks = read (by every recipient). */
    val state: ReceiptState
        get() = if (receipts.isNotEmpty() && receipts.all { it.isRead }) ReceiptState.Read else ReceiptState.Delivered
}

enum class ConvoFilter(val label: String) {
    All("All"),
    Unread("Unread by anyone"),
    FromAgents("From agents"),
    FromMe("From me"),
}

/** Groups raw per-recipient rows into bubbles (by payload.broadcast_id), oldest first. */
fun groupMessages(messages: Collection<ConvoMessage>): List<ConvoBubble> {
    val groups = LinkedHashMap<String, MutableList<ConvoMessage>>()
    for (m in messages.sortedBy { it.id }) {
        val bid = m.broadcastId
        val key = if (!bid.isNullOrBlank()) "b:$bid" else "m:${m.id}"
        groups.getOrPut(key) { mutableListOf() }.add(m)
    }
    return groups.map { (key, group) ->
        val first = group.first()
        ConvoBubble(
            key = key,
            sortId = first.id,
            senderId = first.senderId,
            text = first.text,
            createdAt = group.minOf { it.createdAt },
            receipts = group
                .map { Receipt(it.recipientId, it.readAt) }
                .distinctBy { it.agentId }
                .sortedBy { it.agentId },
            isBroadcast = key.startsWith("b:"),
        )
    }.sortedBy { it.sortId }
}

fun List<ConvoBubble>.filtered(filter: ConvoFilter): List<ConvoBubble> = when (filter) {
    ConvoFilter.All -> this
    ConvoFilter.Unread -> filter { it.hasUnread }
    ConvoFilter.FromAgents -> filter { it.senderId != ADMIN_ID }
    ConvoFilter.FromMe -> filter { it.senderId == ADMIN_ID }
}

/** Agents the admin can message (everything except the admin identity itself). */
fun List<ConvoAgent>.messageable(): List<ConvoAgent> =
    filter { it.role != "admin" && it.id != ADMIN_ID }

fun formatLastSeen(lastSeen: Long?, now: Long): String {
    if (lastSeen == null) return "never seen"
    val secs = ((now - lastSeen) / 1000L).coerceAtLeast(0L)
    return when {
        secs < 60L -> "just now"
        secs < 3600L -> "${secs / 60L}m ago"
        secs < 86_400L -> "${secs / 3600L}h ago"
        else -> "${secs / 86_400L}d ago"
    }
}

fun recipientLabel(b: ConvoBubble): String = when {
    b.receipts.isEmpty() -> ""
    b.receipts.size == 1 -> b.receipts[0].agentId
    b.receipts.size <= 3 -> b.receipts.joinToString(", ") { it.agentId }
    else -> "${b.receipts.size} agents"
}

fun normalizeBaseUrl(raw: String): String {
    val t = raw.trim().trimEnd('/')
    return if (t.isEmpty()) DEFAULT_CONVO_BASE_URL else t
}
