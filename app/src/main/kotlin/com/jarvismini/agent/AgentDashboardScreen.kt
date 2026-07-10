package com.jarvismini.agent

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

private val Cyan    = Color(0xFF00E0FF)
private val DimCyan = Color(0xFF005566)
private val BgDark  = Color(0xFF060F14)
private val BgCard  = Color(0xFF0A1A22)
private val Green   = Color(0xFF00FF88)
private val Red     = Color(0xFFFF4466)
private val Yellow  = Color(0xFFFFCC00)
private val White70 = Color(0xB3FFFFFF)
private val White40 = Color(0x66FFFFFF)

@Composable
fun AgentDashboardScreen(
    onBack:      () -> Unit,
    initialTask: String? = null,
    vm:          AgentDashboardViewModel = viewModel()
) {
    val state     by vm.state.collectAsState()
    val context   = LocalContext.current
    val listState = rememberLazyListState()
    val scope     = rememberCoroutineScope()

    LaunchedEffect(initialTask) {
        if (!initialTask.isNullOrBlank()) {
            vm.onTaskInput(initialTask)
        }
    }

    LaunchedEffect(state.logs.size) {
        if (state.logs.isNotEmpty()) {
            listState.animateScrollToItem(state.logs.lastIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgDark, Color(0xFF010A0F))))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = Cyan)
                }
                Text(
                    "AGENT DASHBOARD",
                    fontSize      = 18.sp,
                    fontWeight    = FontWeight.Light,
                    letterSpacing = 4.sp,
                    color         = Cyan,
                    fontFamily    = FontFamily.Monospace
                )
                Spacer(Modifier.weight(1f))
                ServerStatusDot(online = state.serverOnline, running = state.running, paused = state.paused)
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { vm.checkServer() }) {
                    Icon(Icons.Default.Refresh, "Refresh", tint = White70, modifier = Modifier.size(18.dp))
                }
            }

            if (state.running || state.done || state.paused) {
                StatusBar(state)
                Spacer(Modifier.height(8.dp))
            }

            TaskInputSection(
                state    = state,
                onTask   = vm::onTaskInput,
                onDevice = vm::onDeviceInput,
                onStart  = vm::startAgent,
                onStop   = vm::stopAgent,
                onPause  = vm::pauseAgent,
                onResume = vm::resumeAgent,
            )

            Spacer(Modifier.height(8.dp))

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "LOGS",
                    fontSize      = 11.sp,
                    letterSpacing = 3.sp,
                    color         = DimCyan,
                    fontFamily    = FontFamily.Monospace
                )
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.VolumeUp,
                        null,
                        tint     = if (state.ttsEnabled) Cyan else White40,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(2.dp))
                    Switch(
                        checked         = state.ttsEnabled,
                        onCheckedChange = vm::onTtsToggle,
                        modifier        = Modifier.height(20.dp),
                        colors          = SwitchDefaults.colors(
                            checkedThumbColor   = Cyan,
                            checkedTrackColor   = DimCyan,
                            uncheckedThumbColor = White40,
                            uncheckedTrackColor = BgCard
                        )
                    )
                }
                if (state.logs.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${state.logs.size} lines",
                        fontSize   = 10.sp,
                        color      = White40,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick        = vm::clearLogs,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                    ) {
                        Text("Clear", fontSize = 11.sp, color = White40)
                    }
                    Spacer(Modifier.width(4.dp))
                    TextButton(
                        onClick        = { vm.speakResult(context) },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.VolumeUp, null, tint = Cyan, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Speak", fontSize = 11.sp, color = Cyan)
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(BgCard)
                    .border(0.5.dp, DimCyan.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                if (state.logs.isEmpty()) {
                    Text(
                        "No logs yet. Start a task to see agent activity.",
                        color      = White40,
                        fontSize   = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier   = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        items(state.logs, key = { it.id }) { log ->
                            AgentLogLineRow(log)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun StatusBar(state: AgentDashboardState) {
    val bg = when {
        state.error != null -> Red.copy(alpha = 0.15f)
        state.paused        -> Yellow.copy(alpha = 0.12f)
        state.done          -> Green.copy(alpha = 0.12f)
        state.running       -> Cyan.copy(alpha = 0.08f)
        else                -> BgCard
    }
    val text = when {
        state.error != null -> "✗ Error: ${state.error}"
        state.paused        -> "⏸ Paused at step ${state.step}  —  ${state.task.take(50)}"
        state.done          -> "✓ Done in ${state.step} steps"
        state.running       -> "Step ${state.step}  —  ${state.task.take(50)}"
        else                -> ""
    }
    val textColor = when {
        state.error != null -> Red
        state.paused        -> Yellow
        state.done          -> Green
        else                -> Cyan
    }
    if (text.isBlank()) return
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (state.running && !state.paused) {
            CircularProgressIndicator(
                modifier    = Modifier.size(12.dp),
                color       = Cyan,
                strokeWidth = 1.5.dp
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text,
            color      = textColor,
            fontSize   = 12.sp,
            fontFamily = FontFamily.Monospace,
            maxLines   = 1
        )
    }
}

@Composable
private fun TaskInputSection(
    state:    AgentDashboardState,
    onTask:   (String) -> Unit,
    onDevice: (String) -> Unit,
    onStart:  () -> Unit,
    onStop:   () -> Unit,
    onPause:  () -> Unit,
    onResume: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BgCard)
            .border(0.5.dp, DimCyan.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        OutlinedTextField(
            value           = state.taskInput,
            onValueChange   = onTask,
            label           = { Text("Task", color = White40, fontSize = 12.sp) },
            placeholder     = { Text("Open WhatsApp and message Mummy...", color = White40, fontSize = 12.sp, fontFamily = FontFamily.Monospace) },
            modifier        = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            minLines        = 2,
            maxLines        = 4,
            colors          = agentFieldColors(),
            textStyle       = LocalTextStyle.current.copy(
                color      = White70,
                fontSize   = 13.sp,
                fontFamily = FontFamily.Monospace
            )
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value         = state.deviceInput,
            onValueChange = onDevice,
            label         = { Text("Device", color = White40, fontSize = 11.sp) },
            singleLine    = true,
            modifier      = Modifier.fillMaxWidth(),
            colors        = agentFieldColors(),
            textStyle     = LocalTextStyle.current.copy(
                color      = Cyan,
                fontSize   = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        )

        Spacer(Modifier.height(10.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            if (state.running) {
                // ── Pause / Resume button ──────────────────────────────────
                if (state.paused) {
                    OutlinedButton(
                        onClick  = onResume,
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = Green),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Resume", fontSize = 13.sp)
                    }
                } else {
                    OutlinedButton(
                        onClick  = onPause,
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = Yellow),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Icon(Icons.Default.Pause, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Pause", fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.width(8.dp))
                // ── Stop button ────────────────────────────────────────────
                OutlinedButton(
                    onClick  = onStop,
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = Red),
                    modifier = Modifier.height(38.dp)
                ) {
                    Icon(Icons.Default.Stop, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Stop", fontSize = 13.sp)
                }
            } else {
                Button(
                    onClick  = onStart,
                    enabled  = state.taskInput.trim().isNotEmpty() && state.serverOnline,
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = Cyan,
                        contentColor   = BgDark
                    ),
                    modifier = Modifier.height(38.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Run", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        if (!state.serverOnline) {
            Spacer(Modifier.height(6.dp))
            Text(
                "⚠ Agent server offline — run start_jarvis_services.sh on Phone A",
                color      = Yellow,
                fontSize   = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

// Renamed from LogLineRow -> AgentLogLineRow (Session fix: Kotlin does not
// allow two top-level functions with the same signature in one package,
// private or not. HealDashboardScreen.kt already declares a public
// LogLineRow(log: LogLine) in this same package, causing
// "Conflicting overloads" + "Overload resolution ambiguity" build failures.
// This function is only ever called from within this file, so the rename
// is a pure, safe, local fix — no other file references it.
@Composable
private fun AgentLogLineRow(log: LogLine) {
    val color = when (log.level) {
        LogLevel.SUCCESS -> Green
        LogLevel.ERROR   -> Red
        LogLevel.WARN    -> Yellow
        LogLevel.STEP    -> Cyan
        LogLevel.SYSTEM  -> DimCyan
        LogLevel.INFO    -> White70
    }
    Text(
        text       = log.text,
        color      = color,
        fontSize   = 11.sp,
        fontFamily = FontFamily.Monospace,
        lineHeight = 14.sp,
        modifier   = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp, horizontal = 2.dp)
    )
}

// Session 17: paused param added — dot turns yellow when paused
@Composable
private fun ServerStatusDot(online: Boolean, running: Boolean, paused: Boolean = false) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val alpha by pulse.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )
    val color = when {
        !online -> Red
        paused  -> Yellow.copy(alpha = alpha)
        running -> Green.copy(alpha = alpha)
        else    -> Green
    }
    Box(
        Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun agentFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = Cyan,
    unfocusedBorderColor = DimCyan.copy(alpha = 0.5f),
    cursorColor          = Cyan,
    focusedLabelColor    = Cyan,
)
