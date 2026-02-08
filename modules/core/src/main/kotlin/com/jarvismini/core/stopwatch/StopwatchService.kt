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
 * Foreground service to show stopwatch notification
 * 
 * Fixed version with proper lifecycle management and notification handling
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
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var updateJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        android.util.Log.d(TAG, "Service onCreate called")
        
        // CRITICAL: Create notification channel BEFORE calling startForeground
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        android.util.Log.d(TAG, "Service onStartCommand called")
        
        try {
            // Start foreground IMMEDIATELY with initial notification
            val notification = buildNotification("00:00")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // API 29+ requires specific foreground service type
                startForeground(
                    NOTIF_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIF_ID, notification)
            }
            
            android.util.Log.d(TAG, "Service started in foreground successfully")
            
            // Now start observing stopwatch state
            startObservingStopwatch()
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to start foreground: ${e.message}", e)
            stopSelf()
        }
        
        // START_STICKY ensures service restarts if killed by system
        return START_STICKY
    }

    private fun startObservingStopwatch() {
        // Cancel any existing job
        updateJob?.cancel()
        
        // Start new coroutine to observe stopwatch state
        updateJob = scope.launch {
            try {
                // Collect state changes from StopwatchManager
                StopwatchManager.state.collectLatest { state ->
                    android.util.Log.d(TAG, "State changed: isRunning=${state.isRunning}")
                    
                    if (state.isRunning) {
                        // Update notification every 500ms while running
                        while (state.isRunning && isActive) {
                            val elapsed = StopwatchManager.getCurrentElapsed()
                            val timeString = StopwatchManager.formatElapsedTimeShort(elapsed)
                            
                            updateNotification(timeString)
                            delay(500)
                            
                            // Re-check state in case it changed during delay
                            if (!StopwatchManager.state.value.isRunning) {
                                break
                            }
                        }
                    } else {
                        // Stopwatch stopped - stop service
                        android.util.Log.d(TAG, "Stopwatch stopped, stopping service")
                        stopSelf()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error in update loop: ${e.message}", e)
                stopSelf()
            }
        }
    }

    private fun updateNotification(time: String) {
        try {
            val notification = buildNotification(time)
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

    private fun buildNotification(time: String): Notification {
        // Create pending intent to open app when notification is tapped
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

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Stopwatch Running")
            .setContentText("Elapsed: $time")
            .setSmallIcon(android.R.drawable.ic_media_play)
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
