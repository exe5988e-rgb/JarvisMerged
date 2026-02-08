package com.jarvismini.core

object Logger {
    fun i(tag: String, msg: String?) {
        android.util.Log.i(tag, msg ?: "")
    }
}
