package com.jarvismini.core.progress

import android.content.Context
import com.jarvismini.core.calendar.model.CalendarEvent
import com.jarvismini.core.calendar.model.EventStatus
import com.jarvismini.core.calendar.AppCalendarStore
import com.jarvismini.core.time.NetworkTimeProvider
import java.util.UUID

object ProgressEngine {

    fun markComplete(context: Context, blockId: String) {
        ProgressStore.markComplete(context, blockId)
    }

    fun markIncomplete(context: Context, blockId: String, blockName: String) {
        ProgressStore.markIncomplete(context, blockId)

        val config = ProgressConfigLoader.load(context)
        if (config.retryEnabled) {
            RetryScheduler.schedule(context, blockId, blockName, config)
        }
    }
}
