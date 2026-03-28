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
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.jarvismini.core.routine.TaskTimerManager

/**
 * FloatingTimerService
 *
 * Thin horizontal bar overlay — dynamic, driven by TaskTimerManager.activeTimers StateFlow
 * which is now updated every second by TaskTimerService.
 *
 * Layout:  [ ⏱ TASK NAME    MM:SS remaining  ✕ ]
 *          [████████████░░░░░░░░░░░░░░░░░░░░░░░]  ← JARVIS cyan/green progress bar
 */
class FloatingTimerService : Service(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry  = LifecycleRegistry(this)
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
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

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

        // Thin bar: match_parent width, wrap height, anchored to bottom
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            x = 0
            y = 120   // slightly above nav bar
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingTimerService)
            setViewTreeViewModelStoreOwner(this@FloatingTimerService)
            setViewTreeSavedStateRegistryOwner(this@FloatingTimerService)
            setContent {
                FloatingTimerBar(
                    taskId       = taskId,
                    taskName     = taskName,
                    totalSeconds = totalSeconds,
                    onClose      = { stopSelf() }
                )
            }
        }

        // Vertical drag to reposition bar up/down
        var initY = 0; var rawY = 0f
        composeView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initY = params.y; rawY = event.rawY; true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.y = initY + (rawY - event.rawY).toInt()
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
            .setContentText("Tap ✕ on the bar to dismiss")
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Floating Timer", NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Keeps the floating timer bar alive"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }
}

// ── Thin bar Composable ──────────────────────────────────────────────────────

private val JBlue  = Color(0xFF00E0FF)
private val JGreen = Color(0xFF00FF00)
private val JRed   = Color(0xFFFF4444)
private val JBg    = Color(0xF0000D14)   // near-opaque dark teal-black

@Composable
private fun FloatingTimerBar(
    taskId: String,
    taskName: String,
    totalSeconds: Long,
    onClose: () -> Unit
) {
    val activeTimers by TaskTimerManager.activeTimers.collectAsState()
    val remaining    = activeTimers[taskId]?.remainingSeconds ?: totalSeconds
    val progress     = 1f - (remaining.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
    val isLowTime    = remaining < 120

    // Pulse border color when low
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue  = 0.4f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val accentColor = if (isLowTime) JRed.copy(alpha = pulseAlpha) else JGreen

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(JBg)
    ) {
        // ── Top row: icon / task name / time remaining / close ───────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 5.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: icon + task name
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text       = "⏱",
                    fontSize   = 12.sp,
                    modifier   = Modifier.padding(end = 6.dp)
                )
                Text(
                    text       = taskName.uppercase(),
                    fontSize   = 11.sp,
                    color      = JBlue,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    maxLines   = 1
                )
            }

            // Right: time remaining label + close
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text       = formatBarTime(remaining),
                    fontSize   = 13.sp,
                    color      = accentColor,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text       = "remaining",
                    fontSize   = 9.sp,
                    color      = JBlue.copy(alpha = 0.6f),
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text      = "✕",
                    fontSize  = 13.sp,
                    color     = JRed.copy(alpha = 0.85f),
                    fontFamily = FontFamily.Monospace,
                    modifier  = Modifier.clickable(
                        indication        = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick           = onClose
                    )
                )
            }
        }

        // ── Progress bar ─────────────────────────────────────────────────
        LinearProgressIndicator(
            progress   = { progress },
            modifier   = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(0.dp)),
            color      = accentColor,
            trackColor = JBlue.copy(alpha = 0.12f)
        )
    }
}

/** Format seconds as "MM:SS" or "H:MM:SS" */
private fun formatBarTime(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s)
    else              "%02d:%02d".format(m, s)
}
