package com.jarvismini.ui.components

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.jarvismini.ui.theme.JarvisCyan
import com.jarvismini.ui.theme.JarvisMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// Back-of-sphere colour (no theme token for the deeper azure yet; same value the old orb used).
private val SphereDeep = Color(0xFF0080FF)

private const val TILT = 0.38f
private val COS_TILT = cos(TILT)
private val SIN_TILT = sin(TILT)
private const val TWO_PI = 6.2831855f
private const val CLOCK_WRAP = 600f // divisible by every period used below (1.25 / 1.6 / 2.5 s)

private class SphereMesh(
    val x: FloatArray,
    val y: FloatArray,
    val z: FloatArray,
    val edgeA: IntArray,
    val edgeB: IntArray,
    val edgeW: FloatArray,
)

/** Fibonacci shell + a few inner nodes, joined to their nearest neighbours plus faint long chords. */
private fun buildMesh(shellPoints: Int = 210, corePoints: Int = 44): SphereMesh {
    val rnd = Random(7)
    val n = shellPoints + corePoints
    val x = FloatArray(n)
    val y = FloatArray(n)
    val z = FloatArray(n)
    val golden = (PI * (3.0 - sqrt(5.0))).toFloat()

    for (i in 0 until shellPoints) {
        val t = (i + 0.5f) / shellPoints
        val yy = 1f - 2f * t
        val rr = sqrt(max(0f, 1f - yy * yy))
        val th = golden * i
        val jitter = 0.9f + rnd.nextFloat() * 0.1f
        x[i] = cos(th) * rr * jitter
        y[i] = yy * jitter
        z[i] = sin(th) * rr * jitter
    }
    for (k in 0 until corePoints) {
        val i = shellPoints + k
        var dx = 0f
        var dy = 0f
        var dz = 0f
        var d2 = 0f
        do {
            dx = rnd.nextFloat() * 2f - 1f
            dy = rnd.nextFloat() * 2f - 1f
            dz = rnd.nextFloat() * 2f - 1f
            d2 = dx * dx + dy * dy + dz * dz
        } while (d2 > 1f || d2 < 0.04f)
        x[i] = dx * 0.75f
        y[i] = dy * 0.75f
        z[i] = dz * 0.75f
    }

    val seen = HashSet<Int>()
    val ea = ArrayList<Int>()
    val eb = ArrayList<Int>()
    val ew = ArrayList<Float>()
    fun add(a: Int, b: Int, w: Float) {
        val lo = min(a, b)
        val hi = max(a, b)
        if (lo == hi) return
        if (seen.add(lo * 1000 + hi)) {
            ea.add(lo)
            eb.add(hi)
            ew.add(w)
        }
    }

    for (i in 0 until n) {
        val k = if (i < shellPoints) 5 else 4
        val near = (0 until n)
            .filter { it != i }
            .map { j ->
                val dx = x[i] - x[j]
                val dy = y[i] - y[j]
                val dz = z[i] - z[j]
                j to (dx * dx + dy * dy + dz * dz)
            }
            .filter { it.second < 0.5625f }
            .sortedBy { it.second }
            .take(k)
        near.forEach { add(i, it.first, 1f) }
    }

    var made = 0
    var tries = 0
    while (made < 64 && tries < 4000) {
        tries++
        val a = rnd.nextInt(shellPoints)
        val b = rnd.nextInt(shellPoints)
        if (a == b) continue
        val dx = x[a] - x[b]
        val dy = y[a] - y[b]
        val dz = z[a] - z[b]
        val d2 = dx * dx + dy * dy + dz * dz
        if (d2 > 0.8f && d2 < 2.4f) {
            val before = ea.size
            add(a, b, 0.4f)
            if (ea.size > before) made++
        }
    }

    return SphereMesh(x, y, z, ea.toIntArray(), eb.toIntArray(), ew.toFloatArray())
}

/**
 * Glowing 3D plexus sphere (dots + thin lines, depth-faded) for the home voice orb.
 *
 * Decorative: the parent carries the button / state semantics. The phase is driven by
 * withFrameNanos and only read inside the draw scope, so it never recomposes per frame.
 * [motionEnabled] = false renders one static, fully visible sphere (reduced motion).
 */
@Composable
fun PlexusSphere(
    listening: Boolean,
    motionEnabled: Boolean,
    modifier: Modifier = Modifier,
    color: Color = JarvisCyan,
    deep: Color = SphereDeep,
) {
    val mesh = remember { buildMesh() }
    val n = mesh.x.size
    val sx = remember { FloatArray(n) }
    val sy = remember { FloatArray(n) }
    val sz = remember { FloatArray(n) }
    val sp = remember { FloatArray(n) }

    val spin by animateFloatAsState(
        targetValue = if (listening) 52f else 11f,
        animationSpec = JarvisMotion.gentle(),
        label = "sphereSpin",
    )
    val energy by animateFloatAsState(
        targetValue = if (listening) 1f else 0f,
        animationSpec = JarvisMotion.gentle(),
        label = "sphereEnergy",
    )

    var yaw by remember { mutableFloatStateOf(28f) }
    var clock by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(motionEnabled) {
        if (!motionEnabled) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = min((now - last) / 1_000_000_000f, 0.05f)
                    yaw = (yaw + spin * dt) % 360f
                    clock = (clock + dt) % CLOCK_WRAP
                }
                last = now
            }
        }
    }

    Canvas(modifier = modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val e = energy.coerceIn(0f, 1f)
        val unit = 1.dp.toPx()
        val pulse = if (motionEnabled) e * 0.035f * sin(clock * (TWO_PI / 1.25f)) else 0f
        val radius = min(size.width, size.height) / 2f * 0.70f * (1f + pulse)

        val yawR = yaw * (PI.toFloat() / 180f)
        val cosY = cos(yawR)
        val sinY = sin(yawR)
        for (i in 0 until n) {
            val x1 = mesh.x[i] * cosY + mesh.z[i] * sinY
            val z1 = -mesh.x[i] * sinY + mesh.z[i] * cosY
            val y2 = mesh.y[i] * COS_TILT - z1 * SIN_TILT
            val z2 = mesh.y[i] * SIN_TILT + z1 * COS_TILT
            val persp = 1f + z2 * 0.16f
            sx[i] = c.x + x1 * radius * persp
            sy[i] = c.y + y2 * radius * persp
            sz[i] = z2
            sp[i] = persp
        }

        // soft glow behind the sphere
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(deep.copy(alpha = 0.16f + 0.14f * e), Color.Transparent),
                center = c,
                radius = radius * 1.35f,
            ),
            radius = radius * 1.35f,
            center = c,
        )
        // silhouette
        drawCircle(
            color = color.copy(alpha = 0.10f + 0.10f * e),
            radius = radius * 1.02f,
            center = c,
            style = Stroke(width = unit),
        )

        // radial rays: short bursts around the silhouette, every 4th one long and bright
        val rayCount = 96
        for (r in 0 until rayCount) {
            val ang = r * (TWO_PI / rayCount) + clock * 0.02f
            val wob = if (motionEnabled) 0.5f + 0.5f * sin(clock * (TWO_PI / 2f) + r * 2.399f) else 0.5f
            val long = r % 4 == 0
            val len = radius * (0.05f + 0.14f * wob * (0.4f + 0.6f * e) + if (long) 0.12f else 0f)
            val dx = cos(ang)
            val dy = sin(ang)
            val inner = radius * 1.05f
            drawLine(
                color = color.copy(alpha = ((if (long) 0.22f else 0.10f) + 0.26f * wob * (0.5f + 0.5f * e)).coerceIn(0f, 1f)),
                start = Offset(c.x + dx * inner, c.y + dy * inner),
                end = Offset(c.x + dx * (inner + len), c.y + dy * (inner + len)),
                strokeWidth = unit * (if (long) 0.9f else 0.6f),
            )
        }

        // web
        for (k in mesh.edgeA.indices) {
            val a = mesh.edgeA[k]
            val b = mesh.edgeB[k]
            val d = ((sz[a] + sz[b]) * 0.25f + 0.5f).coerceIn(0f, 1f)
            val alpha = mesh.edgeW[k] * (0.10f + 0.90f * d * d) * (0.70f + 0.30f * e)
            drawLine(
                color = lerp(deep, color, d).copy(alpha = alpha.coerceIn(0f, 1f)),
                start = Offset(sx[a], sy[a]),
                end = Offset(sx[b], sy[b]),
                strokeWidth = unit * 0.6f * (0.7f + 0.9f * d),
            )
        }

        // nodes
        for (i in 0 until n) {
            val d = (sz[i] * 0.5f + 0.5f).coerceIn(0f, 1f)
            val twinkle = if (motionEnabled) {
                0.78f + 0.22f * sin(clock * (TWO_PI / 2.5f) + i * 1.7f)
            } else {
                1f
            }
            val alpha = (0.22f + 0.78f * d) * twinkle * (0.8f + 0.2f * e)
            drawCircle(
                color = lerp(deep, color, d).copy(alpha = alpha.coerceIn(0f, 1f)),
                radius = unit * (0.9f + 1.5f * d) * sp[i],
                center = Offset(sx[i], sy[i]),
            )
        }

        // radiating waves: three staggered rings, faint at idle, strong while listening
        if (motionEnabled) {
            for (w in 0 until 3) {
                val t = ((clock / 2.5f) + w / 3f) % 1f
                val fade = (1f - t) * (1f - t)
                drawCircle(
                    color = color.copy(alpha = (fade * (0.10f + 0.30f * e)).coerceIn(0f, 1f)),
                    radius = radius * (1.06f + 0.30f * t),
                    center = c,
                    style = Stroke(width = (1.0f + 0.8f * e) * unit),
                )
            }
        }
    }
}
