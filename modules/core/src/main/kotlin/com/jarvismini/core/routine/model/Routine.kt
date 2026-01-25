package com.jarvismini.core.routine.model

data class Routine(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val actions: List<RoutineAction>
)
