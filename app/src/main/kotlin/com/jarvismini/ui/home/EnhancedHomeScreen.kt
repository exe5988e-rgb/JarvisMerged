package com.jarvismini.ui.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvismini.ui.components.*

@Composable
fun EnhancedHomeScreen(
    onNavigateToChat:          () -> Unit,
    onNavigateToCalendar:      () -> Unit,
    onNavigateToChecklist:     () -> Unit,
    onNavigateToSettings:      () -> Unit,
    onNavigateToDebug:         () -> Unit,
    onNavigateToTermuxCommand: () -> Unit,
    onNavigateToAgent:         () -> Unit,
) {
    var isListening by remember { mutableStateOf(false) }

    val pulseAnimation = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by pulseAnimation.animateFloat(
        initialValue = 0.3f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color.Black, Color(0xFF001520), Color.Black)
                )
            )
    ) {
        GridBackground()
        ParticleField()
        ScanLine()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ── Header ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text         = "J.A.R.V.I.S.",
                    fontSize     = 32.sp,
                    fontWeight   = FontWeight.Light,
                    letterSpacing = 6.sp,
                    color        = Color(0xFF00E0FF)
                )
                Row {
                    IconButton(onClick = onNavigateToDebug) {
                        Icon(Icons.Default.BugReport, "Debug", tint = Color(0xFF00E0FF))
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "Settings", tint = Color(0xFF00E0FF))
                    }
                }
            }

            Spacer(Modifier.height(40.dp))

            // ── Core orb ──
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier
                    .size(200.dp)
                    .clickable { isListening = !isListening }
            ) {
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .alpha(if (isListening) pulseAlpha else 0.3f)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF00E0FF).copy(alpha = 0.2f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )
                HologramCanvas()
                Text(
                    text       = if (isListening) "LISTENING" else "TAP TO WAKE",
                    fontSize   = 11.sp,
                    letterSpacing = 2.sp,
                    color      = Color(0xFF00E0FF).copy(alpha = if (isListening) 1f else 0.5f),
                    fontFamily = FontFamily.Monospace,
                    modifier   = Modifier.align(Alignment.BottomCenter)
                )
            }

            Spacer(Modifier.height(40.dp))

            // ── Feature grid ──
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier            = Modifier.fillMaxWidth()
            ) {
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HomeFeatureCard(
                            icon    = Icons.Default.Chat,
                            label   = "CHAT",
                            onClick = onNavigateToChat,
                            modifier = Modifier.weight(1f)
                        )
                        HomeFeatureCard(
                            icon    = Icons.Default.CalendarToday,
                            label   = "CALENDAR",
                            onClick = onNavigateToCalendar,
                            modifier = Modifier.weight(1f)
                        )
                        HomeFeatureCard(
                            icon    = Icons.Default.Checklist,
                            label   = "TASKS",
                            onClick = onNavigateToChecklist,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HomeFeatureCard(
                            icon    = Icons.Default.Terminal,
                            label   = "TERMUX",
                            onClick = onNavigateToTermuxCommand,
                            modifier = Modifier.weight(1f)
                        )
                        // ── AGENT card — highlighted ──
                        HomeAgentCard(
                            onClick  = onNavigateToAgent,
                            modifier = Modifier.weight(2f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeFeatureCard(
    icon:     ImageVector,
    label:    String,
    onClick:  () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(72.dp)
            .background(
                Color(0xFF0A1A22),
                RoundedCornerShape(10.dp)
            )
            .border(0.5.dp, Color(0xFF00E0FF).copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = Color(0xFF00E0FF), modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                fontSize     = 9.sp,
                letterSpacing = 1.sp,
                color        = Color(0xFF00E0FF),
                fontFamily   = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun HomeAgentCard(
    onClick:  () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pulse = rememberInfiniteTransition(label = "agentPulse")
    val glow by pulse.animateFloat(
        initialValue  = 0.3f,
        targetValue   = 0.8f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "agentGlow"
    )
    Box(
        modifier = modifier
            .height(72.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF001A2A), Color(0xFF003344))
                ),
                RoundedCornerShape(10.dp)
            )
            .border(
                1.dp,
                Color(0xFF00E0FF).copy(alpha = glow),
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment      = Alignment.CenterVertically,
            horizontalArrangement  = Arrangement.Center,
        ) {
            Icon(
                Icons.Default.SmartToy,
                null,
                tint     = Color(0xFF00E0FF),
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    "AGENT",
                    fontSize     = 14.sp,
                    fontWeight   = FontWeight.Bold,
                    letterSpacing = 3.sp,
                    color        = Color(0xFF00E0FF),
                    fontFamily   = FontFamily.Monospace,
                )
                Text(
                    "Run tasks on device",
                    fontSize   = 9.sp,
                    color      = Color(0xFF00E0FF).copy(alpha = 0.6f),
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
