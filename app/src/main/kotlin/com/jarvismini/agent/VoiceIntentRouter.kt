package com.jarvismini.agent

import android.util.Log
import com.jarvismini.core.JarvisPrefs
import com.jarvismini.engine.LlamaLLMEngine
import org.json.JSONObject

/**
 * VoiceIntentRouter — the fusion point between Jarvis's voice/chat surface
 * and Agent J's task-execution pipeline.
 *
 * Given a piece of user text (typed or transcribed from voice), decides
 * whether it's ordinary conversation (routed to the local chat LLM) or a
 * real action to hand off to Agent J via AgentRepository.runTask().
 *
 * Classification is two-tier:
 *   1. Fast keyword pass (no network/model call, near-instant)
 *   2. LLM fallback for ambiguous phrasing, using the same on-device
 *      LlamaLLMEngine already used for chat — no new dependency.
 */
sealed class VoiceIntent {
    data class Chat(val text: String) : VoiceIntent()
    data class Task(val task: String, val device: String) : VoiceIntent()
}

object VoiceIntentRouter {

    private const val TAG = "VoiceIntentRouter"

    // Anything starting with these is almost certainly a real action, not chat.
    private val ACTION_VERBS = listOf(
        "open ", "apply ", "send ", "fill ", "block ", "close ", "install ",
        "uninstall ", "message ", "reply to ", "navigate ", "click ", "tap ",
        "scroll ", "search for ", "book ", "order ", "call ", "text ",
        "go to ", "launch ", "start ", "stop ", "enable ", "disable ",
        "toggle ", "turn on ", "turn off ", "screenshot ", "automate "
    )

    // Clearly conversational — skip the task path entirely.
    private val CHAT_MARKERS = listOf(
        "what is", "what's", "who is", "who's", "why ", "how do", "how does",
        "explain", "tell me about", "what do you think", "how are you",
        "thanks", "thank you", "hi ", "hello", "hey jarvis"
    )

    /**
     * Device target for Agent J tasks. Reads from JarvisPrefs so it's
     * configurable from Settings instead of hardcoded — Phone A's ADB port
     * has changed before and will again.
     */
    private fun defaultDevice(): String =
        JarvisPrefs.getString("agent_device") ?: "192.168.29.48:40657"

    /** Zero-cost keyword classification. Returns null if ambiguous. */
    fun classifyFast(input: String): VoiceIntent? {
        val lower = input.trim().lowercase()
        if (lower.isBlank()) return null

        if (CHAT_MARKERS.any { lower.startsWith(it) || lower.contains(" $it") }) {
            return VoiceIntent.Chat(input)
        }
        if (ACTION_VERBS.any { lower.startsWith(it) }) {
            return VoiceIntent.Task(task = input.trim(), device = defaultDevice())
        }
        return null
    }

    /**
     * Full classification: fast path first, LLM fallback for anything
     * ambiguous. Never throws — falls back to Chat on any failure so a
     * broken classification never silently drops a message.
     */
    suspend fun classify(input: String): VoiceIntent {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return VoiceIntent.Chat(trimmed)

        classifyFast(trimmed)?.let { return it }

        val prompt = """
            Classify the following user message as either "chat" (a question or
            conversation) or "task" (an instruction to perform a real action on
            the phone, such as opening an app, filling a form, sending a message,
            or automating a multi-step process).

            Respond with ONLY raw JSON, no markdown, no explanation:
            {"intent": "chat" or "task", "task": "<cleaned up task instruction, empty string if chat>"}

            Message: "$trimmed"
        """.trimIndent()

        return try {
            val raw = LlamaLLMEngine.generateReply(prompt)
            val json = JSONObject(extractJson(raw))
            val intent = json.optString("intent", "chat")
            if (intent == "task") {
                val task = json.optString("task", trimmed).ifBlank { trimmed }
                VoiceIntent.Task(task = task, device = defaultDevice())
            } else {
                VoiceIntent.Chat(trimmed)
            }
        } catch (e: Exception) {
            Log.w(TAG, "LLM classification failed, defaulting to chat", e)
            VoiceIntent.Chat(trimmed)
        }
    }

    /** Strips markdown fences etc. in case the model doesn't obey "raw JSON only". */
    private fun extractJson(raw: String): String {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        return if (start != -1 && end != -1 && end > start) raw.substring(start, end + 1) else "{}"
    }

    /**
     * Full pipeline: classify the input, then either hand it back as chat or
     * dispatch it to Agent J and speak a spoken acknowledgement/result.
     *
     * This is the single entry point callers (ChatActivity, a future wake-word
     * service, etc.) should call — it owns the chat-vs-task decision so no
     * caller has to duplicate classification logic.
     */
    suspend fun routeAndExecute(
        input: String,
        speakOnCompletion: Boolean = true,
        onChat: suspend (String) -> Unit,
        onTaskStarted: suspend (String) -> Unit = {},
        onTaskResult: suspend (String) -> Unit = {},
        onTaskFailed: suspend (String) -> Unit = {}
    ) {
        when (val intent = classify(input)) {
            is VoiceIntent.Chat -> onChat(intent.text)
            is VoiceIntent.Task -> {
                onTaskStarted(intent.task)
                AgentRepository.runTask(intent.task, intent.device)
                    .onSuccess { started ->
                        onTaskResult(started)
                        if (speakOnCompletion) {
                            AgentRepository.speak("Got it. Starting: ${intent.task}")
                        }
                    }
                    .onFailure { e ->
                        onTaskFailed(e.message ?: "Task failed to start")
                        if (speakOnCompletion) {
                            AgentRepository.speak("Sorry, I couldn't start that task.")
                        }
                    }
            }
        }
    }
}
