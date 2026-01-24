package com.jarvismini.core.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object ProgressNotifier {

    /**
     * Engine-safe API
     * App MUST provide the icon resource
     */
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
        blockName: String,
        iconRes: Int
    ) {
        notify(
            context = context,
            id = blockId,
            title = "Reminder",
            msg = "$blockName still incomplete.",
            iconRes = iconRes
        )
    }

    @SuppressLint("MissingPermission")
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

        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context)
                .notify(id.hashCode(), notification)
        } else {
            Log.w("ProgressNotifier", "Notification permission not granted")
        }
    }
}
