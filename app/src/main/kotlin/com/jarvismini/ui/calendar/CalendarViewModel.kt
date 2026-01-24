package com.jarvismini.ui.calendar

import android.content.Context
import androidx.lifecycle.ViewModel
import com.jarvismini.core.calendar.AppCalendarStore
import com.jarvismini.core.calendar.model.CalendarEvent

class CalendarViewModel(
    private val context: Context
) : ViewModel() {

    fun getEventsForDay(dayStartMs: Long, dayEndMs: Long): List<CalendarEvent> {
        return AppCalendarStore.getAll(context)
            .filter { it.startTimeMs in dayStartMs..dayEndMs }
            .sortedBy { it.startTimeMs }
    }
}
