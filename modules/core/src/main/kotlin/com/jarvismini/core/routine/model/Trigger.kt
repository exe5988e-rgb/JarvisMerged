package com.jarvismini.core.routine.model

data class Trigger(
    val type: String,       // e.g., "time"
    val time: String,       // e.g., "08:00"
    val days: List<String>  // e.g., ["MON", "TUE"]
)
