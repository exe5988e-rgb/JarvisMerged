package com.jarvismini.voice

/**
 * VoiceTriggerButton.kt
 *
 * Drop-in Compose component.
 * Add to JarvisChatScreen.kt or EnhancedHomeScreen.kt.
 *
 * Usage:
 *   VoiceTriggerButton()
 *
 * Shows a mic button that:
 *   - Starts VoiceCommandService on first press (always-on listening)
 *   - Manually triggers a listen cycle if service already running
 *   - Shows pulsing animation while listening
 *   - Shows result text for 3 seconds after completion
 */

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Jarvis blue — matches JarvisColors.kt in your app
private val JarvisBlue = Color(0xFF00D4FF)
private val JarvisDark = Color(0xFF001520)

@Composable
fun VoiceTriggerButton(
    modifier: Modifier = Modifier,
    isListening: Boolean = false,
    onToggle: ((Context) -> Unit)? = null
) {
    val context = LocalContext.current
    var active  by remember { mutableStateOf(false) }

    // Pulse animation when listening
    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = if (active) 1.15f else 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        // ── Mic button ────────────────────────────────────────────────────
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(72.dp)
                .scale(scale)
        ) {
            // Outer ring (glow effect)
            if (active) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(JarvisBlue.copy(alpha = 0.15f), CircleShape)
                        .border(1.dp, JarvisBlue.copy(alpha = 0.4f), CircleShape)
                )
            }

            // Main button
            FilledIconButton(
                onClick = {
                    active = !active
                    if (active) {
                        VoiceCommandService.start(context)
                    } else {
                        VoiceCommandService.stop(context)
                    }
                    onToggle?.invoke(context)
                },
                modifier = Modifier.size(60.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (active) JarvisBlue.copy(alpha = 0.2f)
                                     else JarvisDark,
                    contentColor   = if (active) JarvisBlue else Color.Gray
                )
            ) {
                Icon(
                    imageVector = if (active) Icons.Default.Mic else Icons.Default.MicOff,
                    contentDescription = if (active) "Stop listening" else "Start listening",
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // ── Status text ───────────────────────────────────────────────────
        Text(
            text = if (active) "LISTENING" else "TAP TO ACTIVATE",
            color = if (active) JarvisBlue else Color.Gray,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp
        )
    }
}

/**
 * Full voice panel — shows in chat screen above input field.
 * Replace the existing text input row or add above it.
 */
@Composable
fun VoicePanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var statusText by remember { mutableStateOf("Say a command after activating") }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF000D1A))
            .border(1.dp, JarvisBlue.copy(alpha = 0.2f))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Status text
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "VOICE CONTROL",
                color = JarvisBlue.copy(alpha = 0.6f),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = statusText,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        // Mic button
        VoiceTriggerButton()
    }
}
