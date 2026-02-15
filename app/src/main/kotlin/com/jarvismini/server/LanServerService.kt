package com.jarvismini.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.jarvismini.MainActivity
import com.jarvismini.R
import com.jarvismini.api.ApiGateway
import com.jarvismini.executor.FileBasedExecutor
import com.jarvismini.security.SecurityManager
import fi.iki.elonen.NanoHTTPD

class LanServerService : Service() {

    private val tag = "LanServerService"
    private var lanServer: LanServer? = null

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "jarvis_lan_server"

        fun start(context: Context) {
            val intent = Intent(context, LanServerService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                Log.i("LanServerService", "Service start requested")
            } catch (e: Exception) {
                Log.e("LanServerService", "Failed to start service", e)
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, LanServerService::class.java))
                Log.i("LanServerService", "Service stop requested")
            } catch (e: Exception) {
                Log.e("LanServerService", "Failed to stop service", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(tag, "Service onCreate()")

        try {
            // Create notification channel first
            createNotificationChannel()
            
            // Start as foreground service IMMEDIATELY
            val notification = createNotification()
            startForeground(NOTIFICATION_ID, notification)
            Log.i(tag, "Started as foreground service")

            // Now initialize the server
            val executor = FileBasedExecutor(this)
            val securityManager = SecurityManager(this)
            val apiGateway = ApiGateway(this, executor, securityManager)

            lanServer = LanServer(apiGateway, port = 8080)

            try {
                lanServer?.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                Log.i(tag, "✅ LAN Server started successfully on port 8080")
                
                // Update notification to show success
                val successNotification = createNotification("Running on port 8080")
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager?.notify(NOTIFICATION_ID, successNotification)
                
            } catch (e: Exception) {
                Log.e(tag, "❌ Failed to start LAN server", e)
                
                // Update notification to show error
                val errorNotification = createNotification("Failed to start: ${e.message}")
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager?.notify(NOTIFICATION_ID, errorNotification)
            }
            
        } catch (e: Exception) {
            Log.e(tag, "❌ Service onCreate failed", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(tag, "Service onStartCommand()")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(tag, "Service onDestroy()")
        
        try {
            lanServer?.stop()
            lanServer = null
            Log.i(tag, "✅ LAN Server stopped")
        } catch (e: Exception) {
            Log.e(tag, "Error stopping server", e)
        }
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
                enableLights(false)
                enableVibration(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
            Log.d(tag, "Notification channel created")
        }
    }

    private fun createNotification(contentText: String = "Starting..."): Notification {
        // Create intent to open app when notification is tapped
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("JARVIS LAN Server")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }
}
