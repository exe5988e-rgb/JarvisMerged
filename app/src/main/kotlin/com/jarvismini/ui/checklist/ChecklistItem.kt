@file:OptIn(ExperimentalMaterial3Api::class)

package com.jarvismini.ui.checklist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvismini.core.progress.ProgressBlock
import com.jarvismini.core.routine.model.Routine
import kotlinx.coroutines.delay

private val JarvisBlue = Color(0xFF00E0FF)
private val JarvisGreen = Color(0xFF00FF9C)
private val JarvisRed = Color(0xFFFF4C4C)

@Composable
fun ChecklistItem(
    block: ProgressBlock,
    routine: Routine,
    onComplete: () -> Unit,
    onIncomplete: () -> Unit
) {
    var remainingMs by remember { mutableStateOf(0L) }

    val durationMs = block.durationMinutes * 60 * 1000L

    LaunchedEffect(block.startTimestamp, block.durationMinutes) {
        while (true) {
            if (block.startTimestamp > 0 && durationMs > 0) {
                val elapsed = System.currentTimeMillis() - block.startTimestamp
                remainingMs = (durationMs - elapsed).coerceAtLeast(0L)
            } else {
                remainingMs = 0L
            }
            delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(16.dp)
    ) {

        Text(
            text = routine.name.uppercase(),
            color = JarvisBlue,
            fontSize = 18.sp,
            fontFamily = FontFamily.Monospace
        )

        Spacer(Modifier.height(6.dp))

        if (durationMs > 0 && remainingMs > 0) {
            Text(
                text = "TIME LEFT: ${formatTime(remainingMs)}",
                color = JarvisBlue.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {

            Button(
                onClick = onComplete,
                colors = ButtonDefaults.buttonColors(
                    containerColor = JarvisGreen
                )
            ) {
                Text("DONE", fontFamily = FontFamily.Monospace)
            }

            OutlinedButton(
                onClick = onIncomplete,
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(JarvisRed)
                )
            ) {
                Text(
                    "LATER",
                    color = JarvisRed,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%02d:%02d".format(min, sec)
}
