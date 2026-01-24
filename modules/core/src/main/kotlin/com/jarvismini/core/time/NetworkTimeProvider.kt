package com.jarvismini.core.time

object NetworkTimeProvider {

    fun nowMs(): Long {
        // 🔒 Design locked: single authority
        // Later swap with NTP without touching callers
        return System.currentTimeMillis()
    }
}
