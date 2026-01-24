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

        val event = CalendarEvent(
            id = "retry_${blockId}_${UUID.randomUUID()}",
            title = "Retry: $blockName",
            startTimeMs = retryAt,
            endTimeMs = retryAt + 5 * 60_000,
            status = EventStatus.RETRY,
            meta = mapOf("blockId" to blockId)
        )

        AppCalendarStore.save(context, event)
    }

    fun restorePendingRetries(context: Context) {
        val now = NetworkTimeProvider.nowMs()

        AppCalendarStore.getAll(context)
            .filter { it.status == EventStatus.RETRY && it.startTimeMs <= now }
            .forEach {
                ProgressNotifier.showRetryPrompt(
                    context,
                    it.meta["blockId"] ?: it.id,
                    it.title
                )
            }
    }
}
