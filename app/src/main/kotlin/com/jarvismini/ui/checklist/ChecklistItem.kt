package com.jarvismini.ui.checklist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvismini.core.progress.ProgressBlock
import com.jarvismini.core.routine.model.Routine
import com.jarvismini.ui.theme.JarvisBlue
import com.jarvismini.ui.theme.JarvisGreen
import com.jarvismini.ui.theme.JarvisRed
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChecklistItem(
    block: ProgressBlock,
    routine: Routine,
    onComplete: () -> Unit,
    onIncomplete: () -> Unit
) {
    val displayName = routine.name
        .replace("_", " ")
        .replaceFirstChar { it.uppercaseChar() }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(
                color = when {
                    block.completed -> JarvisBlue.copy(alpha = 0.1f)
                    block.missedAt != null -> JarvisRed.copy(alpha = 0.1f)
                    else -> Color.Black.copy(alpha = 0.5f)
                },
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = 1.dp,
                color = when {
                    block.completed -> JarvisBlue.copy(alpha = 0.6f)
                    block.missedAt != null -> JarvisRed.copy(alpha = 0.6f)
                    else -> JarvisBlue.copy(alpha = 0.3f)
                },
                shape = RoundedCornerShape(8.dp)
            ),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {

            Text(
                text = displayName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = JarvisBlue
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(modifier = Modifier.padding(start = 4.dp)) {
                routine.actions.forEach { action ->
                    val displayText = buildString {
                        append(action.type.replace("_", " ").replaceFirstChar { it.uppercaseChar() })
                        if (action.params.isNotEmpty()) {
                            append(": ")
                            append(action.params.entries.joinToString { "${it.key}=${it.value}" })
                        }
                    }
                    Text(
                        text = "• $displayText",
                        fontSize = 12.sp,
                        color = JarvisBlue.copy(alpha = 0.7f),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Scheduled: ${formatTime(block.scheduledAt)}",
                fontSize = 11.sp,
                color = JarvisBlue.copy(alpha = 0.6f),
                fontFamily = FontFamily.Monospace
            )

            block.completedAt?.let {
                if (block.completed) {
                    Text(
                        text = "Completed: ${formatTime(it)}",
                        fontSize = 11.sp,
                        color = JarvisGreen.copy(alpha = 0.8f),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            block.missedAt?.let {
                Text(
                    text = "Missed: ${formatTime(it)}",
                    fontSize = 11.sp,
                    color = JarvisRed,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!block.completed) {
                    TextButton(onClick = onComplete, colors = ButtonDefaults.textButtonColors(contentColor = JarvisGreen)) {
                        Text("DONE", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onIncomplete, colors = ButtonDefaults.textButtonColors(contentColor = JarvisRed)) {
                        Text("MISSED", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                } else {
                    Text(
                        text = "✅ COMPLETED",
                        fontSize = 12.sp,
                        color = JarvisGreen,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

private fun formatTime(timestampMs: Long): String {
    val formatter = SimpleDateFormat("h:mm a", Locale.getDefault())
    return formatter.format(Date(timestampMs))
}
