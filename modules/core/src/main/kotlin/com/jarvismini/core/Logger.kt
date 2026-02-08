//===== FILE: modules/core/src/main/kotlin/com/jarvismini/core/Logger.kt =====
package com.jarvismini.core

import android.util.Log

object Logger {

    fun d(tag: String, msg: String?) {
        Log.d(tag, msg ?: "")
    }

    fun i(tag: String, msg: String?) {
        Log.i(tag, msg ?: "")
    }

    fun e(tag: String, msg: String?, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(tag, msg ?: "", throwable)
        } else {
            Log.e(tag, msg ?: "")
        }
    }
}
