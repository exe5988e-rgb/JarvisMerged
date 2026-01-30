package com.jarvismini.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.random.Random

data class Particle(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val alpha: Float
)

@Composable
fun ParticleField() {
    val particles = remember {
        List(50) {
            Particle(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                vx = (Random.nextFloat() - 0.5f) * 0.0005f,
                vy = (Random.nextFloat() - 0.5f) * 0.0005f,
                alpha = Random.nextFloat() * 0.3f + 0.1f
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "particles")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(100000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        particles.forEach { particle ->
            val px = ((particle.x + particle.vx * time) % 1f) * size.width
            val py = ((particle.y + particle.vy * time) % 1f) * size.height

            drawCircle(
                color = Color(0xFF00E0FF).copy(alpha = particle.alpha),
                radius = 2f,
                center = Offset(px, py)
            )
        }
    }
}
