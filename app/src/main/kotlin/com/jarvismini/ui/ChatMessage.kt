package com.jarvismini.ui

import java.util.concurrent.atomic.AtomicLong

private val chatMessageIds = AtomicLong(0)

/**
 * [id] is a stable list key (LazyColumn keeps scroll anchoring and skips re-composing old bubbles);
 * [createdAtNanos] lets a bubble play its enter animation only when it is actually new.
 * Both have defaults, so existing `ChatMessage(text, isUser = ..)` call sites are unchanged.
 */
data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val id: Long = chatMessageIds.incrementAndGet(),
    val createdAtNanos: Long = System.nanoTime()
)
