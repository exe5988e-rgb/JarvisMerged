package com.jarvismini.ui.timer

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvismini.ui.theme.JarvisBackground
import com.jarvismini.ui.theme.JarvisBlue
import com.jarvismini.ui.theme.JarvisMotion
import com.jarvismini.ui.theme.JarvisOffline
import com.jarvismini.ui.theme.JarvisOnline
import com.jarvismini.ui.theme.JarvisRadius
import com.jarvismini.ui.theme.JarvisTextSecondary
import com.jarvismini.ui.theme.rememberMotionEnabled

private const val LOW_TIME_SECONDS = 120L

/**
 * Live countdown for task cards. Dense list surface, so flat tint rather than glass.
 * Low time is conveyed by label text ("LOW TIME") and colour, never colour alone; the
 * border pulse is dropped under reduced motion. The whole card is one TalkBack node with
 * a spoken duration and progress; only the low-time label is a live region, so the
 * per-second tick does not spam announcements.
 */
@Composable
fun TaskTimerDisplay(
    remainingSeconds: Long,
    totalSeconds: Long
) {
    val remaining = remainingSeconds.coerceAtLeast(0L)
    val progress = if (totalSeconds > 0L) {
        (1f - remaining.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val isLowTime = remaining < LOW_TIME_SECONDS

    val motionEnabled = rememberMotionEnabled()
    val infiniteTransition = rememberInfiniteTransition(label = "lowTimePulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(JarvisMotion.Duration.Slow * 2, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    val borderAlpha = if (isLowTime && motionEnabled) pulse else 0.7f

    val accent = if (isLowTime) JarvisOffline else JarvisOnline
    val shape = RoundedCornerShape(JarvisRadius.Card)
    val spoken = spokenDuration(remaining)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(JarvisBackground, shape)
            .border(
                width = 1.dp,
                color = if (isLowTime) accent.copy(alpha = borderAlpha) else JarvisBlue.copy(alpha = 0.5f),
                shape = shape
            )
            .padding(12.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "Task timer, $spoken remaining" +
                    if (isLowTime) ", low time" else ""
                progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isLowTime) "TIMER · LOW TIME" else "TIMER ACTIVE",
                fontSize = 12.sp,
                color = if (isLowTime) accent else JarvisTextSecondary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )

            Text(
                text = formatTime(remaining),
                fontSize = 18.sp,
                color = accent,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                style = TextStyle(fontFeatureSettings = "tnum")
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = accent,
            trackColor = JarvisBlue.copy(alpha = 0.18f),
        )
    }
}

private fun spokenDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    val parts = buildList {
        if (h > 0) add("$h ${if (h == 1L) "hour" else "hours"}")
        if (m > 0) add("$m ${if (m == 1L) "minute" else "minutes"}")
        if (s > 0 || isEmpty()) add("$s ${if (s == 1L) "second" else "seconds"}")
    }
    return parts.joinToString(" ")
}

private fun formatTime(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format("%02d:%02d", minutes, secs)
    }
}
