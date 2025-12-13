package com.jarvismini.smart

/**
 * SleepMode: a simple global "do not disturb" toggle.
 * You can later replace this with a user-set schedule or UI toggle.
 */
object SleepMode {
    @Volatile
    var enabled: Boolean = false

    /** Convenience: enable for N milliseconds (auto-disable). */
    fun enableFor(millis: Long) {
        enabled = true
        Thread {
            try {
                Thread.sleep(millis)
            } catch (_: InterruptedException) { }
            enabled = false
        }.start()
    }
}
