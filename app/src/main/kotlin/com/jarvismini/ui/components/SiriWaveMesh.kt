package com.jarvismini.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.jarvismini.ui.theme.JarvisCyan
import com.jarvismini.ui.theme.JarvisMotion
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

private const val WAVE_LINES = 9
private const val WAVE_STEPS = 64
private const val WAVE_WRAP = 600f // divisible by the 2.0 s cycle used below
private const val TAU = 6.2831855f

/**
 * Siri-style wave mesh: a stack of interlaced sine ribbons that swell while the mic is live,
 * clipped to the orb. Decorative; the parent keeps the button semantics.
 *
 * Compose it only while listening: it fades in on entry, and with [motionEnabled] = false it
 * draws one static, fully visible mesh (reduced motion). The phase is read only inside the draw
 * scope, so nothing recomposes per frame.
 */
@Composable
fun SiriWaveMesh(
    motionEnabled: Boolean,
    modifier: Modifier = Modifier,
    color: Color = JarvisCyan,
    deep: Color = Color(0xFF0080FF),
    accent: Color = Color(0xFFB57BFF),
) {
    val reveal = remember { Animatable(if (motionEnabled) 0f else 1f) }
    LaunchedEffect(motionEnabled) {
        reveal.animateTo(1f, JarvisMotion.gentle())
    }

    var clock by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(motionEnabled) {
        if (!motionEnabled) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) clock = (clock + min((now - last) / 1_000_000_000f, 0.05f)) % WAVE_WRAP
                last = now
            }
        }
    }

    val path = remember { Path() }

    Canvas(modifier = modifier) {
        val r = reveal.value.coerceIn(0f, 1f)
        if (r <= 0.01f) return@Canvas
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val radius = min(w, h) / 2f * 0.70f
        val unit = 1.dp.toPx()
        val t = if (motionEnabled) clock else 1.3f
        val breathe = if (motionEnabled) 0.78f + 0.22f * sin(t * (TAU / 2f)) else 1f

        val clip = Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(Offset(cx, cy), radius * 0.98f))
        }
        clipPath(clip) {
            for (line in 0 until WAVE_LINES) {
                val f = line / (WAVE_LINES - 1f)           // 0..1 across the stack
                val mid = 1f - kotlin.math.abs(f - 0.5f) * 2f // 1 in the centre, 0 at the edges
                val phase = line * 0.62f
                val speed = 1.1f + 0.35f * line
                val amp = radius * (0.10f + 0.42f * mid) * breathe * r
                path.rewind()
                for (s in 0..WAVE_STEPS) {
                    val u = s / WAVE_STEPS.toFloat()        // 0..1 along the width
                    // taper to zero at both ends so ribbons pinch like Siri's
                    val env = sin(u * PI.toFloat())
                    val env2 = env * env
                    val y = cy + (f - 0.5f) * radius * 0.30f +
                        amp * env2 * (
                            0.62f * sin(u * TAU * 1.6f + t * speed + phase) +
                                0.38f * sin(u * TAU * 2.7f - t * speed * 0.7f + phase * 1.9f)
                            )
                    val x = cx - radius + u * radius * 2f
                    if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                val tint = lerp(deep, if (line % 3 == 0) accent else color, mid)
                drawPath(
                    path = path,
                    color = tint.copy(alpha = (0.18f + 0.55f * mid) * r),
                    style = Stroke(
                        width = unit * (0.8f + 1.0f * mid),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
        }
    }
}
