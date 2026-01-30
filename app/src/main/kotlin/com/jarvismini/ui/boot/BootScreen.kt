package com.jarvismini.ui.boot

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

data class BootStep(
    val progress: Float,
    val message: String,
    val delayMs: Long
)

@Composable
fun BootScreen(onBootComplete: () -> Unit) {
    val bootSteps = remember {
        listOf(
            BootStep(0.15f, "INITIALIZING NEURAL NETWORKS...", 300),
            BootStep(0.35f, "LOADING VISION SYSTEMS...", 500),
            BootStep(0.55f, "CALIBRATING VOICE RECOGNITION...", 400),
            BootStep(0.75f, "ESTABLISHING SECURE PROTOCOLS...", 600),
            BootStep(0.95f, "SYSTEMS ONLINE...", 400),
            BootStep(1.0f, "JARVIS OPERATIONAL", 500)
        )
    }

    var currentStep by remember { mutableStateOf(0) }
    var bootProgress by remember { mutableStateOf(0f) }
    var bootMessage by remember { mutableStateOf("INITIALIZING...") }

    val infiniteRotation = rememberInfiniteTransition(label = "rotation")
    val rotation by infiniteRotation.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    LaunchedEffect(Unit) {
        bootSteps.forEachIndexed { index, step ->
            delay(step.delayMs)
            currentStep = index
            bootProgress = step.progress
            bootMessage = step.message
        }
        delay(500)
        onBootComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Grid background
        GridBackground()

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Animated circle
            BootCircle(
                progress = bootProgress,
                rotation = rotation,
                modifier = Modifier.size(300.dp)
            )

            Spacer(Modifier.height(40.dp))

            Text(
                text = "J.A.R.V.I.S.",
                fontSize = 48.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 8.sp,
                color = JarvisBlue
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = "MARK VII INITIALIZING...",
                fontSize = 12.sp,
                color = JarvisBlue.copy(alpha = 0.6f),
                fontFamily = FontFamily.Monospace
            )

            Spacer(Modifier.height(40.dp))

            // Progress bar
            BootProgressBar(progress = bootProgress)

            Spacer(Modifier.height(12.dp))

            Text(
                text = "${(bootProgress * 100).toInt()}%",
                fontSize = 14.sp,
                color = JarvisBlue,
                fontFamily = FontFamily.Monospace
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = bootMessage,
                fontSize = 12.sp,
                color = JarvisBlue.copy(alpha = 0.8f),
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun BootCircle(
    progress: Float,
    rotation: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = (size.width / 2f) * progress

        // Glowing center
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    JarvisBlue.copy(alpha = 0.8f),
                    JarvisBlue.copy(alpha = 0.4f),
                    Color.Transparent
                ),
                center = center,
                radius = radius
            ),
            center = center,
            radius = radius
        )

        // Rotating segments
        for (i in 0 until 8) {
            val angle = (i / 8f) * 2 * PI.toFloat() + rotation * PI.toFloat() / 180f
            drawArc(
                color = JarvisBlue.copy(alpha = 0.8f * progress),
                startAngle = angle * 180f / PI.toFloat(),
                sweepAngle = 15f,
                useCenter = false,
                style = Stroke(width = 3f),
                topLeft = Offset(center.x - radius * 0.7f, center.y - radius * 0.7f),
                size = androidx.compose.ui.geometry.Size(radius * 1.4f, radius * 1.4f)
            )
        }
    }
}

@Composable
fun BootProgressBar(progress: Float) {
    Box(
        modifier = Modifier
            .width(400.dp)
            .height(8.dp)
            .background(
                color = JarvisBlue.copy(alpha = 0.2f),
                shape = RoundedCornerShape(4.dp)
            )
            .border(
                width = 1.dp,
                color = JarvisBlue.copy(alpha = 0.3f),
                shape = RoundedCornerShape(4.dp)
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(JarvisBlue, Color(0xFF0080FF), JarvisBlue)
                    ),
                    shape = RoundedCornerShape(4.dp)
                )
                .shadow(
                    elevation = 20.dp,
                    shape = RoundedCornerShape(4.dp),
                    spotColor = JarvisBlue
                )
        )
    }
}

val JarvisBlue = Color(0xFF00E0FF)
