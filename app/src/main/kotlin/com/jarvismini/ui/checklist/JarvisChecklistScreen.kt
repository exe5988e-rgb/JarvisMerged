package com.jarvismini.ui.checklist

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvismini.core.routine.RoutineProvider
import com.jarvismini.core.routine.TaskTimerManager
import com.jarvismini.core.progress.ProgressRepository
import com.jarvismini.core.progress.ProgressBlock
import com.jarvismini.core.tts.AssistantTTS
import com.jarvismini.ui.timer.TaskTimerDialog
import com.jarvismini.ui.theme.GlassSurface
import com.jarvismini.ui.theme.JarvisMotion
import com.jarvismini.ui.theme.JarvisRadius
import com.jarvismini.ui.theme.JarvisTextPrimary
import com.jarvismini.ui.theme.JarvisTextSecondary
import com.jarvismini.ui.theme.pressScale
import com.jarvismini.ui.theme.rememberMotionEnabled
import com.jarvismini.ui.timer.TaskTimerDisplay
import java.util.*

private val JarvisBlue  = Color(0xFF00E0FF)
private val JarvisGreen = Color(0xFF00FF00)
private val JarvisRed   = Color(0xFFFF4444)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JarvisChecklistScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    var refreshTrigger by remember { mutableStateOf(0) }
    var routines by remember { mutableStateOf(emptyList<com.jarvismini.core.routine.model.Routine>()) }
    var blocks   by remember { mutableStateOf(emptyList<ProgressBlock>()) }

    // FIX: day-of-week must also respect the 3 AM boundary
    val today = remember { getCurrentDayOfWeek() }

    LaunchedEffect(refreshTrigger) {
        routines = RoutineProvider.getAllRoutines(context).filter { routine ->
            routine.enabled && routine.trigger?.days?.contains(today) == true
        }.sortedBy { routine ->
            // FIX: times 00:00–02:59 sort AFTER 23:59 by adding 24*60 minutes
            parseTimeToMinutesOrdered(routine.trigger?.time ?: "00:00")
        }
        blocks = ProgressRepository.getTodayBlocks()
    }

    val activeTimers by TaskTimerManager.activeTimers.collectAsState()

    var showTimerDialog  by remember { mutableStateOf(false) }
    var selectedTaskId   by remember { mutableStateOf("") }
    var selectedTaskName by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color.Black, Color(0xFF001520), Color.Black)
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(
                        "J.A.R.V.I.S TASK MATRIX",
                        color      = JarvisBlue,
                        fontFamily = FontFamily.Monospace,
                        fontSize   = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier   = Modifier.semantics { heading() }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = JarvisBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.6f)
                )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                val completedCount    = blocks.count { it.completed }
                val totalCount        = routines.size
                val completionPercent = if (totalCount > 0) (completedCount * 100) / totalCount else 0

                // Hero summary (glass-compose: one glass surface per screen); the task list below
                // is dense, so its cards stay flat and solid.
                val motionEnabled = rememberMotionEnabled()
                val progress = if (totalCount > 0) completedCount.toFloat() / totalCount else 0f
                val animatedProgress by animateFloatAsState(
                    targetValue   = progress,
                    animationSpec = if (motionEnabled) JarvisMotion.snappy() else snap(),
                    label         = "completionProgress"
                )

                GlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {
                            contentDescription =
                                "System completion $completionPercent percent, $completedCount of $totalCount tasks done"
                        },
                    shape       = RoundedCornerShape(JarvisRadius.Card),
                    cornerTicks = true
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text          = "SYSTEM COMPLETION",
                                fontSize      = 12.sp,
                                letterSpacing = 2.sp,
                                color         = JarvisTextSecondary,
                                fontFamily    = FontFamily.Monospace
                            )
                            Text(
                                text       = "$completionPercent%",
                                fontSize   = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color      = JarvisTextPrimary,
                                fontFamily = FontFamily.Monospace,
                                fontFeatureSettings = "tnum"
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        // Plain Box bar: avoids depending on a newer LinearProgressIndicator overload.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(JarvisBlue.copy(alpha = 0.15f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                                    .fillMaxHeight()
                                    .background(JarvisBlue)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text       = "$completedCount of $totalCount tasks done",
                            fontSize   = 12.sp,
                            color      = JarvisTextSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Last card clears the (edge-to-edge) navigation bar instead of ending under it.
                val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding      = PaddingValues(bottom = navBottom + 16.dp)
                ) {
                    if (routines.isEmpty()) {
                        item {
                            Text(
                                text       = "No tasks scheduled for today.",
                                fontSize   = 13.sp,
                                color      = JarvisTextSecondary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    items(routines, key = { it.id }) { routine ->
                        val block = blocks.find { it.id == routine.id }
                        TaskCard(
                            routine      = routine,
                            block        = block,
                            timerState   = activeTimers[routine.id],
                            onTimerClick = {
                                selectedTaskId   = routine.id
                                selectedTaskName = routine.name
                                showTimerDialog  = true
                            },
                            onStopTimer = {
                                TaskTimerManager.stopTimer(context, routine.id)
                                AssistantTTS.speak(context, "Timer stopped for ${routine.name}")
                            },
                            onMarkComplete = {
                                ProgressRepository.markComplete(context, routine.id)
                                AssistantTTS.speak(context, "${routine.name} marked as complete")
                                refreshTrigger++
                            },
                            onMarkMissed = {
                                ProgressRepository.markIncomplete(context, routine.id)
                                AssistantTTS.speak(context, "${routine.name} marked as missed")
                                refreshTrigger++
                            }
                        )
                    }
                }
            }
        }
    }

    if (showTimerDialog) {
        TaskTimerDialog(
            taskName  = selectedTaskName,
            onDismiss = { showTimerDialog = false },
            onStartTimer = { durationMinutes ->
                TaskTimerManager.startTimer(
                    context,
                    selectedTaskId,
                    selectedTaskName,
                    durationMinutes
                )
                showTimerDialog = false
                AssistantTTS.speak(context, "Timer started for $selectedTaskName, $durationMinutes minutes")
            }
        )
    }
}

@Composable
fun TaskCard(
    routine: com.jarvismini.core.routine.model.Routine,
    block: ProgressBlock?,
    timerState: TaskTimerManager.TimerState?,
    onTimerClick: () -> Unit,
    onStopTimer: () -> Unit,
    onMarkComplete: () -> Unit,
    onMarkMissed: () -> Unit
) {
    val isCompleted = block?.completed == true
    val isMissed    = block?.missedAt != null && !isCompleted

    // State change feedback: card tint + border ease to the new state (instant when motion is off).
    val motionEnabled = rememberMotionEnabled()
    val colorSpec: AnimationSpec<Color> =
        if (motionEnabled) tween(JarvisMotion.Duration.Normal, easing = JarvisMotion.Standard) else snap()
    val containerColor by animateColorAsState(
        targetValue = when {
            isCompleted -> Color(0xFF001520).copy(alpha = 0.5f)
            isMissed    -> Color(0xFF200505)
            else        -> Color(0xFF001520)
        },
        animationSpec = colorSpec,
        label = "taskContainer"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isCompleted -> JarvisGreen.copy(alpha = 0.5f)
            isMissed    -> JarvisRed.copy(alpha = 0.5f)
            else        -> JarvisBlue.copy(alpha = 0.5f)
        },
        animationSpec = colorSpec,
        label = "taskBorder"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(JarvisRadius.Card),
        colors   = CardDefaults.cardColors(containerColor = containerColor),
        border   = BorderStroke(width = 1.dp, color = borderColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    // read as one item: "<task name>, Completed/Missed/Pending"
                    modifier = Modifier
                        .weight(1f)
                        .semantics(mergeDescendants = true) {
                            stateDescription = when {
                                isCompleted -> "Completed"
                                isMissed    -> "Missed"
                                else        -> "Pending"
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        modifier = Modifier.clearAndSetSemantics { },   // glyph is decorative; state is announced above
                        text = when {
                            isCompleted -> "✓ "
                            isMissed    -> "✗ "
                            else        -> "○ "
                        },
                        color = when {
                            isCompleted -> JarvisGreen
                            isMissed    -> JarvisRed
                            else        -> JarvisBlue
                        },
                        fontSize   = 20.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text       = routine.name,
                        fontSize   = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color      = when {
                            isCompleted -> JarvisGreen.copy(alpha = 0.7f)
                            isMissed    -> JarvisRed.copy(alpha = 0.7f)
                            else        -> JarvisBlue
                        },
                        fontFamily = FontFamily.Monospace
                    )
                }

                if (!isCompleted && !isMissed) {
                    if (timerState == null) {
                        HudActionButton(
                            label       = "⏱️ TIMER",
                            color       = JarvisGreen,
                            description = "Start timer for ${routine.name}",
                            onClick     = onTimerClick
                        )
                    } else {
                        HudActionButton(
                            label       = "■ STOP",
                            color       = JarvisRed,
                            description = "Stop timer for ${routine.name}",
                            onClick     = onStopTimer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            timerState?.let {
                TaskTimerDisplay(
                    remainingSeconds = it.remainingSeconds,
                    totalSeconds     = it.totalSeconds
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            routine.actions.forEach { action ->
                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                    Text("• ", color = JarvisGreen, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    Text(
                        text = when (action.type) {
                            "speak"       -> "Speak: ${action.params["message"]}"
                            "notify"      -> "Notify: ${action.params["message"]}"
                            "set_mode"    -> "Set mode: ${action.params["mode"]}"
                            "set_dnd"     -> "Set dnd: ${action.params["dnd"]}"
                            "start_timer" -> "Timer: ${action.params["task"]} (${action.params["duration"]} min)"
                            else          -> "${action.type}: ${action.params}"
                        },
                        color      = JarvisGreen.copy(alpha = 0.7f),
                        fontSize   = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            routine.trigger?.time?.let { time ->
                Text(
                    text       = "Scheduled: $time",
                    color      = JarvisBlue.copy(alpha = 0.7f),
                    fontSize   = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HudActionButton(
                    label       = "DONE",
                    color       = JarvisGreen,
                    description = "Mark ${routine.name} as done",
                    onClick     = onMarkComplete,
                    modifier    = Modifier.padding(end = 8.dp)
                )
                HudActionButton(
                    label       = "MISSED",
                    color       = JarvisRed,
                    description = "Mark ${routine.name} as missed",
                    onClick     = onMarkMissed
                )
            }
        }
    }
}

/** Outlined HUD action: 48dp tall hit area, press scale, one spoken description. */
@Composable
private fun HudActionButton(
    label: String,
    color: Color,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val source = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick           = onClick,
        interactionSource = source,
        colors            = ButtonDefaults.outlinedButtonColors(contentColor = color),
        border            = BorderStroke(1.dp, color),
        modifier          = modifier
            .heightIn(min = 48.dp)
            .pressScale(source)
            .semantics { contentDescription = description }
    ) {
        Text(label, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

/**
 * FIX: Sort key for display order respecting 3 AM boundary.
 * Times 00:00–02:59 get +1440 so they appear AFTER 23:59.
 * Result: 08:00(480) → 19:00(1140) → 23:30(1410) → 00:20(1460) → 00:50(1490) → 01:00(1500)
 */
private fun parseTimeToMinutesOrdered(time: String): Int {
    return try {
        val parts   = time.split(":")
        val hours   = parts[0].toInt()
        val minutes = parts[1].toInt()
        val raw     = hours * 60 + minutes
        // Anything before 03:00 belongs to the tail end of the previous evening
        if (hours < 3) raw + 1440 else raw
    } catch (_: Exception) {
        0
    }
}

/**
 * FIX: day-of-week respects 3 AM boundary.
 * Before 03:00, we're still in "yesterday's" session.
 */
private fun getCurrentDayOfWeek(): String {
    val cal = Calendar.getInstance()
    if (cal.get(Calendar.HOUR_OF_DAY) < 3) {
        cal.add(Calendar.DAY_OF_YEAR, -1)
    }
    return when (cal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.SUNDAY    -> "SUN"
        Calendar.MONDAY    -> "MON"
        Calendar.TUESDAY   -> "TUE"
        Calendar.WEDNESDAY -> "WED"
        Calendar.THURSDAY  -> "THU"
        Calendar.FRIDAY    -> "FRI"
        Calendar.SATURDAY  -> "SAT"
        else               -> "SUN"
    }
}
