@file:OptIn(ExperimentalMaterial3Api::class)

package com.jarvismini.ui

import android.app.Activity
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jarvismini.api.JarvisApiClient
import com.jarvismini.core.JarvisMode
import com.jarvismini.core.JarvisState
import com.jarvismini.core.WorkModeManager
import kotlinx.coroutines.launch

@Composable
fun JarvisChatScreen() {

    val context = LocalContext.current
    val activity = context as? Activity

    // UI coroutine scope (Main-safe)
    val scope = rememberCoroutineScope()

    // ================= INIT =================

    LaunchedEffect(Unit) {
        JarvisState.init(context)
    }

    LaunchedEffect(Unit) {
        if (activity == null) return@LaunchedEffect

        val perms = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) perms += android.Manifest.permission.READ_CONTACTS

        if (ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) perms += android.Manifest.permission.SEND_SMS

        if (perms.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                activity,
                perms.toTypedArray(),
                2001
            )
        }
    }

    // ================= STATE =================

    val messages = remember { mutableStateListOf<ChatMessage>() }
    var input by remember { mutableStateOf("") }

    var currentMode by remember {
        mutableStateOf(JarvisState.currentMode)
    }

    var expanded by remember { mutableStateOf(false) }
    val modes = JarvisMode.values().toList()

    // ================= UI =================

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {

        Text(
            text = "Current mode: $currentMode",
            style = MaterialTheme.typography.titleMedium
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            TextField(
                value = currentMode.name,
                onValueChange = {},
                readOnly = true,
                label = { Text("Select Mode") },
                modifier = Modifier.menuAnchor()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                modes.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.name) },
                        onClick = {
                            JarvisState.setMode(context, mode)
                            currentMode = mode
                            expanded = false
                        }
                    )
                }
            }
        }

        Button(onClick = {
            WorkModeManager.toggle(context)
            currentMode = JarvisState.currentMode
        }) {
            Text("Toggle Work Mode")
        }

        Divider()

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            reverseLayout = true
        ) {
            items(messages.reversed()) { msg ->
                Text(
                    text = if (msg.isUser)
                        "You: ${msg.text}"
                    else
                        "Jarvis: ${msg.text}",
                    modifier = Modifier.padding(6.dp)
                )
            }
        }

        Row {
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask Jarvis…") }
            )

            Button(onClick = {
                val userText = input.trim()
                if (userText.isEmpty()) return@Button

                input = ""
                messages.add(ChatMessage(userText, true))

                scope.launch {
                    val reply = JarvisApiClient.getResponse(userText)
                    messages.add(ChatMessage(reply, false))
                }
            }) {
                Text("Send")
            }
        }

        Divider()

        Button(onClick = {
            scope.launch {
                val reply = JarvisApiClient.getResponse("Hello Jarvis")
                Toast.makeText(context, reply, Toast.LENGTH_LONG).show()
            }
        }) {
            Text("Ask Jarvis (Test)")
        }
    }
}
