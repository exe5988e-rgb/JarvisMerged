package com.jarvismini.ui.stopwatch

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvismini.core.stopwatch.NotificationPermissionHelper
import com.jarvismini.core.stopwatch.StopwatchManager
import com.jarvismini.ui.theme.JarvisBlue
import com.jarvismini.ui.theme.JarvisOffline
import com.jarvismini.ui.theme.JarvisTextPrimary
import kotlinx.coroutines.delay

/**
 * Stopwatch UI Widget with proper permission handling.
 *
 * Visual hierarchy: elapsed time (hero, tabular monospace so digits do not jitter) ->
 * primary Start/Pause -> secondary Reset -> permission notice. Buttons are at least 48dp
 * tall and share one width pair, the notice is icon + text (not colour alone) and is a
 * polite live region. The ticking time itself is not a live region (it would announce
 * every 100 ms); TalkBack reads it on focus with the running/paused state.
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

    val needsPermission = NotificationPermissionHelper.shouldRequestPermission(context)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Display time (hero)
        Text(
            text = displayTime,
            style = MaterialTheme.typography.displayLarge.merge(
                TextStyle(fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum")
            ),
            color = JarvisTextPrimary,
            modifier = Modifier.semantics {
                contentDescription = "Stopwatch $displayTime"
                stateDescription = if (state.isRunning) "Running" else "Paused"
            }
        )

        // Control buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!state.isRunning) {
                Button(
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JarvisBlue,
                        contentColor = androidx.compose.ui.graphics.Color.Black
                    ),
                    onClick = {
                        // Check permission before starting
                        if (needsPermission) {
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
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JarvisBlue,
                        contentColor = androidx.compose.ui.graphics.Color.Black
                    ),
                    onClick = { StopwatchManager.pause(context) }
                ) {
                    Text("Pause")
                }
            }

            OutlinedButton(
                modifier = Modifier.heightIn(min = 48.dp),
                onClick = { StopwatchManager.reset(context) },
                enabled = displayTime != "00:00"
            ) {
                Text("Reset", color = JarvisBlue.copy(alpha = if (displayTime != "00:00") 1f else 0.5f))
            }
        }

        // Permission status indicator: icon + text, announced politely.
        if (needsPermission) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.semantics(mergeDescendants = true) {
                    liveRegion = LiveRegionMode.Polite
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = JarvisOffline,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Notification permission required for background timer",
                    fontSize = 14.sp,
                    color = JarvisOffline
                )
            }
        }
    }
}
