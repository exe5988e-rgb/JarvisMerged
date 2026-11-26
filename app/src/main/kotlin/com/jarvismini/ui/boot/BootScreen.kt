package com.jarvismini.ui.boot

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvismini.ui.components.GridBackground
import com.jarvismini.ui.theme.JarvisBlack
import com.jarvismini.ui.theme.JarvisBlue
import com.jarvismini.ui.theme.JarvisMotion
import com.jarvismini.ui.theme.JarvisTextPrimary
import com.jarvismini.ui.theme.JarvisTextSecondary
import com.jarvismini.ui.theme.rememberMotionEnabled
import com.jarvismini.ui.theme.staggerEnter
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.max

data class BootStep(
    val progress: Float,
    val message: String,
    val delayMs: Long
)

/**
 * Boot sequence. Visual hierarchy: orb (hero) -> title -> progress -> status line.
 * Motion tokens come from JarvisMotion; the spinning segments and the staggered enter
 * are dropped when reduced motion is on (animator scale 0 / battery saver), and the
 * progress then jumps between steps instead of easing. Progress and status are exposed
 * as one polite live region so TalkBack announces "42 percent, loading vision systems".
 */
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

    val motionEnabled = rememberMotionEnabled()

    var targetProgress by remember { mutableStateOf(0f) }
    var bootMessage by remember { mutableStateOf("INITIALIZING...") }

    // Ease between boot steps; with reduced motion jump straight to the step.
    val progressSpec: FiniteAnimationSpec<Float> =
        if (motionEnabled) JarvisMotion.gentle() else snap()
    val bootProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = progressSpec,
        label = "bootProgress"
    )

    val rotation = if (motionEnabled) bootSpin() else 0f

    LaunchedEffect(Unit) {
        bootSteps.forEach { step ->
            delay(step.delayMs)
            targetProgress = step.progress
            bootMessage = step.message
        }
        delay(500)
        onBootComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisBlack),
        contentAlignment = Alignment.Center
    ) {
        GridBackground()

        // Scrollable so large font scale / landscape never clips the status text.
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            BootCircle(
                progress = bootProgress,
                rotation = rotation,
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .fillMaxWidth(0.75f)
                    .aspectRatio(1f)
                    // Decorative: the progress info below carries the meaning.
                    .clearAndSetSemantics { }
            )

            Spacer(Modifier.height(32.dp))

            Text(
                text = "J.A.R.V.I.S.",
                fontSize = 36.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 6.sp,
                color = JarvisTextPrimary,
                maxLines = 1,
                modifier = Modifier
                    .staggerEnter(1)
                    .semantics { heading() }
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "MARK VII",
                fontSize = 14.sp,
                letterSpacing = 3.sp,
                color = JarvisTextSecondary,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.staggerEnter(2)
            )

            Spacer(Modifier.height(32.dp))

            BootProgressBar(
                progress = bootProgress,
                modifier = Modifier.staggerEnter(3)
            )

            Spacer(Modifier.height(12.dp))

            // One live region: percent + current step message.
            Column(
                modifier = Modifier
                    .staggerEnter(4)
                    .semantics(mergeDescendants = true) {
                        liveRegion = LiveRegionMode.Polite
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${(bootProgress * 100).toInt()}%",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = JarvisTextPrimary,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = bootMessage,
                    fontSize = 14.sp,
                    color = JarvisTextSecondary,
                    fontFamily = FontFamily.Monospace
                )
            }
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

        // Safety guard: nothing to draw (and no zero-radius gradient) at progress = 0.
        if (progress <= 0f) return@Canvas

        val safeProgress = max(progress, 0.01f)
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = (size.width / 2f) * safeProgress
        val strokeWidth = 1.5.dp.toPx()

        // Glowing core
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
            val angle = (i / 8f) * 2f * PI.toFloat() + rotation * PI.toFloat() / 180f

            drawArc(
                color = JarvisBlue.copy(alpha = 0.8f * safeProgress),
                startAngle = angle * 180f / PI.toFloat(),
                sweepAngle = 15f,
                useCenter = false,
                style = Stroke(width = strokeWidth * 2f),
                topLeft = Offset(
                    center.x - radius * 0.7f,
                    center.y - radius * 0.7f
                ),
                size = Size(radius * 1.4f, radius * 1.4f)
            )
        }
    }
}

@Composable
fun BootProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val safeProgress = progress.coerceIn(0f, 1f)
    // Concentric radii: inner fill radius = outer radius - 1dp border.
    val outer = RoundedCornerShape(5.dp)
    val inner = RoundedCornerShape(4.dp)

    Box(
        modifier = modifier
            .widthIn(max = 400.dp)
            .fillMaxWidth()
            .height(10.dp)
            .clip(outer)
            .background(JarvisBlue.copy(alpha = 0.18f), outer)
            .border(1.dp, JarvisBlue.copy(alpha = 0.45f), outer)
            .semantics {
                contentDescription = "Boot progress"
                progressBarRangeInfo = ProgressBarRangeInfo(safeProgress, 0f..1f)
            }
    ) {
        Box(
            modifier = Modifier
                .padding(1.dp)
                .fillMaxHeight()
                .fillMaxWidth(safeProgress)
                .clip(inner)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(JarvisBlue, JarvisTextSecondary, JarvisBlue)
                    ),
                    shape = inner
                )
        )
    }
}

@Composable
private fun bootSpin(): Float {
    val transition = rememberInfiniteTransition(label = "rotation")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    return angle
}
