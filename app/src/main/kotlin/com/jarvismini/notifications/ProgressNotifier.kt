package com.jarvismini.notifications

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.jarvismini.R

object ProgressNotifier {

    fun showCompletionPrompt(context: Context, id: String, name: String) {
        notify(context, id, "Block Finished", "Mark $name complete or incomplete.")
    }

    fun showRetryPrompt(context: Context, id: String, name: String) {
        notify(context, id, "Reminder", "$name still incomplete.")
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
