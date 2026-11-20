package com.jarvismini.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jarvismini.ui.theme.GlassSurface
import com.jarvismini.ui.theme.JarvisBackground
import com.jarvismini.ui.theme.JarvisBlack
import com.jarvismini.ui.theme.JarvisCyan
import com.jarvismini.ui.theme.JarvisOffline
import com.jarvismini.ui.theme.JarvisOnline
import com.jarvismini.ui.theme.JarvisRadius
import com.jarvismini.ui.theme.JarvisTextPrimary
import com.jarvismini.ui.theme.JarvisTextSecondary
import com.jarvismini.ui.theme.rememberMotionEnabled
import com.jarvismini.ui.theme.staggerEnter

// Log-level accents with no theme token yet. ERROR and INFO use JarvisOffline / JarvisCyan.
private val LogWarning = Color(0xFFFFCC00)
private val LogDebug = Color(0xFFE040FB)

@Composable
fun DebugScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: DebugViewModel = viewModel(factory = DebugViewModel.Factory(context))
    val state by vm.state.collectAsState()
    val listState = rememberLazyListState()
    val motionEnabled = rememberMotionEnabled()

    LaunchedEffect(state.logs.size) {
        if (state.logs.isNotEmpty()) {
            // reduced motion / battery saver: jump instead of animating
            if (motionEnabled) listState.animateScrollToItem(state.logs.lastIndex)
            else listState.scrollToItem(state.logs.lastIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(JarvisBlack, JarvisBackground, JarvisBlack)
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
                modifier = Modifier
                    .fillMaxWidth()
                    .staggerEnter(0),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector        = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint               = JarvisCyan
                    )
                }
                Text(
                    text          = "DEBUG CONSOLE",
                    fontSize      = 24.sp,
                    fontWeight    = FontWeight.Light,
                    letterSpacing = 4.sp,
                    color         = JarvisCyan,
                    maxLines      = 1,
                    overflow      = TextOverflow.Ellipsis,
                    modifier      = Modifier
                        .weight(1f)
                        .semantics { heading() }
                )
                if (state.logs.isNotEmpty()) {
                    IconButton(onClick = vm::clearLogs) {
                        Icon(Icons.Default.Delete, "Clear logs", tint = JarvisCyan)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Stats header (glass: the one summary cluster) ─────────────────
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .staggerEnter(1),
                shape = RoundedCornerShape(JarvisRadius.Card)
            ) {
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    StatItem("LOGS", state.logs.size.toString())
                    StatItem("CPU",  state.cpuPct)
                    StatItem("RAM",  state.ramMb)
                    // The word ON / OFF carries the state, the colour only reinforces it.
                    StatItem(
                        label = "SERVER",
                        value = if (state.connected) "ON" else "OFF",
                        valueColor = if (state.connected) JarvisOnline else JarvisOffline
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Log pane (dense log view: flat and solid, no glass) ───────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .staggerEnter(2)
                    .border(
                        width = 1.dp,
                        color = JarvisCyan.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(JarvisRadius.Small)
                    )
                    .background(JarvisBlack.copy(alpha = 0.55f), RoundedCornerShape(JarvisRadius.Small))
                    .padding(8.dp)
            ) {
                if (state.logs.isEmpty()) {
                    Text(
                        text       = "Waiting for logs…",
                        color      = JarvisTextSecondary,
                        fontSize   = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier   = Modifier.align(Alignment.Center)
                    )
                } else {
                    // Selectable so a line can be copied for a bug report.
                    SelectionContainer {
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
                                            "ERROR"   -> JarvisOffline
                                            "WARNING" -> LogWarning
                                            "DEBUG"   -> LogDebug
                                            else      -> JarvisCyan
                                        }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text       = entry.message,
                                        fontSize   = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color      = JarvisTextPrimary,
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
}

@Composable
private fun RowScope.StatItem(
    label:      String,
    value:      String,
    valueColor: Color = JarvisCyan,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .weight(1f)
            // announced as one unit, "LOGS 12", not value and label separately
            .clearAndSetSemantics { contentDescription = "$label $value" }
    ) {
        Text(
            text       = value,
            fontSize   = 20.sp,
            fontWeight = FontWeight.Bold,
            color      = valueColor,
            maxLines   = 1,
            // tabular digits so live numbers do not jitter as they change
            style      = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text     = label,
            fontSize = 12.sp,
            color    = JarvisTextSecondary
        )
    }
}
