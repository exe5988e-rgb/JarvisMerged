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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvismini.core.progress.ProgressBlock
import com.jarvismini.core.routine.ActionDispatcher
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
    var elapsedTime by remember { mutableStateOf(0L) }
    var isTimerRunning by remember { mutableStateOf(false) }

    // Stopwatch ticker
    LaunchedEffect(isTimerRunning) {
        if (isTimerRunning) {
            val startTime = System.currentTimeMillis()
            while (isTimerRunning) {
                elapsedTime = System.currentTimeMillis() - startTime
                delay(100L)
            }
        }
    }

    // Pulsing animation for active timer
    val pulseAnimation = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by pulseAnimation.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val containerColor = when {
        block.completed -> Color(0xFF00E0FF).copy(alpha = 0.15f)
        block.missedAt != null -> Color(0xFFFF4444).copy(alpha = 0.15f)
        else -> Color(0xFF001520).copy(alpha = 0.8f)
    }

    val borderColor = when {
        block.completed -> Color(0xFF00E0FF)
        block.missedAt != null -> Color(0xFFFF4444)
        else -> Color(0xFF00E0FF).copy(alpha = 0.4f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(containerColor)
                .border(1.dp, borderColor)
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ===== STOPWATCH TIMER (LEFT SIDE) =====
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(end = 16.dp)
                ) {
                    // Timer Display
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(60.dp)
                            .background(
                                brush = if (isTimerRunning) {
                                    Brush.radialGradient(
                                        colors = listOf(
                                            Color(0xFF00E0FF).copy(alpha = pulseAlpha * 0.3f),
                                            Color.Transparent
                                        )
                                    )
                                } else {
                                    Brush.radialGradient(
                                        colors = listOf(
                                            Color(0xFF00E0FF).copy(alpha = 0.1f),
                                            Color.Transparent
                                        )
                                    )
                                },
                                shape = CircleShape
                            )
                            .border(
                                1.dp,
                                if (isTimerRunning) Color(0xFF00E0FF).copy(alpha = pulseAlpha)
                                else Color(0xFF00E0FF).copy(alpha = 0.4f),
                                CircleShape
                            )
                    ) {
                        Text(
                            text = formatElapsedTime(elapsedTime),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF00E0FF),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Start/Stop Button
                    IconButton(
                        onClick = { isTimerRunning = !isTimerRunning },
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                Color(0xFF00E0FF).copy(alpha = 0.1f),
                                CircleShape
                            )
                            .border(1.dp, Color(0xFF00E0FF).copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isTimerRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isTimerRunning) "Stop" else "Start",
                            tint = Color(0xFF00E0FF),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // ===== TASK DETAILS =====
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = routine.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E0FF)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Scheduled: ${formatTime(block.scheduledAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF00E0FF).copy(alpha = 0.7f)
                    )

                    block.completedAt?.let {
                        Text(
                            text = "Completed: ${formatTime(it)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF00E0FF).copy(alpha = 0.7f)
                        )
                    }

                    block.missedAt?.let {
                        Text(
                            text = "Missed: ${formatTime(it)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFFF4444).copy(alpha = 0.9f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // ===== ACTION BUTTONS (RIGHT SIDE) =====
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                isTimerRunning = false
                                onComplete()
                            },
                            enabled = !block.completed,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00E0FF).copy(alpha = 0.2f),
                                contentColor = Color(0xFF00E0FF),
                                disabledContainerColor = Color(0xFF00E0FF).copy(alpha = 0.1f),
                                disabledContentColor = Color(0xFF00E0FF).copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.border(
                                1.dp,
                                if (!block.completed) Color(0xFF00E0FF) else Color(0xFF00E0FF).copy(alpha = 0.3f)
                            )
                        ) {
                            Text("Done")
                        }

                        OutlinedButton(
                            onClick = {
                                isTimerRunning = false
                                onIncomplete()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFF00E0FF).copy(alpha = 0.8f)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                Color(0xFF00E0FF).copy(alpha = 0.6f)
                            )
                        ) {
                            Text("Missed")
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
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
