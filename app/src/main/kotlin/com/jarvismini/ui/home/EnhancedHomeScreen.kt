package com.jarvismini.ui.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvismini.core.JarvisMode
import com.jarvismini.core.JarvisState
import com.jarvismini.ui.components.*
import kotlin.math.PI

@Composable
fun EnhancedHomeScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToDebug: () -> Unit
) {
    var isListening by remember { mutableStateOf(false) }
    var currentMode by remember { mutableStateOf(JarvisState.currentMode) }

    val infiniteRotation = rememberInfiniteTransition(label = "hologram")
    val rotation by infiniteRotation.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val pulse by infiniteRotation.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse"
    )

    val scanController = remember { Animatable(0f) }

    LaunchedEffect(isListening) {
        if (isListening) {
            scanController.animateTo(
                targetValue = (2 * PI).toFloat(),
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        } else {
            scanController.stop()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Black,
                        Color(0xFF001520),
                        Color.Black
                    )
                )
            )
    ) {
        // Grid background
        GridBackground()

        // Particle field
        ParticleField(progress = rotation)

        // Top bar
        TopBar(
            onSettingsClick = onNavigateToSettings,
            onDebugClick = onNavigateToDebug
        )

        // Main content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Hologram
            HologramCanvas(
                rotation = rotation,
                pulse = pulse,
                scanAngle = scanController.value,
                isScanning = isListening,
                modifier = Modifier.size(300.dp)
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = currentMode.name,
                fontSize = 32.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 8.sp,
                color = Color(0xFF00E0FF).copy(alpha = 0.9f)
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = if (isListening) "◉ LISTENING" else "○ STANDBY",
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF00E0FF).copy(alpha = 0.6f)
            )

            Spacer(Modifier.height(40.dp))

            // Voice button
            VoiceButton(
                isListening = isListening,
                onClick = { isListening = !isListening }
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = if (isListening) "Processing voice input..." else "Press to activate",
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF00E0FF).copy(alpha = 0.6f)
            )

            Spacer(Modifier.height(40.dp))

            // Mode selector
            ModeSelector(
                currentMode = currentMode,
                onModeChange = { newMode ->
                    currentMode = newMode
                    JarvisState.setMode(/* context needed */, newMode)
                }
            )
        }

        // Scan line effect
        ScanLine(progress = rotation)
    }
}

@Composable
fun TopBar(
    onSettingsClick: () -> Unit,
    onDebugClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        color = Color.Black.copy(alpha = 0.4f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .border(
                    width = 1.dp,
                    color = Color(0xFF00E0FF).copy(alpha = 0.3f),
                    shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row {
                Text(
                    text = "J.A.R.V.I.S.",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 4.sp,
                    color = Color(0xFF00E0FF)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "v3.1.MARK_VII",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF00E0FF).copy(alpha = 0.6f)
                )
            }

            Row {
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color(0xFF00E0FF)
                    )
                }
                IconButton(onClick = onDebugClick) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = "Debug",
                        tint = Color(0xFF00E0FF)
                    )
                }
            }
        }
    }
}

@Composable
fun VoiceButton(
    isListening: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .shadow(
                elevation = 30.dp,
                shape = CircleShape,
                spotColor = if (isListening) Color(0xFF00FF88) else Color(0xFF00E0FF)
            )
            .background(
                color = if (isListening)
                    Color(0xFF00FF88).copy(alpha = 0.2f)
                else
                    Color(0xFF00E0FF).copy(alpha = 0.1f),
                shape = CircleShape
            )
            .border(
                width = 2.dp,
                color = if (isListening) Color(0xFF00FF88) else Color(0xFF00E0FF),
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = "Voice Input",
            tint = if (isListening) Color(0xFF00FF88) else Color(0xFF00E0FF),
            modifier = Modifier.size(36.dp)
        )
    }
}

@Composable
fun ModeSelector(
    currentMode: JarvisMode,
    onModeChange: (JarvisMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .padding(horizontal = 40.dp)
            .fillMaxWidth()
    ) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.Black.copy(alpha = 0.7f)
            ),
            border = BorderStroke(1.dp, Color(0xFF00E0FF).copy(alpha = 0.4f))
        ) {
            Text(
                text = currentMode.name,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF00E0FF)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color.Black)
        ) {
            JarvisMode.values().forEach { mode ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = mode.name,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF00E0FF)
                        )
                    },
                    onClick = {
                        onModeChange(mode)
                        expanded = false
                    }
                )
            }
        }
    }
}
