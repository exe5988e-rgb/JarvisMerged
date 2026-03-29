package com.jarvismini.ui.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvismini.core.voice.VoiceModule
import com.jarvismini.core.voice.VoiceTriggerManager

// ── Colour tokens (kept identical to original) ──────────────────────────────
private val BgDark  = Color(0xFF0A0E1A)
private val BgCard  = Color(0xFF111827)
private val Cyan    = Color(0xFF00D4FF)
private val DimCyan = Color(0xFF00A8CC)
private val Green   = Color(0xFF00FF88)
private val Red     = Color(0xFFFF4444)
private val White70 = Color(0xB3FFFFFF)
private val White40 = Color(0x66FFFFFF)

// ── Data models ──────────────────────────────────────────────────────────────
private data class QuickAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)
private data class StatusItem(val label: String, val value: String, val ok: Boolean)

// ── Screen ───────────────────────────────────────────────────────────────────
@Composable
fun EnhancedHomeScreen(
    onNavigateToChat:          () -> Unit = {},
    onNavigateToAgent:         () -> Unit = {},
    onNavigateToCalendar:      () -> Unit = {},
    // ↓ these four were missing — added to fix the build
    onNavigateToChecklist:     () -> Unit = {},
    onNavigateToSettings:      () -> Unit = {},
    onNavigateToDebug:         () -> Unit = {},
    onNavigateToTermuxCommand: () -> Unit = {},
) {
    val context     = LocalContext.current
    val voiceState  by VoiceTriggerManager.state.collectAsState()

    val isListening  = voiceState == VoiceTriggerManager.VoiceState.ACTIVE_LISTENING
    val isProcessing = voiceState == VoiceTriggerManager.VoiceState.PROCESSING
    val wakeActive   = voiceState == VoiceTriggerManager.VoiceState.WAKE_LISTENING

    LaunchedEffect(Unit) {
        VoiceTriggerManager.init(context, VoiceModule)
        VoiceTriggerManager.startWakeWord()
    }

    val pulse = rememberInfiniteTransition(label = "orbPulse")
    val pulseAlpha by pulse.animateFloat(
        0.4f, 1f,
        infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseAlpha"
    )
    val pulseScale by pulse.animateFloat(
        0.95f, 1.05f,
        infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseScale"
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))

            // ── Header ────────────────────────────────────────────────────
            Text(
                "J.A.R.V.I.S",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Cyan,
                letterSpacing = 6.sp
            )
            Text(
                "MINI  //  SYSTEM ONLINE",
                fontSize = 10.sp,
                color = White40,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 3.sp
            )

            Spacer(Modifier.height(32.dp))

            // ── Central orb ───────────────────────────────────────────────
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(160.dp)
                    .scale(if (isListening || isProcessing) pulseScale else 1f)
            ) {
                Box(
                    Modifier
                        .size(160.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    Cyan.copy(alpha = if (isListening) pulseAlpha else 0.15f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                Box(
                    Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(BgCard)
                        .border(
                            1.5.dp,
                            if (isListening) Cyan else if (wakeActive) Green else DimCyan.copy(0.5f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isListening) Icons.Default.Mic
                        else if (isProcessing) Icons.Default.HourglassTop
                        else Icons.Default.Mic,
                        contentDescription = null,
                        tint = if (isListening) Cyan else White70,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                when {
                    isListening  -> "LISTENING …"
                    isProcessing -> "PROCESSING …"
                    wakeActive   -> "WAKE WORD ACTIVE"
                    else         -> "SAY  \"HEY JARVIS\""
                },
                fontSize = 12.sp,
                color = if (isListening) Cyan else White40,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )

            Spacer(Modifier.height(32.dp))

            // ── Quick actions ─────────────────────────────────────────────
            Text(
                "QUICK ACCESS",
                fontSize = 10.sp,
                color = White40,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 3.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Tasks → Checklist, Terminal → TermuxCommand now wired
                items(
                    quickActions(
                        onChat      = onNavigateToChat,
                        onAgent     = onNavigateToAgent,
                        onCalendar  = onNavigateToCalendar,
                        onChecklist = onNavigateToChecklist,
                        onTermux    = onNavigateToTermuxCommand,
                    )
                ) { action ->
                    QuickActionCard(action)
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── System status ─────────────────────────────────────────────
            Text(
                "SYSTEM STATUS",
                fontSize = 10.sp,
                color = White40,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 3.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
            systemStatusItems(voiceState).forEach { item ->
                StatusRow(item)
                Spacer(Modifier.height(4.dp))
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────
private fun quickActions(
    onChat:      () -> Unit,
    onAgent:     () -> Unit,
    onCalendar:  () -> Unit,
    onChecklist: () -> Unit,
    onTermux:    () -> Unit,
) = listOf(
    QuickAction("Chat",     Icons.Default.Chat,          onChat),
    QuickAction("Calendar", Icons.Default.CalendarToday, onCalendar),
    QuickAction("Tasks",    Icons.Default.CheckCircle,   onChecklist),   // was {}
    QuickAction("Terminal", Icons.Default.Terminal,      onTermux),      // was {}
    QuickAction("Agent",    Icons.Default.SmartToy,      onAgent),
)

@Composable
private fun QuickActionCard(action: QuickAction) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .size(90.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(0.5.dp, DimCyan.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .clickable { action.onClick() }
            .padding(8.dp)
    ) {
        Icon(action.icon, null, tint = Cyan, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(6.dp))
        Text(
            action.label,
            fontSize = 10.sp,
            color = White70,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )
    }
}

private fun systemStatusItems(voiceState: VoiceTriggerManager.VoiceState) = listOf(
    StatusItem("Neural Networks",   "ONLINE",   true),
    StatusItem(
        "Voice Recognition",
        when (voiceState) {
            VoiceTriggerManager.VoiceState.ACTIVE_LISTENING -> "LISTENING"
            VoiceTriggerManager.VoiceState.WAKE_LISTENING   -> "WATCHING"
            VoiceTriggerManager.VoiceState.PROCESSING       -> "THINKING"
            else                                             -> "STANDBY"
        },
        voiceState != VoiceTriggerManager.VoiceState.IDLE
    ),
    StatusItem("Automation Engine", "READY",    true),
    StatusItem("Bridge Service",    "ACTIVE",   true),
)

@Composable
private fun StatusRow(item: StatusItem) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(BgCard)
            .border(0.5.dp, DimCyan.copy(0.2f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (item.ok) Green else Red)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            item.label,
            fontSize = 11.sp,
            color = White70,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )
        Text(
            item.value,
            fontSize = 11.sp,
            color = if (item.ok) Green else Red,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )
    }
}
