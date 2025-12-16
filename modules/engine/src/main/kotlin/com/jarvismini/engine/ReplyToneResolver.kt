package com.jarvismini.engine

import com.jarvismini.core.JarvisMode

object ReplyToneResolver {

    fun resolve(reason: ReplyReason, mode: JarvisMode): String {
        return when (mode) {

            JarvisMode.WORK ->
                "This is Jarvis, assisting Mr. Aamir. He is currently in a meeting."

            JarvisMode.DRIVING ->
                "This is Jarvis, assisting Mr. Aamir. He is currently driving."

            JarvisMode.SLEEP ->
                "This is Jarvis, assisting Mr. Aamir. He is currently unavailable."

            JarvisMode.FOCUS ->
                "This is Jarvis, assisting Mr. Aamir. He is currently focused and unavailable."

            JarvisMode.NORMAL ->
                defaultByReason(reason)
        }
    }

    private fun defaultByReason(reason: ReplyReason): String {
        return when (reason) {
            ReplyReason.USER_BUSY ->
                "This is Jarvis, assisting Mr. Aamir. He is currently occupied and will respond when available."

            ReplyReason.MISSED_MESSAGE ->
                "This is Jarvis, assisting Mr. Aamir. He will review this message shortly."

            ReplyReason.IMPORTANT_MESSAGE ->
                "This is Jarvis, assisting Mr. Aamir. He has been notified and will respond as soon as possible."
        }
    }
}
