@file:OptIn(ExperimentalMaterial3Api::class)

package com.jarvismini.ui.chat

import android.app.Activity
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jarvismini.core.JarvisMode
import com.jarvismini.core.JarvisState
import com.jarvismini.core.WorkModeManager
import com.jarvismini.engine.EngineProvider
import com.jarvismini.engine.EngineResult
import com.jarvismini.ui.ChatMessage
import com.jarvismini.ui.components.GridBackground
import kotlinx.coroutines.launch

private val JarvisBlue = Color(0xFF00E0FF)

@Composable
fun JarvisChatScreen() {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        JarvisState.init(context)
        EngineProvider.init(context)
    }

    LaunchedEffect(Unit) {
        if (activity == null) return@LaunchedEffect
        val perms = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED)
            perms += android.Manifest.permission.READ_CONTACTS

        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED)
            perms += android.Manifest.permission.SEND_SMS

        if (perms.isNotEmpty())
            ActivityCompat.requestPermissions(activity, perms.toTypedArray(), 2001)
    }

    val messages = remember { mutableStateListOf<ChatMessage>() }
    var input by remember { mutableStateOf("") }
    var currentMode by remember { mutableStateOf(JarvisState.currentMode) }
    var expanded by remember { mutableStateOf(false) }
    val modes = JarvisMode.values().toList()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color.Black, Color(0xFF001520), Color.Black)
                )
            )
    ) {
        GridBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {

            // HEADER
            Text(
                text = "JARVIS CORE INTERFACE",
                fontSize = 22.sp,
                color = JarvisBlue,
                fontFamily = FontFamily.Monospace
            )

            Spacer(Modifier.height(12.dp))

            // MODE PANEL
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                TextField(
                    value = currentMode.name,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("OPERATION MODE", color = JarvisBlue) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    colors = TextFieldDefaults.textFieldColors(
                        containerColor = Color.Black,
                        focusedTextColor = JarvisBlue,
                        unfocusedTextColor = JarvisBlue
                    )
                )

                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    modes.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.name, color = JarvisBlue) },
                            onClick = {
                                JarvisState.setMode(context, mode)
                                currentMode = mode
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            TextButton(onClick = {
                WorkModeManager.toggle(context)
                currentMode = JarvisState.currentMode
            }) {
                Text("TOGGLE WORK MODE", color = JarvisBlue, fontFamily = FontFamily.Monospace)
            }

            Spacer(Modifier.height(12.dp))

            // CHAT PANEL
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .border(1.dp, JarvisBlue.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(10.dp)
            ) {
                LazyColumn(
                    reverseLayout = true,
                    modifier = Modifier.padding(12.dp)
                ) {
                    items(messages.reversed()) { msg ->
                        Text(
                            text = if (msg.isUser) "YOU > ${msg.text}" else "JARVIS > ${msg.text}",
                            color = if (msg.isUser) Color.White else JarvisBlue,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // INPUT PANEL
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("COMMAND INPUT...", color = JarvisBlue.copy(alpha = 0.5f)) },
                    colors = TextFieldDefaults.textFieldColors(
                        containerColor = Color.Black,
                        focusedTextColor = JarvisBlue,
                        unfocusedTextColor = JarvisBlue
                    )
                )

                Spacer(Modifier.width(10.dp))

                Button(
                    onClick = {
                        val userText = input.trim()
                        if (userText.isEmpty()) return@Button

                        input = ""
                        messages.add(ChatMessage(userText, true))
                        messages.add(ChatMessage("PROCESSING...", false))

                        scope.launch {
                            val result = EngineProvider.commandEngine.handle(userText)
                            val reply = when (result) {
                                is EngineResult.Success -> result.reply
                                is EngineResult.Unhandled -> EngineProvider.llmEngine.generateReply(userText)
                                else -> EngineProvider.llmEngine.generateReply(userText)
                            }

                            messages.removeLast()
                            messages.add(ChatMessage(reply, false))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisBlue)
                ) {
                    Text("SEND", color = Color.Black, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}
