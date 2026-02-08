package com.jarvismini.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
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
import com.jarvismini.core.progress.ProgressBlock
import com.jarvismini.core.progress.TaskTimerStore
import com.jarvismini.core.routine.model.Routine
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChecklistItem(
    block: ProgressBlock,
    routine: Routine,
    onComplete: () -> Unit,
    onIncomplete: () -> Unit
) {
    val context = LocalContext.current

    var elapsedTime by remember(block.id) {
        mutableStateOf(TaskTimerStore.getElapsed(context, block.id))
    }
    var startTime by remember(block.id) {
        mutableStateOf(TaskTimerStore.getStartTime(context, block.id))
    }
    var isTimerRunning by remember(block.id) {
        mutableStateOf(TaskTimerStore.isRunning(context, block.id))
    }

    // ðŸ”¹ Auto-start if task is active and not completed
    LaunchedEffect(block.completed, isTimerRunning) {
        if (!block.completed && !isTimerRunning && elapsedTime == 0L) {
            startTime = System.currentTimeMillis()
            isTimerRunning = true
            TaskTimerStore.setStartTime(context, block.id, startTime)
            TaskTimerStore.setRunning(context, block.id, true)
        }
    }

    // Stopwatch ticker
    LaunchedEffect(isTimerRunning, startTime) {
        if (isTimerRunning) {
            while (isTimerRunning) {
                val now = System.currentTimeMillis()
                elapsedTime = now - startTime
                TaskTimerStore.setElapsed(context, block.id, elapsedTime)
                delay(1000L)
            }
        }
    }

    val pulseAnimation = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by pulseAnimation.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        // TIMER
        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(60.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                Color(0xFF00E0FF).copy(alpha = pulseAlpha * 0.2f),
                                Color.Transparent
                            )
                        ),
                        CircleShape
                    )
                    .border(1.dp, Color(0xFF00E0FF), CircleShape)
            ) {
                Text(
                    formatElapsedTime(elapsedTime),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF00E0FF),
                    fontWeight = FontWeight.Bold
                )
            }

            IconButton(
                onClick = {
                    if (isTimerRunning) {
                        isTimerRunning = false
                        TaskTimerStore.setRunning(context, block.id, false)
                    } else {
                        startTime = System.currentTimeMillis() - elapsedTime
                        isTimerRunning = true
                        TaskTimerStore.setStartTime(context, block.id, startTime)
                        TaskTimerStore.setRunning(context, block.id, true)
                    }
                }
            ) {
                Icon(
                    if (isTimerRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color(0xFF00E0FF)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                routine.name,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00E0FF)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    isTimerRunning = false
                    TaskTimerStore.setRunning(context, block.id, false)
                    onComplete()
                }) {
                    Text("Done")
                }

                OutlinedButton(onClick = {
                    isTimerRunning = false
                    TaskTimerStore.setRunning(context, block.id, false)
                    onIncomplete()
                }) {
                    Text("Missed")
                }
            }
        }
    }
}

private fun formatElapsedTime(ms: Long): String {
    val seconds = (ms / 1000) % 60
    val minutes = (ms / (1000 * 60)) % 60
    val hours = (ms / (1000 * 60 * 60))
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
