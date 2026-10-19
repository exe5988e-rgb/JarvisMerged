package com.jarvismini.automation

import android.content.Context
import com.jarvismini.core.AssistantVoice
import com.jarvismini.core.AutoReplyProfile
import com.jarvismini.core.JarvisMode
import com.jarvismini.core.JarvisState
import com.jarvismini.llm.OpenRouterClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Writes the reply for the few contacts allowed to "chat" while the owner is unavailable.
 * Speaks as the owner, only from the facts below (mode, status note, time), and carries the
 * automated tag. Returns null on any failure so the caller falls back to the canned reply.
 */
object AiChatReplier {

    private const val MAX_TURNS = 6
    private val history = ConcurrentHashMap<String, MutableList<Pair<String, String>>>() // chat -> (who, text)

    private val SYSTEM = """
        You are writing a WhatsApp reply on behalf of Aamir, who cannot reply himself right now.
        Write in first person as Aamir: casual, warm, 1-2 short sentences, plain text, no markdown.
        Use only the facts in CONTEXT (what he is doing, status note, time). Never invent plans,
        promises, places, money or other details. If asked something you cannot answer from CONTEXT,
        say you will get back to them once you are free. Never share passwords, OTPs, financial or
        private information. Do not mention Jarvis or being an AI assistant.
    """.trimIndent()

    private fun modeText(m: JarvisMode) = when (m) {
        JarvisMode.DRIVING -> "driving"
        JarvisMode.WORK -> "busy working"
        JarvisMode.SLEEP -> "sleeping"
        JarvisMode.FOCUS -> "in focus mode, studying or doing deep work"
        JarvisMode.NORMAL -> "around but not replying right now"
    }

    suspend fun reply(context: Context, chatId: String, sender: String, message: String): String? {
        val client = OpenRouterClient(context.applicationContext)
        if (!client.isConfigured() || message.isBlank()) return null

        val turns = history.getOrPut(chatId) { mutableListOf() }
        val convo = synchronized(turns) { turns.takeLast(MAX_TURNS) }
            .joinToString("\n") { (who, text) -> "$who: $text" }
        val status = AutoReplyProfile.status().ifBlank { "none" }
        val now = SimpleDateFormat("EEE h:mm a", Locale.ENGLISH).format(Date())

        val user = buildString {
            append("CONTEXT\n")
            append("Aamir is currently: ${modeText(JarvisState.currentMode)}\n")
            append("Status note from Aamir: $status\n")
            append("Local time: $now\n\n")
            if (convo.isNotEmpty()) append("CONVERSATION SO FAR\n$convo\n\n")
            append("NEW MESSAGE from $sender: $message\n\nWrite Aamir's reply.")
        }

        val out = client.complete(SYSTEM, user).getOrNull()?.trim()?.trim('"') ?: return null
        if (out.isBlank()) return null
        val reply = out.take(400)

        synchronized(turns) {
            turns.add(sender to message)
            turns.add("Aamir" to reply)
            while (turns.size > MAX_TURNS * 2) turns.removeAt(0)
        }
        return AssistantVoice.withTag(reply)
    }
}
