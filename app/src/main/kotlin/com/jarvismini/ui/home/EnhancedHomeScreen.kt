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
                brush = Brush.radialGradient(
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

            // ===== HEADER =====
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text          = "J.A.R.V.I.S.",
                    fontSize      = 32.sp,
                    fontWeight    = FontWeight.Light,
                    letterSpacing = 6.sp,
                    color         = Color(0xFF00E0FF)
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

            // ===== CORE ORB =====
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier
                    .size(200.dp)
                    .clickable { isListening = !isListening }
            ) {
                // Outer ring
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .alpha(if (isListening) pulseAlpha else 0.3f)
                        .background(Color(0xFF00E0FF).copy(alpha = 0.1f), CircleShape)
                        .border(
                            2.dp,
                            Color(0xFF00E0FF).copy(alpha = if (isListening) pulseAlpha else 0.3f),
                            CircleShape
                        )
                )
                // Mid ring
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .background(Color(0xFF00E0FF).copy(alpha = 0.05f), CircleShape)
                        .border(1.dp, Color(0xFF00E0FF).copy(alpha = 0.4f), CircleShape)
                )
                // Inner orb with mic icon
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .shadow(
                            elevation  = if (isListening) 30.dp else 10.dp,
                            shape      = CircleShape,
                            spotColor  = Color(0xFF00E0FF)
                        )
                        .background(
                            brush = Brush.radialGradient(
                                listOf(
                                    Color(0xFF00E0FF).copy(alpha = if (isListening) 0.8f else 0.5f),
                                    Color(0xFF0080FF).copy(alpha = if (isListening) 0.4f else 0.2f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector        = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Voice",
                        tint               = Color(0xFF00E0FF),
                        modifier           = Modifier
                            .size(40.dp)
                            .align(Alignment.Center)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text       = if (isListening) "LISTENING..." else "TAP TO ACTIVATE",
                fontSize   = 14.sp,
                fontFamily = FontFamily.Monospace,
                color      = Color(0xFF00E0FF).copy(alpha = 0.8f)
            )

            Spacer(Modifier.height(40.dp))

            // ===== QUICK ACCESS =====
            Text(
                text          = "QUICK ACCESS",
                fontSize      = 12.sp,
                fontFamily    = FontFamily.Monospace,
                letterSpacing = 2.sp,
                color         = Color(0xFF00E0FF).copy(alpha = 0.6f)
            )

            Spacer(Modifier.height(16.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                item { QuickActionCard("Chat",     Icons.Default.Chat,         onNavigateToChat) }
                item { QuickActionCard("Calendar", Icons.Default.CalendarToday, onNavigateToCalendar) }
                item { QuickActionCard("Tasks",    Icons.Default.Checklist,    onNavigateToChecklist) }
                item { QuickActionCard("Terminal", Icons.Default.Terminal,     onNavigateToTermuxCommand) }
                item { QuickActionCard("Agent",    Icons.Default.SmartToy,     onNavigateToAgent) }
            }

            Spacer(Modifier.height(40.dp))

            // ===== SYSTEM STATUS =====
            Text(
                text          = "SYSTEM STATUS",
                fontSize      = 12.sp,
                fontFamily    = FontFamily.Monospace,
                letterSpacing = 2.sp,
                color         = Color(0xFF00E0FF).copy(alpha = 0.6f)
            )

            Spacer(Modifier.height(16.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { StatusCard("Neural Networks",    "Online",  true) }
                item { StatusCard("Voice Recognition",  "Active",  true) }
                item { StatusCard("Automation Engine",  "Standby", false) }
                item { StatusCard("Local LLM",          "Ready",   true) }
                item { StatusCard("Agent Server",       "Standby", false) }
            }
        }
    }
}

// ─── Local components ─────────────────────────────────────────────────────────

@Composable
private fun QuickActionCard(
    title:   String,
    icon:    ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(100.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .border(
                    width = 1.dp,
                    color = Color(0xFF00E0FF).copy(alpha = 0.4f),
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector        = icon,
                    contentDescription = title,
                    tint               = Color(0xFF00E0FF),
                    modifier           = Modifier.size(32.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text       = title,
                    fontSize   = 12.sp,
                    color      = Color(0xFF00E0FF).copy(alpha = 0.8f),
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun StatusCard(
    label:    String,
    status:   String,
    isOnline: Boolean,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color.Black.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = 1.dp,
                color = Color(0xFF00E0FF).copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(16.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                text     = label,
                fontSize = 14.sp,
                color    = Color(0xFF00E0FF).copy(alpha = 0.8f)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            color = if (isOnline) Color(0xFF00FF00) else Color(0xFFFFAA00),
                            shape = CircleShape
                        )
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text       = status,
                    fontSize   = 12.sp,
                    color      = Color(0xFF00E0FF).copy(alpha = 0.6f),
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
