package com.jarvismini.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun ScanLine() {
    val infiniteTransition = rememberInfiniteTransition(label = "scanline")
    val yPosition by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "yPosition"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val y = yPosition * size.height
        drawLine(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFF00E0FF).copy(alpha = 0.3f),
                    Color.Transparent
                ),
                startY = y - 50f,
                endY = y + 50f
            ),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 100f
        )
    }
}
