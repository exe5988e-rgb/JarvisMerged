package com.jarvismini.ui.theme

import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Motion tokens (motion-compose skill). Every duration, easing and spring in the UI
 * comes from here; no inline tween(300) / spring(stiffness = ..) in screens.
 */
object JarvisMotion {
    object Duration {
        const val Instant = 90
        const val Fast = 160
        const val Normal = 240
        const val Slow = 400
    }

    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)   // enter / move
    val Exit = CubicBezierEasing(0.4f, 0f, 1f, 1f)       // leave

    object Distance {
        val Small = 8.dp
        val Medium = 16.dp
        val Large = 32.dp
    }

    object Scale {
        const val Press = 0.96f
        const val Hover = 1.02f
    }

    fun <T> snappy(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.87f, stiffness = 300f)
    fun <T> gentle(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.64f, stiffness = 120f)
    fun <T> bouncy(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.25f, stiffness = 400f)
    fun <T> instant(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.71f, stiffness = 600f)
    fun <T> release(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.71f, stiffness = 200f)
}

/**
 * Reduced-motion gate. False when the user turned animations off (animator scale 0)
 * or battery saver is on: callers must drop decorative loops and make transitions instant.
 */
@Composable
fun rememberMotionEnabled(): Boolean {
    val ctx = LocalContext.current
    return remember { isMotionEnabled(ctx) }
}

fun isMotionEnabled(ctx: Context): Boolean {
    val scale = try {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    } catch (_: Throwable) {
        1f
    }
    val saver = try {
        (ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
    } catch (_: Throwable) {
        false
    }
    return scale != 0f && !saver
}

/** whileTap equivalent: scales to 0.96 on press using the instant spring. Feedback < 90 ms. */
@Composable
fun Modifier.pressScale(interactionSource: MutableInteractionSource): Modifier {
    val enabled = rememberMotionEnabled()
    val pressed by interactionSource.collectIsPressedAsState()
    val s by animateFloatAsState(
        targetValue = if (pressed && enabled) JarvisMotion.Scale.Press else 1f,
        animationSpec = JarvisMotion.instant(),
        label = "pressScale",
    )
    return this.graphicsLayer {
        scaleX = s
        scaleY = s
    }
}
