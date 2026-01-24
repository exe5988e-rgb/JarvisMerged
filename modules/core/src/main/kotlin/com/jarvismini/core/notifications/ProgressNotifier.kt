package com.jarvismini.core.notifications

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object ProgressNotifier {

    fun showCompletionPrompt(
        context: Context,
        blockId: String,
        blockName: String
    ) {
        showCompletionPrompt(
            context = context,
            blockId = blockId,
            blockName = blockName,
            iconRes = android.R.drawable.ic_dialog_info
        )
    }

    fun showCompletionPrompt(
        context: Context,
        blockId: String,
        blockName: String,
        iconRes: Int
    ) {
        notify(
            context = context,
            id = blockId,
            title = "Block Finished",
            msg = "Mark $blockName as complete or incomplete.",
            iconRes = iconRes
        )
    }

    fun showRetryPrompt(
        context: Context,
        blockId: String,
        blockName: String
    ) {
        notify(
            context = context,
            id = blockId,
            title = "Reminder",
            msg = "$blockName still incomplete.",
            iconRes = android.R.drawable.ic_dialog_info
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
