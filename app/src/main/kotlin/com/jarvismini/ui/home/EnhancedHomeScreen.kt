package com.jarvismini.ui.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.jarvismini.ui.components.*
import com.jarvismini.ui.theme.GlassSurface
import com.jarvismini.ui.theme.JarvisCyan
import com.jarvismini.ui.theme.JarvisMotion
import com.jarvismini.ui.theme.JarvisOffline
import com.jarvismini.ui.theme.JarvisOnline
import com.jarvismini.ui.theme.JarvisRadius
import com.jarvismini.ui.theme.JarvisTextPrimary
import com.jarvismini.ui.theme.JarvisTextSecondary
import com.jarvismini.ui.theme.pressScale
import com.jarvismini.ui.theme.rememberMotionEnabled
import com.jarvismini.ui.theme.staggerEnter
import com.jarvismini.voice.VoiceTriggerManager

private data class QuickAction(
    val title: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
fun EnhancedHomeScreen(
    onNavigateToChat:          () -> Unit,
    onNavigateToCalendar:      () -> Unit,
    onNavigateToChecklist:     () -> Unit,
    onNavigateToSettings:      () -> Unit,
    onNavigateToDebug:         () -> Unit,
    onNavigateToTermuxCommand: () -> Unit,
    onNavigateToAgent:         () -> Unit,
    onNavigateToHeal:          () -> Unit,
    onNavigateToConvo:         () -> Unit = {},
    // Called when voice produces a task string — parent routes it to AgentDashboard
    onVoiceTask: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current

    // ── Voice trigger ────────────────────────────────────────────────────────
    val vtm = remember { VoiceTriggerManager(context) }
    DisposableEffect(Unit) { onDispose { vtm.destroy() } }

    var isListening   by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("TAP TO ACTIVATE") }

    // ── Pulse animation ──────────────────────────────────────────────────────
    // Decorative loops are gated: animator scale 0 / battery saver => static HUD.
    val motionEnabled = rememberMotionEnabled()
    val pulseAnimation = rememberInfiniteTransition(label = "pulse")
    val pulseAnimated by pulseAnimation.animateFloat(
        initialValue = 0.3f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1500, easing = JarvisMotion.Standard),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    val pulseAlpha = if (motionEnabled) pulseAnimated else 0.8f

    // ── Slow continuous ring rotation — reads as a live HUD even at rest ──────
    val rotationAnimation = rememberInfiniteTransition(label = "rotation")
    val ringRotationAnimated by rotationAnimation.animateFloat(
        initialValue = 0f,
        targetValue  = 360f,
        animationSpec = infiniteRepeatable(
            animation  = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ringRotation"
    )
    val ringRotation = if (motionEnabled) ringRotationAnimated else 0f

    // Ordered by how often they are used: conversation first, maintenance last.
    val quickActions = listOf(
        QuickAction("Chat",        Icons.Default.Chat,          onNavigateToChat),
        QuickAction("Agent",       Icons.Default.SmartToy,      onNavigateToAgent),
        QuickAction("Tasks",       Icons.Default.Checklist,     onNavigateToChecklist),
        QuickAction("Calendar",    Icons.Default.CalendarToday, onNavigateToCalendar),
        QuickAction("Terminal",    Icons.Default.Terminal,      onNavigateToTermuxCommand),
        QuickAction("Heal",        Icons.Default.Build,         onNavigateToHeal),
        QuickAction("Multi-agent", Icons.Default.Groups,        onNavigateToConvo),
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
        if (motionEnabled) {
            ParticleField()
            ScanLine()
        }

        // One scrolling column: nothing is clipped on small screens or at large font scale.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ===== HEADER =====
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .staggerEnter(0),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text          = "J.A.R.V.I.S.",
                    fontSize      = 28.sp,
                    fontWeight    = FontWeight.Light,
                    letterSpacing = 6.sp,
                    color         = JarvisCyan,
                    maxLines      = 1,
                    modifier      = Modifier
                        .weight(1f)
                        .semantics { heading() }
                )
                Row {
                    IconButton(onClick = onNavigateToDebug) {
                        Icon(Icons.Default.BugReport, "Debug", tint = JarvisCyan)
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "Settings", tint = JarvisCyan)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ===== HERO: voice orb + live state, one glass surface =====
            GlassSurface(
                modifier    = Modifier
                    .fillMaxWidth()
                    .staggerEnter(1),
                shape       = RoundedCornerShape(JarvisRadius.Large),
                cornerTicks = true
            ) {
                Column(
                    modifier            = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier         = Modifier
                            .size(188.dp)
                            .semantics {
                                contentDescription = "Voice assistant"
                                stateDescription   = if (isListening) "Listening" else "Idle"
                            }
                            .clickable(
                                role = Role.Button,
                                onClickLabel = if (isListening) "Stop listening" else "Start listening"
                            ) {
                                if (isListening) {
                                    vtm.stop()
                                    isListening   = false
                                    statusMessage = "TAP TO ACTIVATE"
                                } else {
                                    isListening   = true
                                    statusMessage = "LISTENING..."
                                    vtm.start(
                                        onReady  = {
                                            isListening   = true
                                            statusMessage = "LISTENING..."
                                        },
                                        onResult = { result ->
                                            isListening   = false
                                            statusMessage = "TAP TO ACTIVATE"
                                            onVoiceTask?.invoke(result) ?: onNavigateToAgent()
                                        },
                                        onError  = { err ->
                                            isListening   = false
                                            statusMessage = err
                                        }
                                    )
                                }
                            }
                    ) {
                        // Outer ring
                        Box(
                            modifier = Modifier
                                .size(168.dp)
                                .alpha(if (isListening) pulseAlpha else 0.3f)
                                .background(JarvisCyan.copy(alpha = 0.1f), CircleShape)
                                .border(
                                    2.dp,
                                    JarvisCyan.copy(alpha = if (isListening) pulseAlpha else 0.3f),
                                    CircleShape
                                )
                        )
                        // Rotating tick ring — four short arcs spinning slowly around the orb
                        Canvas(modifier = Modifier.size(188.dp)) {
                            rotate(ringRotation) {
                                val stroke = 3.dp.toPx()
                                val inset  = stroke / 2f
                                val sweep  = 18f
                                listOf(0f, 90f, 180f, 270f).forEach { start ->
                                    drawArc(
                                        color      = JarvisCyan.copy(alpha = if (isListening) 0.9f else 0.45f),
                                        startAngle = start,
                                        sweepAngle = sweep,
                                        useCenter  = false,
                                        topLeft    = androidx.compose.ui.geometry.Offset(inset, inset),
                                        size       = androidx.compose.ui.geometry.Size(
                                            size.width - stroke,
                                            size.height - stroke
                                        ),
                                        style      = Stroke(width = stroke, cap = StrokeCap.Round)
                                    )
                                }
                            }
                        }
                        // Mid ring
                        Box(
                            modifier = Modifier
                                .size(126.dp)
                                .background(JarvisCyan.copy(alpha = 0.05f), CircleShape)
                                .border(1.dp, JarvisCyan.copy(alpha = 0.4f), CircleShape)
                        )
                        // Inner orb with mic icon
                        Box(
                            modifier = Modifier
                                .size(86.dp)
                                .shadow(
                                    elevation  = if (isListening) 30.dp else 10.dp,
                                    shape      = CircleShape,
                                    spotColor  = JarvisCyan
                                )
                                .background(
                                    brush = Brush.radialGradient(
                                        listOf(
                                            JarvisCyan.copy(alpha = if (isListening) 0.8f else 0.5f),
                                            Color(0xFF0080FF).copy(alpha = if (isListening) 0.4f else 0.2f),
                                            Color.Transparent
                                        )
                                    ),
                                    shape = CircleShape
                                )
                        ) {
                            Icon(
                                imageVector        = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = null, // parent orb carries the semantics
                                tint               = JarvisCyan,
                                modifier           = Modifier
                                    .size(36.dp)
                                    .align(Alignment.Center)
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text       = statusMessage,
                        fontSize   = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        color      = JarvisTextPrimary,
                        textAlign  = TextAlign.Center,
                        modifier   = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // ===== QUICK ACCESS: 3-column grid, every action visible without scrolling sideways =====
            SectionLabel("QUICK ACCESS", Modifier.staggerEnter(2))

            Spacer(Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                quickActions.chunked(3).forEachIndexed { rowIndex, row ->
                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .staggerEnter(3 + rowIndex),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        row.forEach { action ->
                            QuickActionTile(
                                title    = action.title,
                                icon     = action.icon,
                                onClick  = action.onClick,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // keep the last row's tiles the same width as the rows above
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            // ===== SYSTEM STATUS: one card, rows separated by hairlines (was 5 bordered cards) =====
            SectionLabel("SYSTEM STATUS", Modifier.staggerEnter(6))

            Spacer(Modifier.height(12.dp))

            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .staggerEnter(6),
                shape = RoundedCornerShape(JarvisRadius.Card)
            ) {
                Column {
                    val rows = listOf(
                        Triple("Neural Networks",   "Online",  true),
                        Triple("Voice Recognition", "Active",  true),
                        Triple("Automation Engine", "Standby", false),
                        Triple("Local LLM",         "Ready",   true),
                        Triple("Agent Server",      "Standby", false),
                    )
                    rows.forEachIndexed { index, (label, status, online) ->
                        StatusRow(label, status, online)
                        if (index < rows.lastIndex) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .height(1.dp)
                                    .background(JarvisCyan.copy(alpha = 0.12f))
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Local components ─────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text          = text,
        fontSize      = 12.sp,
        fontFamily    = FontFamily.Monospace,
        letterSpacing = 2.sp,
        color         = JarvisTextSecondary,
        modifier      = modifier
            .fillMaxWidth()
            .semantics { heading() }
    )
}

@Composable
private fun QuickActionTile(
    title:    String,
    icon:     ImageVector,
    onClick:  () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    GlassSurface(
        modifier = modifier
            .heightIn(min = 92.dp)
            .pressScale(interaction)
            .clickable(
                interactionSource = interaction,
                indication        = null,
                role              = Role.Button,
                onClick           = onClick
            ),
        shape       = RoundedCornerShape(JarvisRadius.Card),
        cornerTicks = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 92.dp)
                .padding(horizontal = 6.dp, vertical = 12.dp)
                // icon + label read as one button named by its text
                .clearAndSetSemantics { contentDescription = title },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = JarvisCyan,
                modifier           = Modifier.size(30.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text       = title,
                fontSize   = 13.sp,
                color      = JarvisTextPrimary,
                fontFamily = FontFamily.Monospace,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StatusRow(
    label:    String,
    status:   String,
    isOnline: Boolean,
) {
    val statusColor = if (isOnline) JarvisOnline else JarvisOffline
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // one announcement: "Neural Networks, Online"; dot is decorative
            .semantics(mergeDescendants = true) {}
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(
            text       = label,
            fontSize   = 14.sp,
            fontFamily = FontFamily.Monospace,
            color      = JarvisTextPrimary,
            modifier   = Modifier.weight(1f)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color = statusColor, shape = CircleShape)
            )
            Spacer(Modifier.width(8.dp))
            // state is conveyed by the word, not only by colour
            Text(
                text       = status,
                fontSize   = 13.sp,
                fontFamily = FontFamily.Monospace,
                color      = statusColor
            )
        }
    }
}
