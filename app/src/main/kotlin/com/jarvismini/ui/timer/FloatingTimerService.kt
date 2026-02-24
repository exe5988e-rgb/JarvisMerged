package com.jarvismini.ui.timer

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.jarvismini.core.routine.TaskTimerManager

/**
 * FloatingTimerService (lives in :app — has Compose dependencies)
 *
 * Draws a compact draggable JARVIS countdown overlay via WindowManager + ComposeView.
 * Started automatically alongside TaskTimerService when the TIMER button is pressed,
 * via TaskTimerManager.FloatingTimerDelegate registered in CoreApp.
 *
 * Requires: android.permission.SYSTEM_ALERT_WINDOW
 * Permission is requested at startup in MainActivity.
 * If not granted, start() silently skips — notification timer still works.
 */
class FloatingTimerService : Service(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    // ── Lifecycle plumbing required by ComposeView inside a Service ─────
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val vmStore = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = vmStore

    private val savedStateController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null

    companion object {
        private const val CHANNEL_ID  = "floating_timer_channel"
        private const val NOTIF_ID    = 8877
        const val EXTRA_TASK_ID       = "ft_task_id"
        const val EXTRA_TASK_NAME     = "ft_task_name"
        const val EXTRA_TOTAL_SECONDS = "ft_total_seconds"

        fun canDrawOverlays(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                    Settings.canDrawOverlays(context)

        fun start(context: Context, taskId: String, taskName: String, totalSeconds: Long) {
            if (!canDrawOverlays(context)) return
            val intent = Intent(context, FloatingTimerService::class.java).apply {
                putExtra(EXTRA_TASK_ID,       taskId)
                putExtra(EXTRA_TASK_NAME,     taskName)
                putExtra(EXTRA_TOTAL_SECONDS, totalSeconds)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(intent)
            else
                context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingTimerService::class.java))
        }
    }

    override fun onCreate() {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val taskId       = intent?.getStringExtra(EXTRA_TASK_ID)           ?: "task"
        val taskName     = intent?.getStringExtra(EXTRA_TASK_NAME)         ?: "Task"
        val totalSeconds = intent?.getLongExtra(EXTRA_TOTAL_SECONDS, 3600) ?: 3600

        startForeground(NOTIF_ID, buildForegroundNotif(taskName))
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        showOverlay(taskId, taskName, totalSeconds)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        removeOverlay()
        vmStore.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showOverlay(taskId: String, taskName: String, totalSeconds: Long) {
        removeOverlay()

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 200
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingTimerService)
            setViewTreeViewModelStoreOwner(this@FloatingTimerService)
            setViewTreeSavedStateRegistryOwner(this@FloatingTimerService)
            setContent {
                FloatingTimerOverlay(
                    taskId       = taskId,
                    taskName     = taskName,
                    totalSeconds = totalSeconds,
                    onClose      = { stopSelf() }
                )
            }
        }

        // Drag via raw touch — avoids competing with Compose pointer input
        var initX = 0; var initY = 0
        var rawX = 0f; var rawY = 0f
        composeView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = params.x; initY = params.y
                    rawX = event.rawX; rawY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initX + (event.rawX - rawX).toInt()
                    params.y = initY + (event.rawY - rawY).toInt()
                    runCatching { windowManager.updateViewLayout(composeView, params) }
                    true
                }
                else -> false
            }
        }

        overlayView = composeView
        runCatching { windowManager.addView(composeView, params) }
    }

    private fun removeOverlay() {
        overlayView?.let { v -> runCatching { windowManager.removeView(v) } }
        overlayView = null
    }

    private fun buildForegroundNotif(taskName: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("⏱ $taskName — floating timer active")
            .setContentText("Tap ✕ on the overlay to dismiss")
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Floating Timer", NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Keeps the floating timer overlay alive"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }
}

// ── Floating overlay Composable ──────────────────────────────────────────────

private val FBlue  = Color(0xFF00E0FF)
private val FGreen = Color(0xFF00FF00)
private val FRed   = Color(0xFFFF4444)
private val FBg    = Color(0xE6001520)

@Composable
private fun FloatingTimerOverlay(
    taskId: String,
    taskName: String,
    totalSeconds: Long,
    onClose: () -> Unit
) {
    val activeTimers by TaskTimerManager.activeTimers.collectAsState()
    val remaining    = activeTimers[taskId]?.remainingSeconds ?: totalSeconds
    val progress     = 1f - (remaining.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
    val isLowTime    = remaining < 120

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue  = 0.4f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Column(
        modifier = Modifier
            .width(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(FBg)
            .border(
                width = 1.dp,
                color = if (isLowTime) FRed.copy(alpha = pulseAlpha) else FBlue.copy(alpha = 0.8f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text       = taskName.uppercase().take(16),
                fontSize   = 9.sp,
                color      = FBlue,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                maxLines   = 1
            )
            Text(
                text      = "✕",
                fontSize  = 12.sp,
                color     = FRed,
                fontFamily = FontFamily.Monospace,
                modifier  = Modifier
                    .padding(start = 8.dp)
                    .clickable(
                        indication        = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick           = onClose
                    )
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text       = formatFTime(remaining),
            fontSize   = 28.sp,
            color      = if (isLowTime) FRed else FGreen,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier   = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(6.dp))

        LinearProgressIndicator(
            progress   = { progress },
            modifier   = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color      = if (isLowTime) FRed else FGreen,
            trackColor = FBlue.copy(alpha = 0.15f)
        )

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text       = "⏱ JARVIS TIMER — DRAG TO MOVE",
            fontSize   = 7.sp,
            color      = FBlue.copy(alpha = 0.45f),
            fontFamily = FontFamily.Monospace,
            modifier   = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

private fun formatFTime(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s)
    else "%02d:%02d".format(m, s)
}
