package com.jarvismini.ui.stopwatch

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jarvismini.core.stopwatch.NotificationPermissionHelper
import com.jarvismini.core.stopwatch.StopwatchManager
import kotlinx.coroutines.delay

/**
 * Stopwatch UI Widget with proper permission handling
 */
@Composable
fun StopwatchWidget() {
    val context = LocalContext.current
    val state by StopwatchManager.state.collectAsState()
    
    var displayTime by remember { mutableStateOf("00:00") }
    
    // Update display time every 100ms when running
    LaunchedEffect(state.isRunning) {
        while (state.isRunning) {
            displayTime = StopwatchManager.formatElapsedTimeLong(
                StopwatchManager.getCurrentElapsed()
            )
            delay(100)
        }
    }
    
    // Update display time even when paused (for resume case)
    LaunchedEffect(Unit) {
        while (true) {
            if (!state.isRunning) {
                displayTime = StopwatchManager.formatElapsedTimeLong(
                    StopwatchManager.getCurrentElapsed()
                )
            }
            delay(100)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Display time
        Text(
            text = displayTime,
            style = MaterialTheme.typography.displayLarge
        )
        
        // Control buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!state.isRunning) {
                Button(
                    onClick = {
                        // Check permission before starting
                        if (NotificationPermissionHelper.shouldRequestPermission(context)) {
                            (context as? Activity)?.let { activity ->
                                NotificationPermissionHelper.requestPermission(activity)
                                Toast.makeText(
                                    context,
                                    "Please grant notification permission",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else {
                            StopwatchManager.start(context)
                        }
                    }
                ) {
                    Text("Start")
                }
            } else {
                Button(
                    onClick = { StopwatchManager.pause(context) }
                ) {
                    Text("Pause")
                }
            }
            
            Button(
                onClick = { StopwatchManager.reset(context) },
                enabled = displayTime != "00:00"
            ) {
                Text("Reset")
            }
        }
        
        // Permission status indicator
        if (NotificationPermissionHelper.shouldRequestPermission(context)) {
            Text(
                text = "⚠️ Notification permission required for background timer",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
