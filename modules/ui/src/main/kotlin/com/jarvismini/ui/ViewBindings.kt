package com.jarvismini.ui

import android.app.Activity
import android.view.View

// Simple helper extension to keep your UI clean
fun <T : View> Activity.bind(id: Int): Lazy<T> = lazy { findViewById(id) }
