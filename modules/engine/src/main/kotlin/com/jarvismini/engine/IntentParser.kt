package com.jarvismini.engine

object IntentParser {

    fun parse(input: String): Intent {
        val time = TimeParser.parse(input) ?: return Intent.Unknown

        return when {
            input.contains("alarm", true) ->
                Intent.SetAlarm(time)

            input.contains("timer", true) ->
                Intent.SetTimer(time - System.currentTimeMillis())

            input.contains("remind", true) ->
                Intent.AddReminder(input, time)

            input.contains("study", true) ->
                Intent.ScheduleStudy("Study Session", time)

            else -> Intent.Unknown
        }
    }
}
