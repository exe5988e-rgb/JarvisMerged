package com.jarvismini.smart

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * RateLimiter: a simple global rate limiter for total replies over time.
 *
 * Useful to limit Jarvis across all chats (e.g., max 10 replies per minute).
 */
class RateLimiter(private val windowMs: Long, private val maxEvents: Int) {

    private val events = ConcurrentHashMap<Long, AtomicInteger>()

    fun recordEvent(): Boolean {
        val now = System.currentTimeMillis()
        val bucket = now / windowMs
        events.putIfAbsent(bucket, AtomicInteger(0))
        val count = events[bucket]!!.incrementAndGet()
        // cleanup old buckets (simple)
        val keysToRemove = events.keys.filter { it < bucket - 2 }
        for (k in keysToRemove) events.remove(k)
        return count <= maxEvents
    }

    fun reset() {
        events.clear()
    }
}
