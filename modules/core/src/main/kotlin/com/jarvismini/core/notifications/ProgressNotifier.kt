package com.jarvismini.core.notifications

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.jarvismini.R

object ProgressNotifier {

    fun showCompletionPrompt(context: Context, blockId: String, blockName: String) {
        notify(context, blockId, "Block Finished", "Mark ${blockName} as complete or incomplete.")
    }

    fun showRetryPrompt(context: Context, blockId: String, blockName: String) {
        notify(context, blockId, "Reminder", "${blockName} still incomplete.")
    }

    private fun notify(context: Context, id: String, title: String, msg: String) {
        val n = NotificationCompat.Builder(context, "progress")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(msg)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context)
            .notify(id.hashCode(), n)
    }
}
