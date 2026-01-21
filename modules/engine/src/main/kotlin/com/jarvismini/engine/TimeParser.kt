package com.jarvismini.engine

import com.jarvismini.core.TimeUtils
import java.util.concurrent.TimeUnit

object TimeParser {

    fun parse(input: String): Long? {
        val now = TimeUtils.nowMs()

        Regex("(\\d+)\\s*min").find(input)?.let {
            return now + TimeUnit.MINUTES.toMillis(it.groupValues[1].toLong())
        }

        Regex("(\\d+)\\s*hour").find(input)?.let {
            return now + TimeUnit.HOURS.toMillis(it.groupValues[1].toLong())
        }

        Regex("(\\d{1,2})\\s*(am|pm)", RegexOption.IGNORE_CASE)
            .find(input)?.let {
                val hour =
                    (it.groupValues[1].toInt() % 12) +
                            if (it.groupValues[2].lowercase() == "pm") 12 else 0

                val cal = java.util.Calendar.getInstance()
                cal.set(java.util.Calendar.HOUR_OF_DAY, hour)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                return cal.timeInMillis
            }

        return null
    }
}
