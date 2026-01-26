package com.jarvismini.core.routine.model

data class Routine(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val trigger: Trigger? = null,
    val actions: List<RoutineAction>
)
