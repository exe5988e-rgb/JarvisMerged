package com.jarvismini.ui.chat

import android.app.Activity
import android.content.pm.PackageManager
import android.util.Log
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
import com.jarvismini.llm.TermuxLlamaClient
import com.jarvismini.ui.components.GridBackground
import com.jarvismini.ui.ChatMessage
import com.jarvismini.ui.theme.JarvisBlue
import kotlinx.coroutines.*

private const val TAG = "JarvisChatScreen"

/**
 * Main chat screen for interacting with J.A.R.V.I.S.
 * 
 * UPDATED: Now uses Termux LLM server instead of local models
 * This fixes crashes that occurred when trying to load local .gguf models
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JarvisChatScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    // ================= TERMUX LLM CLIENT =================
    // Use Termux server instead of local models
    val llamaClient = remember { TermuxLlamaClient() }

    // ================= INITIALIZATION =================
    LaunchedEffect(Unit) {
        Log.d(TAG, "Initializing JarvisChatScreen")
        JarvisState.init(context)
        EngineProvider.init(context)
        
        // Check if Termux server is healthy
        val isHealthy = llamaClient.checkHealth()
        Log.d(TAG, "Termux server health: $isHealthy")
        
        Log.d(TAG, "Initialization complete")
    }

    // ================= PERMISSIONS =================
    LaunchedEffect(Unit) {
        if (activity == null) {
            Log.w(TAG, "Activity is null, cannot request permissions")
            return@LaunchedEffect
        }
        
        val perms = mutableListOf<String>()
        
        if (ContextCompat.checkSelfPermission(
                context, 
                android.Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            perms += android.Manifest.permission.READ_CONTACTS
        }
        
        if (ContextCompat.checkSelfPermission(
                context, 
                android.Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            perms += android.Manifest.permission.SEND_SMS
        }
        
        if (perms.isNotEmpty()) {
            Log.d(TAG, "Requesting permissions: ${perms.joinToString()}")
            ActivityCompat.requestPermissions(activity, perms.toTypedArray(), 2001)
        }
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
                        "J.A.R.V.I.S CHAT (Termux LLM)",
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
                                Log.d(TAG, "Mode changed to: ${mode.name}")
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
                    Log.d(TAG, "Work mode toggled")
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = JarvisBlue.copy(alpha = 0.7f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Toggle Work Mode", color = Color.Black)
            }

            HorizontalDivider(
                color = JarvisBlue.copy(alpha = 0.3f), 
                modifier = Modifier.padding(vertical = 8.dp)
            )

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
                            if (isProcessing) "Processing..." else "Chat with JARVIS…",
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

                // ===== SEND BUTTON =====
                IconButton(
                    onClick = {
                        val userText = input.trim()
                        
                        // Validation
                        if (userText.isEmpty()) {
                            Log.d(TAG, "❌ Ignoring empty input")
                            return@IconButton
                        }
                        
                        if (isProcessing) {
                            Log.d(TAG, "⚠️ Already processing, ignoring click")
                            return@IconButton
                        }

                        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                        Log.d(TAG, "🚀 SEND BUTTON CLICKED")
                        Log.d(TAG, "📝 User input: '$userText'")
                        
                        // Set processing state
                        isProcessing = true
                        
                        // Add user message
                        messages.add(ChatMessage(userText, isUser = true))
                        messages.add(ChatMessage("Processing…", isUser = false))

                        // Launch processing
                        scope.launch {
                            try {
                                Log.d(TAG, "⏳ Starting message processing")
                                
                                // First try command engine for simple commands
                                Log.d(TAG, "🔄 Trying command engine first")
                                val commandResult = EngineProvider.commandEngine.handle(userText)
                                Log.d(TAG, "🎯 Command result: $commandResult")
                                
                                val reply = when (commandResult) {
                                    is EngineResult.Success -> {
                                        Log.d(TAG, "✅ Command handled by engine")
                                        commandResult.reply
                                    }
                                    else -> {
                                        // Use Termux LLM server for general chat
                                        Log.d(TAG, "🔄 Using Termux LLM server")
                                        
                                        withContext(Dispatchers.IO) {
                                            try {
                                                // For general chat, we need a different prompt
                                                // The server is configured for command generation
                                                // So we'll use it creatively or add a chat endpoint
                                                
                                                // For now, just generate a response
                                                // You may want to add a /chat endpoint to the server
                                                val result = llamaClient.generateCommand(userText)
                                                
                                                if (result.success && result.command != null) {
                                                    Log.d(TAG, "✅ Termux LLM replied")
                                                    result.command
                                                } else {
                                                    Log.e(TAG, "❌ Termux LLM error: ${result.error}")
                                                    "❌ Error from Termux server:\n${result.error}"
                                                }
                                            } catch (e: Exception) {
                                                Log.e(TAG, "❌ Termux LLM exception", e)
                                                "❌ Error: ${e.message ?: "Unknown error"}"
                                            }
                                        }
                                    }
                                }
                                
                                // Update UI with reply
                                messages.removeLastOrNull()
                                messages.add(ChatMessage(reply, isUser = false))
                                input = "" // Clear input only on success
                                Log.d(TAG, "✅ PROCESSING COMPLETE")
                                
                            } catch (e: CancellationException) {
                                Log.w(TAG, "🚫 Processing cancelled")
                                throw e
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ Processing error", e)
                                messages.removeLastOrNull()
                                messages.add(ChatMessage(
                                    "❌ Error: ${e.message ?: "Unknown error occurred"}",
                                    isUser = false
                                ))
                            } finally {
                                Log.d(TAG, "🔄 Resetting processing state")
                                isProcessing = false
                                Log.d(TAG, "✅ Cleanup complete")
                            }
                        }
                    },
                    enabled = !isProcessing
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "Send",
                        tint = if (isProcessing) 
                            JarvisBlue.copy(alpha = 0.3f) 
                        else 
                            JarvisBlue
                    )
                }
            }
        }
    }
}
