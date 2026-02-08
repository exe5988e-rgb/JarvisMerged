package com.jarvismini.ui.checklist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.jarvismini.ui.timer.TaskTimerDisplay
import java.util.*

private val JarvisBlue = Color(0xFF00E0FF)
private val JarvisGreen = Color(0xFF00FF00)
private val JarvisRed = Color(0xFFFF4444)

/**
 * FULLY WORKING JarvisChecklistScreen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JarvisChecklistScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    
    // State with refresh trigger
    var refreshTrigger by remember { mutableStateOf(0) }
    var routines by remember { mutableStateOf(emptyList<com.jarvismini.core.routine.model.Routine>()) }
    var blocks by remember { mutableStateOf(emptyList<ProgressBlock>()) }
    val today = remember { getCurrentDayOfWeek() }

    // Load data whenever refreshTrigger changes
    LaunchedEffect(refreshTrigger) {
        routines = RoutineProvider.getAllRoutines(context).filter { routine ->
            routine.enabled && routine.trigger?.days?.contains(today) == true
        }.sortedBy { routine ->
            parseTimeToMinutes(routine.trigger?.time ?: "00:00")
        }
        blocks = ProgressRepository.getTodayBlocks()
    }

    // Track active timers
    val activeTimers by TaskTimerManager.activeTimers.collectAsState()

    // Dialog state
    var showTimerDialog by remember { mutableStateOf(false) }
    var selectedTaskId by remember { mutableStateOf("") }
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
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // ===== TOP APP BAR WITH BACK BUTTON =====
            TopAppBar(
                title = {
                    Text(
                        "J.A.R.V.I.S TASK MATRIX",
                        color = JarvisBlue,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
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
                // Completion percentage
                val completedCount = blocks.count { it.completed }
                val totalCount = routines.size
                val completionPercent = if (totalCount > 0) (completedCount * 100) / totalCount else 0

                Text(
                    text = "SYSTEM COMPLETION: $completionPercent% ($completedCount/$totalCount)",
                    fontSize = 14.sp,
                    color = JarvisBlue.copy(alpha = 0.7f),
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Task list
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(routines) { routine ->
                        val block = blocks.find { it.id == routine.id }
                        TaskCard(
                            routine = routine,
                            block = block,
                            timerState = activeTimers[routine.id],
                            onTimerClick = {
                                selectedTaskId = routine.id
                                selectedTaskName = routine.name
                                showTimerDialog = true
                            },
                            onStopTimer = {
                                TaskTimerManager.stopTimer(context, routine.id)
                                AssistantTTS.speak(context, "Timer stopped for ${routine.name}")
                            },
                            onMarkComplete = {
                                ProgressRepository.markComplete(context, routine.id)
                                AssistantTTS.speak(context, "${routine.name} marked as complete")
                                refreshTrigger++ // Force refresh
                            },
                            onMarkMissed = {
                                ProgressRepository.markIncomplete(context, routine.id)
                                AssistantTTS.speak(context, "${routine.name} marked as missed")
                                refreshTrigger++ // Force refresh
                            }
                        )
                    }
                }
            }
        }
    }

    // Timer dialog
    if (showTimerDialog) {
        TaskTimerDialog(
            taskName = selectedTaskName,
            onDismiss = { 
                showTimerDialog = false
            },
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
    val isMissed = block?.missedAt != null && !isCompleted

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isCompleted -> Color(0xFF001520).copy(alpha = 0.5f)
                isMissed -> Color(0xFF200505)
                else -> Color(0xFF001520)
            }
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = when {
                isCompleted -> JarvisGreen.copy(alpha = 0.5f)
                isMissed -> JarvisRed.copy(alpha = 0.5f)
                else -> JarvisBlue.copy(alpha = 0.5f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Status indicator + Title and timer button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Status icon
                    Text(
                        text = when {
                            isCompleted -> "✓ "
                            isMissed -> "✗ "
                            else -> "○ "
                        },
                        color = when {
                            isCompleted -> JarvisGreen
                            isMissed -> JarvisRed
                            else -> JarvisBlue
                        },
                        fontSize = 20.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    
                    Text(
                        text = routine.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isCompleted -> JarvisGreen.copy(alpha = 0.7f)
                            isMissed -> JarvisRed.copy(alpha = 0.7f)
                            else -> JarvisBlue
                        },
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Timer button (only show if not completed/missed)
                if (!isCompleted && !isMissed) {
                    if (timerState == null) {
                        OutlinedButton(
                            onClick = onTimerClick,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = JarvisGreen
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, JarvisGreen)
                        ) {
                            Text(
                                "⏱️ TIMER",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = onStopTimer,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = JarvisRed
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, JarvisRed)
                        ) {
                            Text(
                                "■ STOP",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Timer display (if active)
            timerState?.let {
                TaskTimerDisplay(
                    remainingSeconds = it.remainingSeconds,
                    totalSeconds = it.totalSeconds
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Actions list
            routine.actions.forEach { action ->
                Row(
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Text(
                        text = "• ",
                        color = JarvisGreen,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = when (action.type) {
                            "speak" -> "Speak: ${action.params["message"]}"
                            "notify" -> "Notify: ${action.params["message"]}"
                            "set_mode" -> "Set mode: ${action.params["mode"]}"
                            "set_dnd" -> "Set dnd: ${action.params["dnd"]}"
                            "start_timer" -> "Timer: ${action.params["task"]} (${action.params["duration"]} min)"
                            else -> "${action.type}: ${action.params}"
                        },
                        color = JarvisGreen.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Scheduled time
            routine.trigger?.time?.let { time ->
                Text(
                    text = "Scheduled: $time",
                    color = JarvisBlue.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action buttons (only show if not already marked)
            if (!isCompleted && !isMissed) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    OutlinedButton(
                        onClick = onMarkComplete,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = JarvisGreen
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, JarvisGreen)
                    ) {
                        Text(
                            "DONE",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }

                    OutlinedButton(
                        onClick = onMarkMissed,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = JarvisRed
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, JarvisRed)
                    ) {
                        Text(
                            "MISSED",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                }
            } else if (isCompleted) {
                Text(
                    text = "✓ COMPLETED",
                    color = JarvisGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            } else if (isMissed) {
                Text(
                    text = "✗ MISSED",
                    color = JarvisRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

private fun getCurrentDayOfWeek(): String {
    val cal = Calendar.getInstance()
    return when (cal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.SUNDAY -> "SUN"
        Calendar.MONDAY -> "MON"
        Calendar.TUESDAY -> "TUE"
        Calendar.WEDNESDAY -> "WED"
        Calendar.THURSDAY -> "THU"
        Calendar.FRIDAY -> "FRI"
        Calendar.SATURDAY -> "SAT"
        else -> "SUN"
    }
}

private fun parseTimeToMinutes(time: String): Int {
    return try {
        val parts = time.split(":")
        parts[0].toInt() * 60 + parts[1].toInt()
    } catch (e: Exception) {
        0
    }
}
