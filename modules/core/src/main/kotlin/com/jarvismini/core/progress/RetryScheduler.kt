package com.jarvismini.core.progress

import android.content.Context
import com.jarvismini.core.calendar.AppCalendarStore
import com.jarvismini.core.calendar.model.CalendarEvent
import com.jarvismini.core.calendar.model.EventStatus
import com.jarvismini.core.time.NetworkTimeProvider
import java.util.UUID

object RetryScheduler {

    fun schedule(
        context: Context,
        blockId: String,
        blockName: String,
        config: ProgressConfig
    ) {
        val retryAt = NetworkTimeProvider.nowMs() + config.retryDelayMs

        AppCalendarStore.save(
            context,
            CalendarEvent(
                id = "retry_${UUID.randomUUID()}",
                title = "Retry: $blockName",
                startTimeMs = retryAt,
                endTimeMs = retryAt + 5 * 60_000, // 5 min reminder
                status = EventStatus.RETRY_SCHEDULED,
                meta = mapOf("blockId" to blockId)
            )
        )
    }
}
