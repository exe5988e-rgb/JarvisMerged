package com.jarvismini.ui.checklist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvismini.core.progress.ProgressBlock
import com.jarvismini.core.routine.model.Routine
import com.jarvismini.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

private enum class ItemState(val label: String, val icon: ImageVector) {
    Pending("PENDING", Icons.Filled.Schedule),
    Done("COMPLETED", Icons.Filled.Check),
    Missed("MISSED", Icons.Filled.Close),
}

@Composable
fun ChecklistItem(
    block: ProgressBlock,
    routine: Routine,
    onComplete: () -> Unit,
    onIncomplete: () -> Unit,
    index: Int = 0,
) {
    val displayName = routine.name.replace("_", " ").replaceFirstChar { it.uppercaseChar() }
    val state = when {
        block.completed -> ItemState.Done
        block.missedAt != null -> ItemState.Missed
        else -> ItemState.Pending
    }
    val accent = when (state) {
        ItemState.Done -> JarvisGreen
        ItemState.Missed -> JarvisRed
        ItemState.Pending -> JarvisCyan
    }
    val mono = FontFamily.Monospace
    val timeStyle = androidx.compose.ui.text.TextStyle(
        fontFamily = mono, fontSize = 12.sp, fontFeatureSettings = "tnum",
    )

    // Glass only on this top-level card; actions list stays plain text (no nested glass).
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .staggerEnter(index)
            .semantics(mergeDescendants = true) {
                stateDescription = state.label.lowercase()
                contentDescription = "$displayName, ${state.label.lowercase()}, scheduled ${formatTime(block.scheduledAt)}"
            },
        shape = RoundedCornerShape(JarvisRadius.Card),
        tint = accent,
    ) {
        Column(Modifier.padding(16.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // State is icon + text, never colour alone.
                Icon(state.icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    displayName,
                    modifier = Modifier.weight(1f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = JarvisTextPrimary,
                )
                Text(state.label, style = timeStyle, color = accent, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(8.dp))
            routine.actions.forEach { action ->
                val text = buildString {
                    append(action.type.replace("_", " ").replaceFirstChar { it.uppercaseChar() })
                    if (action.params.isNotEmpty()) {
                        append(": ")
                        append(action.params.entries.joinToString { "${it.key}=${it.value}" })
                    }
                }
                Text("• $text", style = timeStyle, color = JarvisTextSecondary)
            }

            Spacer(Modifier.height(8.dp))
            Text("Scheduled ${formatTime(block.scheduledAt)}", style = timeStyle, color = JarvisTextSecondary)
            block.completedAt?.takeIf { block.completed }?.let {
                Text("Completed ${formatTime(it)}", style = timeStyle, color = JarvisGreen)
            }
            block.missedAt?.let {
                Text("Missed ${formatTime(it)}", style = timeStyle, color = JarvisRed)
            }

            if (!block.completed) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    ActionButton("DONE", JarvisGreen, onComplete)
                    Spacer(Modifier.width(8.dp))
                    ActionButton("MISSED", JarvisRed, onIncomplete)
                }
            }
        }
    }
}

@Composable
private fun ActionButton(label: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    TextButton(
        onClick = onClick,
        interactionSource = source,
        modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp).pressScale(source),
        colors = ButtonDefaults.textButtonColors(contentColor = color),
    ) {
        Text(label, fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

private fun formatTime(timestampMs: Long): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestampMs))
