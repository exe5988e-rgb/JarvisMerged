package com.jarvismini.core

object Logger {

    fun i(tag: String, msg: String?) {
        android.util.Log.i(tag, msg ?: "")
    }

    // 🔧 Added methods (non-breaking)
    fun d(tag: String, msg: String?) {
        android.util.Log.d(tag, msg ?: "")
    }

    fun w(tag: String, msg: String?) {
        android.util.Log.w(tag, msg ?: "")
    }

    fun e(tag: String, msg: String?, tr: Throwable? = null) {
        if (tr != null) {
            android.util.Log.e(tag, msg ?: "", tr)
        } else {
            android.util.Log.e(tag, msg ?: "")
        }
    }
}
