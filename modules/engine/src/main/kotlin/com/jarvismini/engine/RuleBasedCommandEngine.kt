package com.jarvismini.engine

class RuleBasedCommandEngine : CommandEngine {

    override fun canHandle(input: String): Boolean = true

    override fun handle(input: String): EngineResult {
        ContextStore.history.add(input)

        val intent = IntentParser.parse(input)

        return when (intent) {
            is Intent.SetAlarm ->
                EngineResult.Success("Alarm set successfully.")

            is Intent.SetTimer ->
                EngineResult.Success("Timer started.")

            is Intent.AddReminder ->
                EngineResult.Success("Reminder added.")

            is Intent.ScheduleStudy ->
                EngineResult.Success("Study session scheduled.")

            Intent.Unknown ->
                EngineResult.Unhandled
        }
    }
}
