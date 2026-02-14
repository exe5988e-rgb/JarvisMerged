package com.jarvismini.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.jarvismini.api.ApiGateway
import com.jarvismini.executor.FileBasedExecutor
import com.jarvismini.security.SecurityManager
import fi.iki.elonen.NanoHTTPD   // ✅ ADD THIS IMPORT

class LanServerService : Service() {

    private val tag = "LanServerService"
    private var lanServer: LanServer? = null

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "jarvis_lan_server"

        fun start(context: Context) {
            val intent = Intent(context, LanServerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LanServerService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(tag, "Service created")

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        val executor = FileBasedExecutor(this)
        val securityManager = SecurityManager(this)
        val apiGateway = ApiGateway(this, executor, securityManager)

        lanServer = LanServer(apiGateway, port = 8080)

        try {
            lanServer?.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            Log.i(tag, "LAN Server started on port 8080")
        } catch (e: Exception) {
            Log.e(tag, "Failed to start LAN server", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(tag, "Service destroyed")
        lanServer?.stop()
        lanServer = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "JARVIS LAN Server",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Allows LAN devices to access JARVIS"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("JARVIS LAN Server")
            .setContentText("Listening on port 8080")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }
}
