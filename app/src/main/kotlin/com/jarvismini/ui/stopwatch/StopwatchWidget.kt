package com.jarvismini.ui.stopwatch

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvismini.core.stopwatch.StopwatchManager

/**
 * Holographic stopwatch widget with Jarvis theme
 */
@Composable
fun StopwatchWidget(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val stopwatchState by StopwatchManager.state.collectAsState()

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

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF001520).copy(alpha = 0.95f),
                            Color(0xFF002030).copy(alpha = 0.90f)
                        )
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
                .border(
                    1.dp,
                    if (stopwatchState.isRunning) 
                        Color(0xFF00E0FF).copy(alpha = pulseAlpha)
                    else 
                        Color(0xFF00E0FF).copy(alpha = 0.4f),
                    RoundedCornerShape(16.dp)
                )
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Status Text
                Text(
                    text = if (stopwatchState.isRunning) "RUNNING" 
                           else if (stopwatchState.elapsedTimeMs > 0) "PAUSED"
                           else "READY",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp,
                    color = Color(0xFF00E0FF).copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Timer Display
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(180.dp)
                        .alpha(if (stopwatchState.isRunning) 1f else 0.7f)
                        .background(
                            brush = if (stopwatchState.isRunning) {
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF00E0FF).copy(alpha = pulseAlpha * 0.2f),
                                        Color.Transparent
                                    )
                                )
                            } else {
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF00E0FF).copy(alpha = 0.05f),
                                        Color.Transparent
                                    )
                                )
                            },
                            shape = CircleShape
                        )
                        .border(
                            2.dp,
                            if (stopwatchState.isRunning)
                                Color(0xFF00E0FF).copy(alpha = pulseAlpha)
                            else
                                Color(0xFF00E0FF).copy(alpha = 0.3f),
                            CircleShape
                        )
                ) {
                    Text(
                        text = StopwatchManager.formatElapsedTimeShort(stopwatchState.elapsedTimeMs),
                        fontSize = 48.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E0FF)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Control Buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Reset Button
                    IconButton(
                        onClick = { StopwatchManager.reset(context) },
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                Color(0xFF00E0FF).copy(alpha = 0.1f),
                                CircleShape
                            )
                            .border(1.dp, Color(0xFF00E0FF).copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset",
                            tint = Color(0xFF00E0FF),
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Play/Pause Button (larger, primary action)
                    IconButton(
                        onClick = { StopwatchManager.toggle(context) },
                        modifier = Modifier
                            .size(72.dp)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF00E0FF).copy(alpha = 0.3f),
                                        Color(0xFF00E0FF).copy(alpha = 0.1f)
                                    )
                                ),
                                shape = CircleShape
                            )
                            .border(2.dp, Color(0xFF00E0FF), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (stopwatchState.isRunning) 
                                Icons.Default.Pause 
                            else 
                                Icons.Default.PlayArrow,
                            contentDescription = if (stopwatchState.isRunning) "Pause" else "Start",
                            tint = Color(0xFF00E0FF),
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Stop Button
                    IconButton(
                        onClick = { StopwatchManager.stop(context) },
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                Color(0xFF00E0FF).copy(alpha = 0.1f),
                                CircleShape
                            )
                            .border(1.dp, Color(0xFF00E0FF).copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop",
                            tint = Color(0xFF00E0FF),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Button Labels
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RESET",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF00E0FF).copy(alpha = 0.6f),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (stopwatchState.isRunning) "PAUSE" else "START",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF00E0FF).copy(alpha = 0.8f),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "STOP",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF00E0FF).copy(alpha = 0.6f),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * Compact stopwatch display for top bar or floating widget
 */
@Composable
fun CompactStopwatchDisplay(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val stopwatchState by StopwatchManager.state.collectAsState()

    if (stopwatchState.isRunning || stopwatchState.elapsedTimeMs > 0) {
        Row(
            modifier = modifier
                .background(
                    Color(0xFF00E0FF).copy(alpha = 0.1f),
                    RoundedCornerShape(20.dp)
                )
                .border(1.dp, Color(0xFF00E0FF).copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (stopwatchState.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color(0xFF00E0FF),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = StopwatchManager.formatElapsedTimeShort(stopwatchState.elapsedTimeMs),
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF00E0FF),
                fontWeight = FontWeight.Bold
            )
        }
    }
}
