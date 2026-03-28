package com.jarvismini.ui.timer

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val JarvisBlue = Color(0xFF00E0FF)
private val JarvisGreen = Color(0xFF00FF00)
private val JarvisRed = Color(0xFFFF4444)

/**
 * Live countdown timer display for task cards
 */
@Composable
fun TaskTimerDisplay(
    remainingSeconds: Long,
    totalSeconds: Long
) {
    val progress = 1f - (remainingSeconds.toFloat() / totalSeconds.toFloat())
    
    // Pulsing animation when time is running low (< 2 minutes)
    val isLowTime = remainingSeconds < 120
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF001520))
            .border(
                width = 1.dp,
                color = if (isLowTime) JarvisRed.copy(alpha = alpha) else JarvisBlue.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "⏱️ TIMER ACTIVE",
                fontSize = 11.sp,
                color = if (isLowTime) JarvisRed else JarvisBlue,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = formatTime(remainingSeconds),
                fontSize = 16.sp,
                color = if (isLowTime) JarvisRed else JarvisGreen,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = if (isLowTime) JarvisRed else JarvisGreen,
            trackColor = Color(0xFF001520),
        )
    }
}

private fun formatTime(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format("%02d:%02d", minutes, secs)
    }
}
