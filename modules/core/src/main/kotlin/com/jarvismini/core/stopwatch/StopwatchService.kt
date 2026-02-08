package com.jarvismini.core.stopwatch

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

/**
 * FIXED StopwatchService - notification now persists when paused
 * 
 * Key changes:
 * - Notification stays visible when paused (shows "Paused" state)
 * - Service only stops when reset, not when paused
 * - Notification updates to show paused time
 */
class StopwatchService : Service() {

    companion object {
        private const val CHANNEL_ID = "stopwatch_channel"
        private const val NOTIF_ID = 1001
        private const val TAG = "StopwatchService"

        fun start(context: Context) {
            val intent = Intent(context, StopwatchService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to start service: ${e.message}")
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, StopwatchService::class.java)
            context.stopService(intent)
        }
        
        fun updatePausedState(context: Context) {
            // Send broadcast to update notification to paused state
            val intent = Intent(context, StopwatchService::class.java)
            intent.action = "UPDATE_PAUSED"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var updateJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        android.util.Log.d(TAG, "Service onCreate called")
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        android.util.Log.d(TAG, "Service onStartCommand called with action: ${intent?.action}")
        
        try {
            // Get current elapsed time for initial notification
            val elapsed = StopwatchManager.getCurrentElapsed()
            val timeString = StopwatchManager.formatElapsedTimeShort(elapsed)
            val isRunning = StopwatchManager.state.value.isRunning
            
            // Start foreground IMMEDIATELY
            val notification = buildNotification(timeString, isRunning)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIF_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIF_ID, notification)
            }
            
            android.util.Log.d(TAG, "Service started in foreground successfully")
            
            // Start observing stopwatch state
            startObservingStopwatch()
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to start foreground: ${e.message}", e)
            stopSelf()
        }
        
        return START_STICKY
    }

    private fun startObservingStopwatch() {
        updateJob?.cancel()
        
        updateJob = scope.launch {
            try {
                StopwatchManager.state.collectLatest { state ->
                    android.util.Log.d(TAG, "State changed: isRunning=${state.isRunning}")
                    
                    if (state.isRunning) {
                        // Update notification every 500ms while running
                        while (state.isRunning && isActive) {
                            val elapsed = StopwatchManager.getCurrentElapsed()
                            val timeString = StopwatchManager.formatElapsedTimeShort(elapsed)
                            
                            updateNotification(timeString, true)
                            delay(500)
                            
                            if (!StopwatchManager.state.value.isRunning) {
                                break
                            }
                        }
                        
                        // When stopwatch pauses, show paused notification
                        if (!state.isRunning) {
                            val elapsed = StopwatchManager.getCurrentElapsed()
                            val timeString = StopwatchManager.formatElapsedTimeShort(elapsed)
                            updateNotification(timeString, false)
                        }
                    } else {
                        // Stopwatch paused - keep notification visible with paused state
                        val elapsed = StopwatchManager.getCurrentElapsed()
                        val timeString = StopwatchManager.formatElapsedTimeShort(elapsed)
                        updateNotification(timeString, false)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error in update loop: ${e.message}", e)
            }
        }
    }

    private fun updateNotification(time: String, isRunning: Boolean) {
        try {
            val notification = buildNotification(time, isRunning)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.notify(NOTIF_ID, notification)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to update notification: ${e.message}")
        }
    }

    override fun onDestroy() {
        android.util.Log.d(TAG, "Service onDestroy called")
        updateJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(time: String, isRunning: Boolean): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        val title = if (isRunning) "Stopwatch Running" else "Stopwatch Paused"
        val icon = if (isRunning) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Elapsed: $time")
            .setSmallIcon(icon)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            
            if (manager != null) {
                val existingChannel = manager.getNotificationChannel(CHANNEL_ID)
                
                if (existingChannel == null) {
                    android.util.Log.d(TAG, "Creating notification channel")
                    
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        "Stopwatch",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "Shows stopwatch timer while running"
                        setShowBadge(false)
                        enableLights(false)
                        enableVibration(false)
                    }
                    
                    manager.createNotificationChannel(channel)
                    android.util.Log.d(TAG, "Notification channel created successfully")
                } else {
                    android.util.Log.d(TAG, "Notification channel already exists")
                }
            } else {
                android.util.Log.e(TAG, "NotificationManager is null!")
            }
        }
    }
}
