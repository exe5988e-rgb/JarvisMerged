@file:OptIn(ExperimentalMaterial3Api::class)

package com.jarvismini.ui

import android.app.Activity
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jarvismini.core.JarvisMode
import com.jarvismini.core.JarvisState
import com.jarvismini.core.WorkModeManager
import com.jarvismini.engine.EngineProvider
import com.jarvismini.engine.EngineResult
import com.jarvismini.ui.components.GridBackground
import kotlinx.coroutines.launch

private val JarvisBlue = Color(0xFF00E0FF)

@Composable
fun JarvisChatScreen(
    onBack: () -> Unit
) {
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
        if (perms.isNotEmpty()) ActivityCompat.requestPermissions(activity, perms.toTypedArray(), 2001)
    }

    val messages = remember { mutableStateListOf<ChatMessage>() }
    var input by remember { mutableStateOf("") }
    var currentMode by remember { mutableStateOf(JarvisState.currentMode) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(Color.Black, Color(0xFF001520), Color.Black)
                )
            )
    ) {
        GridBackground()

        Column(modifier = Modifier.fillMaxSize()) {

            // ===== HEADER =====
            TopAppBar(
                title = {
                    Text(
                        "J.A.R.V.I.S CHAT",
                        color = JarvisBlue,
                        fontFamily = FontFamily.Monospace
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = JarvisBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.6f)
                )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                // ===== MODE STRIP =====
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            JarvisBlue.copy(alpha = 0.4f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "MODE: ${currentMode.name}",
                        color = JarvisBlue,
                        fontFamily = FontFamily.Monospace
                    )

                    TextButton(onClick = {
                        WorkModeManager.toggle(context)
                        currentMode = JarvisState.currentMode
                    }) {
                        Text("TOGGLE", color = JarvisBlue)
                    }
                }

                Divider(color = JarvisBlue.copy(alpha = 0.3f))

                // ===== CHAT LOG =====
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    reverseLayout = true
                ) {
                    items(messages.reversed()) { msg ->
                        Text(
                            text = if (msg.isUser) "YOU ▸ ${msg.text}" else "JARVIS ▸ ${msg.text}",
                            color = JarvisBlue,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(6.dp)
                        )
                    }
                }

                // ===== INPUT BAR =====
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            JarvisBlue.copy(alpha = 0.4f),
                            RoundedCornerShape(10.dp)
                        )
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                "Command input…",
                                fontFamily = FontFamily.Monospace
                            )
                        },
                        colors = TextFieldDefaults.textFieldColors(
                            containerColor = Color.Transparent,
                            textColor = JarvisBlue,
                            cursorColor = JarvisBlue,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )

                    IconButton(onClick = {
                        val userText = input.trim()
                        if (userText.isEmpty()) return@IconButton

                        input = ""
                        messages.add(ChatMessage(userText, true))
                        messages.add(ChatMessage("Processing…", false))

                        scope.launch {
                            val result = EngineProvider.commandEngine.handle(userText)
                            val reply = when (result) {
                                is EngineResult.Success -> result.reply
                                else -> EngineProvider.llmEngine.generateReply(userText)
                            }
                            messages.removeLast()
                            messages.add(ChatMessage(reply, false))
                        }
                    }) {
                        Icon(Icons.Default.Send, "Send", tint = JarvisBlue)
                    }
                }
            }
        }
    }
}
