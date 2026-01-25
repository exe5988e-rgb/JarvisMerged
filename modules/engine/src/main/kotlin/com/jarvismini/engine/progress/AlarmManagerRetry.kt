package com.jarvismini.engine.progress

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.jarvismini.core.notifications.ProgressReminderReceiver

/**
 * Schedules retry reminders for missed tasks.
 */
object AlarmManagerRetry {

    fun schedule(
        context: Context,
        blockId: String,
        blockName: String,
        delayMinutes: Int
    ) {
        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, ProgressReminderReceiver::class.java).apply {
            putExtra("blockId", blockId)
            putExtra("blockName", blockName)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            blockId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAt =
            System.currentTimeMillis() + delayMinutes * 60 * 1000L

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            pendingIntent
        )
    }

    fun cancel(context: Context, blockId: String) {
        val intent = Intent(context, ProgressReminderReceiver::class.java)

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            blockId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        alarmManager.cancel(pendingIntent)
    }
}
