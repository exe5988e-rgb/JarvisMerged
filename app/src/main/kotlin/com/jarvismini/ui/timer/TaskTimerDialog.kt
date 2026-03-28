package com.jarvismini.ui.timer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

private val JarvisBlue = Color(0xFF00E0FF)
private val JarvisGreen = Color(0xFF00FF00)

/**
 * JARVIS-themed timer duration picker dialog
 */
@Composable
fun TaskTimerDialog(
    taskName: String,
    onDismiss: () -> Unit,
    onStartTimer: (Long) -> Unit
) {
    var selectedMinutes by remember { mutableStateOf(30L) }
    var customInput by remember { mutableStateOf("") }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF001520)
            ),
            border = BorderStroke(2.dp, JarvisBlue)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title
                Text(
                    text = "⏱️ SET TASK TIMER",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = JarvisBlue,
                    fontFamily = FontFamily.Monospace
                )
                
                // Task name
                Text(
                    text = taskName.uppercase(),
                    fontSize = 14.sp,
                    color = JarvisGreen,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
                
                Divider(color = JarvisBlue.copy(alpha = 0.3f))
                
                // Quick preset buttons
                Text(
                    text = "QUICK PRESETS:",
                    fontSize = 12.sp,
                    color = JarvisBlue,
                    fontFamily = FontFamily.Monospace
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(15L, 30L, 45L, 60L).forEach { minutes ->
                        OutlinedButton(
                            onClick = {
                                selectedMinutes = minutes
                                customInput = ""
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (selectedMinutes == minutes && customInput.isEmpty()) 
                                    JarvisBlue.copy(alpha = 0.2f) 
                                else 
                                    Color.Transparent,
                                contentColor = JarvisBlue
                            ),
                            border = BorderStroke(1.dp, JarvisBlue),
                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = "${minutes}m",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
                
                // Custom input
                Text(
                    text = "OR CUSTOM DURATION:",
                    fontSize = 12.sp,
                    color = JarvisBlue,
                    fontFamily = FontFamily.Monospace
                )
                
                OutlinedTextField(
                    value = customInput,
                    onValueChange = { 
                        if (it.isEmpty() || it.all { char -> char.isDigit() }) {
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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JarvisBlue,
                        unfocusedBorderColor = JarvisBlue.copy(alpha = 0.5f),
                        focusedLabelColor = JarvisBlue,
                        unfocusedLabelColor = JarvisBlue.copy(alpha = 0.5f),
                        focusedTextColor = JarvisGreen,
                        unfocusedTextColor = JarvisGreen
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Selected duration display
                Text(
                    text = "DURATION: ${selectedMinutes} MINUTES",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = JarvisGreen,
                    fontFamily = FontFamily.Monospace
                )
                
                Divider(color = JarvisBlue.copy(alpha = 0.3f))
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.Red
                        ),
                        border = BorderStroke(1.dp, Color.Red),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "CANCEL",
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    
                    Button(
                        onClick = {
                            val duration = customInput.toLongOrNull() ?: selectedMinutes
                            if (duration > 0) {
                                onStartTimer(duration)
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = JarvisGreen,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "START ▶",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
