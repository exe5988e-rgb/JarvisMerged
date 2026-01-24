package com.jarvismini.engine.scheduler

import android.os.Handler
import android.os.Looper

class DelayedTaskScheduler {

    private val handler = Handler(Looper.getMainLooper())

    fun schedule(delayMs: Long, task: () -> Unit) {
        handler.postDelayed(task, delayMs)
    }
}
