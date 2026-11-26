package com.jarvismini.ui.home

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.jarvismini.convo.ConvoNotifier
import com.jarvismini.ui.components.*
import com.jarvismini.ui.theme.JarvisBackground
import com.jarvismini.ui.theme.JarvisCyan
import com.jarvismini.ui.theme.JarvisMotion
import com.jarvismini.ui.theme.JarvisTextPrimary
import com.jarvismini.ui.theme.rememberMotionEnabled
import com.jarvismini.ui.theme.staggerEnter
import com.jarvismini.voice.VoiceTriggerManager

/**
 * Home: the voice orb is the single hero. Under it, one live status strip (real state only).
 * The four primary destinations live in a bottom dock; everything else sits behind "More".
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    var showMore      by remember { mutableStateOf(false) }

    // ── Real status (checked, not hardcoded) ─────────────────────────────────
    // Re-read on every resume: the user can change these in system Settings and come back.
    var resumeCount by remember { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeCount++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val a11yOn      = remember(resumeCount) { isJarvisAccessibilityOn(context) }
    val convoAlerts = remember(resumeCount) { ConvoNotifier.isEnabled(context) }
    val chips = listOf(
        StatusChipData("Voice",         if (isListening) "Listening" else "Ready", if (isListening) true else null),
        StatusChipData("Accessibility", if (a11yOn) "On" else "Off",               a11yOn),
        StatusChipData("Convo alerts",  if (convoAlerts) "On" else "Off",          convoAlerts),
    )

    // Decorative motion is gated: animator scale 0 / battery saver => static sphere, no particles.
    val motionEnabled = rememberMotionEnabled()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Black, JarvisBackground, Color.Black)
                )
            )
    ) {
        GridBackground()
        if (motionEnabled) {
            ParticleField()
            ScanLine()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp),
        ) {

            // ===== TOP BAR: title + one icon =====
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
                IconButton(onClick = onNavigateToSettings) {
                    Icon(Icons.Default.Settings, "Settings", tint = JarvisCyan)
                }
            }

            // ===== CENTRE: orb (hero) + state line + status strip =====
            // heightIn(min = maxHeight) keeps the content vertically centred when it fits,
            // and lets it scroll on short screens or at large font scale.
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = maxHeight)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier         = Modifier
                            .staggerEnter(1)
                            .size(256.dp)
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
                        PlexusSphere(
                            listening     = isListening,
                            motionEnabled = motionEnabled,
                            modifier      = Modifier.fillMaxSize()
                        )
                        // Siri-style wave mesh while the mic is live
                        if (isListening) {
                            SiriWaveMesh(
                                motionEnabled = motionEnabled,
                                modifier      = Modifier.fillMaxSize()
                            )
                        }
                        // Centre mic disc: the tap affordance inside the sphere
                        val micScale by animateFloatAsState(
                            targetValue   = if (isListening && motionEnabled) 1.12f else 1f,
                            animationSpec = JarvisMotion.snappy(),
                            label         = "micScale"
                        )
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(64.dp)
                                .graphicsLayer { scaleX = micScale; scaleY = micScale }
                                .background(
                                    brush = Brush.radialGradient(
                                        listOf(
                                            JarvisCyan.copy(alpha = if (isListening) 0.45f else 0.22f),
                                            JarvisBackground.copy(alpha = 0.85f)
                                        )
                                    ),
                                    shape = CircleShape
                                )
                                .border(
                                    1.dp,
                                    JarvisCyan.copy(alpha = if (isListening) 0.9f else 0.5f),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                imageVector        = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = null, // parent orb carries the semantics
                                tint               = JarvisCyan,
                                modifier           = Modifier.size(28.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Fixed-height box so LISTENING... / TAP TO ACTIVATE / error text never shifts the layout
                    Box(
                        modifier = Modifier
                            .staggerEnter(2)
                            .fillMaxWidth()
                            .heightIn(min = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text       = statusMessage,
                            fontSize   = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            color      = JarvisTextPrimary,
                            textAlign  = TextAlign.Center,
                            modifier   = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    StatusStrip(
                        chips    = chips,
                        modifier = Modifier.staggerEnter(3)
                    )
                }
            }

            // ===== DOCK: four primary destinations + More =====
            JarvisDock(
                items = listOf(
                    DockItem("Chat",     Icons.Default.Chat,      onNavigateToChat),
                    DockItem("Tasks",    Icons.Default.Checklist, onNavigateToChecklist),
                    DockItem("Agent",    Icons.Default.SmartToy,  onNavigateToAgent),
                    DockItem("Terminal", Icons.Default.Terminal,  onNavigateToTermuxCommand),
                ),
                onMore   = { showMore = true },
                modifier = Modifier
                    .staggerEnter(4)
                    .padding(top = 8.dp, bottom = 12.dp)
                    .navigationBarsPadding()
            )
        }
    }

    // ===== MORE: secondary destinations =====
    if (showMore) {
        ModalBottomSheet(
            onDismissRequest = { showMore = false },
            sheetState       = rememberModalBottomSheetState(),
            containerColor   = JarvisBackground,
            contentColor     = JarvisTextPrimary,
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                MoreRow("Calendar",    Icons.Default.CalendarToday) { showMore = false; onNavigateToCalendar() }
                MoreRow("Multi-agent", Icons.Default.Groups)        { showMore = false; onNavigateToConvo() }
                MoreRow("Heal",        Icons.Default.Build)         { showMore = false; onNavigateToHeal() }
                MoreRow("Debug",       Icons.Default.BugReport)     { showMore = false; onNavigateToDebug() }
            }
        }
    }
}

// ─── Local helpers ────────────────────────────────────────────────────────────

/** True when one of our accessibility services is enabled in system settings. */
private fun isJarvisAccessibilityOn(ctx: Context): Boolean = try {
    Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        ?.contains(ctx.packageName) == true
} catch (_: Throwable) {
    false
}

@Composable
private fun MoreRow(
    label:   String,
    icon:    ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // the row is announced by its label; the icon is decorative
        Icon(icon, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(
            text       = label,
            fontSize   = 15.sp,
            fontFamily = FontFamily.Monospace,
            color      = JarvisTextPrimary
        )
    }
}
