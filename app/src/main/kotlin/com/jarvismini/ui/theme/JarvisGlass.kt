package com.jarvismini.ui.theme

import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Corner radii shared by glass surfaces. */
object JarvisRadius {
    val Small = 8.dp
    val Card = 12.dp
    val Large = 20.dp
}

/** Text colours verified for >= 4.5:1 on the glass tint over JarvisBackground. */
val JarvisTextPrimary = Color(0xFFD8F8FF)
val JarvisTextSecondary = Color(0xFF8FE9FA)
val JarvisOnline = Color(0xFF00FF88)
val JarvisOffline = Color(0xFFFF6680)

/** Real backdrop blur needs API 31+ and a library (Haze); until added, every API uses the tint path. */
val canBlurBackdrop: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
private fun rememberHighContrast(): Boolean {
    val ctx = LocalContext.current
    return remember {
        try {
            Settings.Secure.getInt(ctx.contentResolver, "high_text_contrast_enabled", 0) == 1
        } catch (_: Throwable) {
            false
        }
    }
}

/**
 * HUD glass surface (glass-compose skill): translucent cyan tint over navy, gradient edge
 * (brighter on top), optional corner ticks. No backdrop blur yet, so tint alpha stays high
 * enough to keep text readable on the particle field. Solid fallback when the system
 * high-contrast-text setting is on. Do not use for dense lists or log views.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(JarvisRadius.Card),
    tint: Color = JarvisCyan,
    cornerTicks: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val highContrast = rememberHighContrast()
    val fill = if (highContrast) {
        JarvisBackground
    } else {
        tint.copy(alpha = 0.12f).compositeOver(JarvisBackground.copy(alpha = 0.86f))
    }
    val edge = if (highContrast) {
        Brush.linearGradient(listOf(tint, tint))
    } else {
        Brush.linearGradient(listOf(tint.copy(alpha = 0.70f), tint.copy(alpha = 0.12f), tint.copy(alpha = 0.35f)))
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(fill)
            .border(1.dp, edge, shape)
            .then(if (cornerTicks) Modifier.hudCornerTicks(tint.copy(alpha = 0.9f)) else Modifier),
        content = content,
    )
}

/** 1dp-stroke L-shaped ticks in the four corners for the HUD look. */
fun Modifier.hudCornerTicks(color: Color, length: Dp = 10.dp, stroke: Dp = 1.5.dp): Modifier =
    drawBehind {
        val l = length.toPx()
        val s = stroke.toPx()
        val w = size.width
        val h = size.height
        fun tick(a: Offset, b: Offset, c: Offset) {
            drawLine(color, a, b, s)
            drawLine(color, b, c, s)
        }
        tick(Offset(0f, l), Offset(0f, 0f), Offset(l, 0f))
        tick(Offset(w - l, 0f), Offset(w, 0f), Offset(w, l))
        tick(Offset(0f, h - l), Offset(0f, h), Offset(l, h))
        tick(Offset(w - l, h), Offset(w, h), Offset(w, h - l))
    }
