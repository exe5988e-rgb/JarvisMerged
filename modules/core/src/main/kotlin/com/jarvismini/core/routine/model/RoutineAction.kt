package com.jarvismini.core.routine.model

/**
 * Represents an action inside a routine.
 */
data class RoutineAction(
    val type: String,
    val params: Map<String, String> = emptyMap()
)
