package com.jarvismini.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Black, Color(0xFF001520), Color.Black))
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
                    text = "SETTINGS",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 4.sp,
                    color = Color(0xFF00E0FF)
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "SYSTEM CONFIGURATION",
                fontSize = 14.sp,
                letterSpacing = 2.sp,
                color = Color(0xFF00E0FF),
                fontFamily = FontFamily.Monospace
            )

            Spacer(Modifier.height(20.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    SettingsTile("Permissions", "All Granted", Icons.Default.CheckCircle)
                }
                item {
                    SettingsTile("LLM Model", "GPT-4.1 Mini", Icons.Default.Psychology)
                }
                item {
                    SettingsTile("Voice Engine", "ElevenLabs", Icons.Default.Mic)
                }
                item {
                    SettingsTile("Vision Models", "Downloaded", Icons.Default.Visibility)
                }
            }
        }
    }
}

@Composable
fun SettingsTile(title: String, subtitle: String, icon: ImageVector) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color.Black.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = 1.dp,
                color = Color(0xFF00E0FF).copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(16.dp),
        color = Color.Transparent
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF00E0FF)
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00E0FF)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = Color(0xFF00E0FF).copy(alpha = 0.6f)
                )
            }
        }
    }
}
