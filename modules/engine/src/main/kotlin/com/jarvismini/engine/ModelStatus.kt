package com.jarvismini.engine

/**
 * Data class representing the status of loaded AI models
 */
data class ModelStatus(
    val isLoading: Boolean = false,
    val chatReady: Boolean = false,
    val codeReady: Boolean = false,
    val errorMessage: String? = null
)
