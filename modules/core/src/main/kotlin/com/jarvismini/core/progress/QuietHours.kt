package com.jarvismini.core.progress

import java.time.LocalTime

object QuietHours {

    fun isQuietNow(config: ProgressConfig): Boolean {
        val now = LocalTime.now()
        val start = LocalTime.parse(config.quietStart)
        val end = LocalTime.parse(config.quietEnd)

        return if (start.isBefore(end)) {
            now.isAfter(start) && now.isBefore(end)
        } else {
            now.isAfter(start) || now.isBefore(end)
        }
    }
}
