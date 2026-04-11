package com.jarvismini.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun DebugScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: DebugViewModel = viewModel(factory = DebugViewModel.Factory(context))
    val state by vm.state.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(state.logs.size) {
        if (state.logs.isNotEmpty()) listState.animateScrollToItem(state.logs.lastIndex)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Black, Color(0xFF001520), Color.Black)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            // ── Top bar ───────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector        = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint               = Color(0xFF00E0FF)
                    )
                }
                Text(
                    text          = "DEBUG CONSOLE",
                    fontSize      = 24.sp,
                    fontWeight    = FontWeight.Light,
                    letterSpacing = 4.sp,
                    color         = Color(0xFF00E0FF)
                )
                Spacer(Modifier.weight(1f))
                if (state.logs.isNotEmpty()) {
                    IconButton(onClick = vm::clearLogs) {
                        Icon(Icons.Default.Delete, "Clear", tint = Color(0xFF00E0FF).copy(alpha = 0.6f))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Stats header ──────────────────────────────────────────────────
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = Color(0xFF00E0FF).copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp)
                    ),
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    StatItem("LOGS", state.logs.size.toString())
                    StatItem("CPU",  state.cpuPct)
                    StatItem("RAM",  state.ramMb)
                    StatItem(
                        label = "SERVER",
                        value = if (state.connected) "ON" else "OFF",
                        valueColor = if (state.connected) Color(0xFF00FF88) else Color(0xFFFF4466)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Log pane ──────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .border(
                        width = 1.dp,
                        color = Color(0xFF00E0FF).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                if (state.logs.isEmpty()) {
                    Text(
                        text       = "Waiting for logs…",
                        color      = Color(0xFF00E0FF).copy(alpha = 0.4f),
                        fontSize   = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier   = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(
                        state               = listState,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(state.logs, key = { it.id }) { entry ->
                            Row {
                                Text(
                                    text       = "[${entry.level}]",
                                    fontSize   = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color      = when (entry.level) {
                                        "ERROR"   -> Color(0xFFFF4466)
                                        "WARNING" -> Color(0xFFFFCC00)
                                        "DEBUG"   -> Color(0xFFE040FB)
                                        else      -> Color(0xFF00E0FF)
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text       = entry.message,
                                    fontSize   = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color      = Color.White.copy(alpha = 0.8f),
                                    lineHeight = 15.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(
    label:      String,
    value:      String,
    valueColor: Color = Color(0xFF00E0FF),
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text       = value,
            fontSize   = 20.sp,
            fontWeight = FontWeight.Bold,
            color      = valueColor
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text     = label,
            fontSize = 12.sp,
            color    = Color(0xFF00E0FF).copy(alpha = 0.6f)
        )
    }
}
