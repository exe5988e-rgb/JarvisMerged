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

data class ChatMessage(val text: String, val isUser: Boolean)

@Composable
fun JarvisChatScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    // ================= INIT =================
    LaunchedEffect(Unit) {
        JarvisState.init(context)
        EngineProvider.init(context)
    }

    // ================= PERMISSIONS =================
    LaunchedEffect(Unit) {
        if (activity == null) return@LaunchedEffect
        val perms = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED)
            perms += android.Manifest.permission.READ_CONTACTS
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED)
            perms += android.Manifest.permission.SEND_SMS
        if (perms.isNotEmpty()) ActivityCompat.requestPermissions(activity, perms.toTypedArray(), 2001)
    }

    // ================= STATE =================
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var input by remember { mutableStateOf("") }
    var currentMode by remember { mutableStateOf(JarvisState.currentMode) }
    var expanded by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    val modes = JarvisMode.values().toList()

    // ================= UI =================
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

        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {

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

            Spacer(modifier = Modifier.height(12.dp))

            // ===== MODE SELECTOR =====
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                TextField(
                    value = currentMode.name,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Select Mode", color = JarvisBlue) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = JarvisBlue,
                        unfocusedTextColor = JarvisBlue,
                        cursorColor = JarvisBlue,
                        focusedIndicatorColor = JarvisBlue,
                        unfocusedIndicatorColor = JarvisBlue
                    ),
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
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

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    WorkModeManager.toggle(context)
                    currentMode = JarvisState.currentMode
                },
                colors = ButtonDefaults.buttonColors(containerColor = JarvisBlue.copy(alpha = 0.7f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Toggle Work Mode", color = Color.Black)
            }

            HorizontalDivider(color = JarvisBlue.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 8.dp))

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
                    enabled = !isProcessing,
                    placeholder = {
                        Text(
                            if (isProcessing) "Processing..." else "Command input…",
                            fontFamily = FontFamily.Monospace,
                            color = JarvisBlue.copy(alpha = 0.7f)
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedTextColor = JarvisBlue,
                        unfocusedTextColor = JarvisBlue,
                        disabledTextColor = JarvisBlue.copy(alpha = 0.5f),
                        cursorColor = JarvisBlue,
                        focusedIndicatorColor = JarvisBlue,
                        unfocusedIndicatorColor = JarvisBlue,
                        disabledIndicatorColor = JarvisBlue.copy(alpha = 0.3f)
                    )
                )

                // ✅ FIX: Use proper coroutine with async LLM call
                IconButton(
                    onClick = {
                        val userText = input.trim()
                        if (userText.isEmpty() || isProcessing) return@IconButton

                        input = ""
                        isProcessing = true
                        messages.add(ChatMessage(userText, true))
                        messages.add(ChatMessage("Processing…", false))

                        scope.launch {
                            try {
                                val result = EngineProvider.commandEngine.handle(userText)
                                val reply = when (result) {
                                    is EngineResult.Success -> result.reply
                                    else -> {
                                        // ✅ FIX: Use the async version instead of blocking
                                        val llmEngine = EngineProvider.llmEngine
                                        if (llmEngine is com.jarvismini.engine.LlamaLLMEngine) {
                                            llmEngine.generateReplyAsync(userText)
                                        } else {
                                            llmEngine.generateReply(userText)
                                        }
                                    }
                                }
                                messages.removeLast()
                                messages.add(ChatMessage(reply, false))
                            } catch (e: Exception) {
                                messages.removeLast()
                                messages.add(ChatMessage("Error: ${e.message}", false))
                            } finally {
                                isProcessing = false
                            }
                        }
                    },
                    enabled = !isProcessing
                ) {
                    Icon(
                        Icons.Default.Send, 
                        "Send", 
                        tint = if (isProcessing) JarvisBlue.copy(alpha = 0.3f) else JarvisBlue
                    )
                }
            }
        }
    }
}
