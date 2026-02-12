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
import com.jarvismini.llm.TermuxLlamaClient
import com.jarvismini.ui.components.GridBackground
import com.jarvismini.ui.ChatMessage
import com.jarvismini.ui.theme.JarvisBlue
import kotlinx.coroutines.*

private const val TAG = "JarvisChatScreen"

/**
 * Main chat screen for interacting with J.A.R.V.I.S.
 * 
 * FIXED VERSION: Always uses /chat_sync endpoint, bypasses buggy command engine
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
    val llamaClient = remember { TermuxLlamaClient() }

    // ================= INITIALIZATION =================
    LaunchedEffect(Unit) {
        Log.d(TAG, "Initializing JarvisChatScreen")
        JarvisState.init(context)
        
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
                        
                        isProcessing = true
                        messages.add(ChatMessage(userText, isUser = true))
                        messages.add(ChatMessage("Processing…", isUser = false))

                        scope.launch {
                            try {
                                Log.d(TAG, "⏳ Starting message processing")
                                
                                // =================================================================
                                // CRITICAL FIX: Skip the buggy command engine, go straight to chat
                                // =================================================================
                                // The command engine was incorrectly calling generateCommand()
                                // which hits /generate_sync instead of /chat_sync
                                //
                                // OLD CODE (BUGGY):
                                // val commandResult = EngineProvider.commandEngine.handle(userText)
                                //
                                // NEW CODE: Direct to chat endpoint
                                // =================================================================
                                
                                Log.d(TAG, "🗨️ Using Termux LLM CHAT endpoint (/chat_sync)")
                                
                                val reply = withContext(Dispatchers.IO) {
                                    try {
                                        // IMPORTANT: Use chat() for conversations
                                        // This hits /chat_sync endpoint with proper chat configuration
                                        val result = llamaClient.chat(
                                            query = userText,
                                            timeoutSeconds = 150  // 2.5 min (within server's 120s limit + buffer)
                                        )
                                        
                                        if (result.success && result.response != null) {
                                            Log.d(TAG, "✅ Chat response: ${result.response.take(100)}...")
                                            result.response
                                        } else {
                                            val errorMsg = result.error ?: "Unknown error"
                                            Log.e(TAG, "❌ Chat error: $errorMsg")
                                            "❌ $errorMsg"
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "❌ Chat exception", e)
                                        "❌ Error: ${e.message ?: "Unknown error"}"
                                    }
                                }
                                
                                // Update UI with reply
                                messages.removeLastOrNull()
                                messages.add(ChatMessage(reply, isUser = false))
                                input = ""
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
