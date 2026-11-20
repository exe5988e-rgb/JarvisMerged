package com.jarvismini.ui.timer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.jarvismini.ui.theme.GlassSurface
import com.jarvismini.ui.theme.JarvisBlue
import com.jarvismini.ui.theme.JarvisOffline
import com.jarvismini.ui.theme.JarvisOnline
import com.jarvismini.ui.theme.JarvisRadius
import com.jarvismini.ui.theme.JarvisTextPrimary
import com.jarvismini.ui.theme.JarvisTextSecondary
import com.jarvismini.ui.theme.pressScale
import com.jarvismini.ui.theme.staggerEnter

private val PresetMinutes = listOf(15L, 30L, 45L, 60L)

/**
 * JARVIS-themed timer duration picker dialog (glass panel, HUD corner ticks).
 * All controls are >= 48dp, the selected preset is announced, and the chosen
 * duration is a polite live region.
 */
@Composable
fun TaskTimerDialog(
    taskName: String,
    onDismiss: () -> Unit,
    onStartTimer: (Long) -> Unit
) {
    var selectedMinutes by remember { mutableStateOf(30L) }
    var customInput by remember { mutableStateOf("") }

    val effectiveMinutes = customInput.toLongOrNull() ?: selectedMinutes
    val canStart = effectiveMinutes > 0

    Dialog(onDismissRequest = onDismiss) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .staggerEnter(0),
            shape = RoundedCornerShape(JarvisRadius.Large),
            cornerTicks = true
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "SET TASK TIMER",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = JarvisBlue,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.semantics { heading() }
                )

                Text(
                    text = taskName.uppercase(),
                    fontSize = 14.sp,
                    color = JarvisOnline,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )

                Divider(color = JarvisBlue.copy(alpha = 0.3f))

                Text(
                    text = "QUICK PRESETS",
                    fontSize = 14.sp,
                    color = JarvisTextSecondary,
                    fontFamily = FontFamily.Monospace
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PresetMinutes.forEach { minutes ->
                        PresetButton(
                            minutes = minutes,
                            selected = selectedMinutes == minutes && customInput.isEmpty(),
                            onClick = {
                                selectedMinutes = minutes
                                customInput = ""
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Text(
                    text = "OR CUSTOM DURATION",
                    fontSize = 14.sp,
                    color = JarvisTextSecondary,
                    fontFamily = FontFamily.Monospace
                )

                OutlinedTextField(
                    value = customInput,
                    onValueChange = {
                        if (it.length <= 4 && (it.isEmpty() || it.all { char -> char.isDigit() })) {
                            customInput = it
                            if (it.isNotEmpty()) {
                                selectedMinutes = it.toLongOrNull() ?: selectedMinutes
                            }
                        }
                    },
                    label = {
                        Text(
                            "Minutes",
                            fontFamily = FontFamily.Monospace
                        )
                    },
                    isError = customInput.isNotEmpty() && !canStart,
                    supportingText = {
                        if (customInput.isNotEmpty() && !canStart) {
                            Text("Enter at least 1 minute")
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JarvisBlue,
                        unfocusedBorderColor = JarvisBlue.copy(alpha = 0.6f),
                        focusedLabelColor = JarvisBlue,
                        unfocusedLabelColor = JarvisTextSecondary,
                        focusedTextColor = JarvisTextPrimary,
                        unfocusedTextColor = JarvisTextPrimary,
                        errorTextColor = JarvisTextPrimary,
                        errorBorderColor = JarvisOffline,
                        errorLabelColor = JarvisOffline,
                        errorSupportingTextColor = JarvisOffline,
                        cursorColor = JarvisBlue
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "DURATION: $effectiveMinutes MINUTES",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = JarvisOnline,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )

                Divider(color = JarvisBlue.copy(alpha = 0.3f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val cancelSource = remember { MutableInteractionSource() }
                    OutlinedButton(
                        onClick = onDismiss,
                        interactionSource = cancelSource,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = JarvisOffline
                        ),
                        border = BorderStroke(1.dp, JarvisOffline),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .pressScale(cancelSource)
                    ) {
                        Text(
                            "CANCEL",
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    val startSource = remember { MutableInteractionSource() }
                    Button(
                        onClick = {
                            if (canStart) {
                                onStartTimer(effectiveMinutes)
                                onDismiss()
                            }
                        },
                        enabled = canStart,
                        interactionSource = startSource,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = JarvisOnline,
                            contentColor = Color.Black,
                            disabledContainerColor = JarvisOnline.copy(alpha = 0.3f),
                            disabledContentColor = JarvisTextSecondary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .pressScale(startSource)
                    ) {
                        Text(
                            "START",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetButton(
    minutes: Long,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val source = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        interactionSource = source,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) JarvisBlue.copy(alpha = 0.25f) else Color.Transparent,
            contentColor = JarvisBlue
        ),
        border = BorderStroke(if (selected) 2.dp else 1.dp, JarvisBlue),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
        modifier = modifier
            .heightIn(min = 48.dp)
            .pressScale(source)
            .semantics { stateDescription = if (selected) "Selected" else "Not selected" }
    ) {
        Text(
            text = "${minutes}m",
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontFamily = FontFamily.Monospace
        )
    }
}
