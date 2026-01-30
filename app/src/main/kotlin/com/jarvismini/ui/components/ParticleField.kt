package com.jarvismini.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.random.Random

@Composable
fun ParticleField(progress: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val random = Random(42)
        val particles = List(50) {
            Offset(
                random.nextFloat() * size.width,
                (random.nextFloat() * size.height + progress * 50) % size.height
            )
        }

        // Draw particles
        particles.forEach { p ->
            drawCircle(
                color = Color(0xFF00C8FF).copy(alpha = 0.3f),
                center = p,
                radius = 2f
            )
        }

        // Connect nearby particles
        for (i in particles.indices) {
            for (j in i + 1 until particles.size) {
                val distance = (particles[i] - particles[j]).getDistance()
                if (distance < 100f) {
                    drawLine(
                        color = Color(0xFF00C8FF).copy(alpha = 0.1f),
                        start = particles[i],
                        end = particles[j],
                        strokeWidth = 0.5f
                    )
                }
            }
        }
    }
}
