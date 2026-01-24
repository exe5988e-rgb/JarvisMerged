package com.jarvismini.core.calendar

import android.content.Context
import com.jarvismini.core.calendar.model.CalendarEvent
import com.jarvismini.core.util.JsonUtil

object AppCalendarStore {

    private const val PREF = "app_calendar"

    fun save(context: Context, event: CalendarEvent) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(event.id, JsonUtil.toJson(event))
            .apply()
    }

    fun getAll(context: Context): List<CalendarEvent> {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return prefs.all.values.mapNotNull {
            runCatching {
                JsonUtil.fromJson<CalendarEvent>(it as String)
            }.getOrNull()
        }
    }

    fun updateStatus(context: Context, id: String, status: String) {
        val event = getAll(context).find { it.id == id } ?: return
        save(context, event.copy(status = enumValueOf(status)))
    }

    fun delete(context: Context, id: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .remove(id)
            .apply()
    }
}
