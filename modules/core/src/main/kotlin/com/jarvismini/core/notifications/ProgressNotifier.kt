package com.jarvismini.core.notifications

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object ProgressNotifier {

    fun showCompletionPrompt(
        context: Context,
        blockId: String,
        blockName: String,
        iconRes: Int
    ) {
        notify(
            context,
            blockId,
            "Block Finished",
            "Mark $blockName as complete or incomplete.",
            iconRes
        )
    }

    fun showRetryPrompt(
        context: Context,
        blockId: String,
        blockName: String,
        iconRes: Int
    ) {
        notify(
            context,
            blockId,
            "Reminder",
            "$blockName still incomplete.",
            iconRes
        )
    }

    private fun notify(
        context: Context,
        id: String,
        title: String,
        msg: String,
        iconRes: Int
    ) {
        val notification = NotificationCompat.Builder(context, "progress")
            .setSmallIcon(iconRes)
            .setContentTitle(title)
            .setContentText(msg)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context)
            .notify(id.hashCode(), notification)
    }
}
