package com.jarvismini.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class LogEntry(val level: String, val message: String)

@Composable
fun DebugScreen(onBack: () -> Unit) {
    val logs = listOf(
        LogEntry("INFO", "JARVIS INITIALIZED"),
        LogEntry("DEBUG", "Hotword engine started"),
        LogEntry("INFO", "Voice recognition online"),
        LogEntry("WARNING", "Camera permission pending")
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFF00E0FF)
                    )
                }
                Text(
                    text = "DEBUG CONSOLE",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 4.sp,
                    color = Color(0xFF00E0FF)
                )
            }

            Spacer(Modifier.height(16.dp))

            // Stats header
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
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    StatItem("LOGS", "245")
                    StatItem("CPU", "12%")
                    StatItem("RAM", "234MB")
                }
            }

            Spacer(Modifier.height(16.dp))

            // Logs
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(logs.size) { index ->
                    val log = logs[index]
                    Row {
                        Text(
                            text = "[${log.level}]",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = when (log.level) {
                                "ERROR" -> Color.Red
                                "WARNING" -> Color(0xFFFFA500)
                                else -> Color(0xFF00E0FF)
                            }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = log.message,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF00E00FF)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color(0xFF00E0FF).copy(alpha = 0.6f)
        )
    }
}
