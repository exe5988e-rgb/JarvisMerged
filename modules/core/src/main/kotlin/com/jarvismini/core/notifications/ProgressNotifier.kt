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
     * Engine-safe call.
     * No icon required — engine must not know about Android resources.
     */
    fun showCompletionPrompt(
        context: Context,
        blockId: String,
        blockName: String
    ) {
        showCompletionPrompt(
            context = context,
            blockId = blockId,
            blockName = blockName,
            iconRes = null
        )
    }

    /**
     * App-layer call.
     * Icon is optional to avoid resource dependency crashes.
     */
    fun showCompletionPrompt(
        context: Context,
        blockId: String,
        blockName: String,
        iconRes: Int?
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
        iconRes: Int? = null
    ) {
        notify(
            context = context,
            id = blockId,
            title = "Reminder",
            msg = "$blockName still incomplete.",
            iconRes = iconRes
        )
    }

    /**
     * Lint-safe notification dispatcher
     */
    @SuppressLint("MissingPermission")
    private fun notify(
        context: Context,
        id: String,
        title: String,
        msg: String,
        iconRes: Int?
    ) {
        val builder = NotificationCompat.Builder(context, "progress")
            .setContentTitle(title)
            .setContentText(msg)
            .setAutoCancel(true)

        // Only set icon if provided (prevents drawable/mipmap crashes)
        if (iconRes != null) {
            builder.setSmallIcon(iconRes)
        }

        val notification = builder.build()

        // Android 13+ runtime permission check
        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat
                .from(context)
                .notify(id.hashCode(), notification)
        } else {
            Log.w(
                "ProgressNotifier",
                "Notification permission not granted, skipping notify()"
            )
        }
    }
}
