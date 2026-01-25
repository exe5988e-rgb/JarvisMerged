package com.jarvismini.core.progress

import android.content.Context

/**
 * Holds application context for background / engine access.
 * Must be initialized in Application.onCreate().
 */
object AppContextProvider {
    lateinit var appContext: Context
}
