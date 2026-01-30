package com.jarvismini.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun ScanLine(progress: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val y = size.height * progress
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFF00E0FF).copy(alpha = 0.05f),
                    Color.Transparent
                ),
                startY = y - 50f,
                endY = y + 50f
            ),
            topLeft = Offset(0f, y - 50f),
            size = androidx.compose.ui.geometry.Size(size.width, 100f)
        )
    }
}
