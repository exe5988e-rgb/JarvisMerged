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
import com.jarvismini.ui.components.GridBackground
import kotlinx.coroutines.*

// UI Constants
private val JarvisBlue = Color(0xFF00E0FF)
private const val TAG = "JarvisChatScreen"

// ChatMessage data class (if not already defined elsewhere)
data class ChatMessage(
    val text: String,
    val isUser: Boolean
)

/**
 * Main chat screen for interacting with J.A.R.V.I.S.
 * 
 * ✅ FIXED: Input clearing moved to AFTER successful processing
 * ✅ FIXED: Added 30-second timeout protection
 * ✅ FIXED: Enhanced logging for debugging
 * ✅ FIXED: Input preserved on error for retry
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JarvisChatScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    // ================= INITIALIZATION =================
    LaunchedEffect(Unit) {
        Log.d(TAG, "Initializing JarvisChatScreen")
        JarvisState.init(context)
        EngineProvider.init(context)
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

                // ===== SEND BUTTON =====
                // ✅ FIXED: Input cleared AFTER success, with timeout protection
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
                        Log.d(TAG, "🧵 Main thread: ${Thread.currentThread().name}")
                        
                        // ✅ FIX: Set processing state but DON'T clear input yet
                        // Input stays visible (disabled) during processing
                        isProcessing = true
                        
                        // Add user message to chat
                        messages.add(ChatMessage(userText, isUser = true))
                        
                        // Add temporary "thinking" message
                        messages.add(ChatMessage("Processing…", isUser = false))

                        // Launch coroutine with proper error handling and timeout
                        scope.launch {
                            try {
                                Log.d(TAG, "⏳ Starting message processing in coroutine")
                                Log.d(TAG, "🧵 Coroutine thread: ${Thread.currentThread().name}")
                                
                                // ✅ FIX: Add 30-second timeout to prevent infinite hang
                                val reply = withTimeout(30000L) {
                                    // Try command engine first
                                    val result = EngineProvider.commandEngine.handle(userText)
                                    Log.d(TAG, "🎯 Command engine result: $result")
                                    
                                    when (result) {
                                        is EngineResult.Success -> {
                                            Log.d(TAG, "✅ Command handled successfully")
                                            result.reply
                                        }
                                        else -> {
                                            Log.d(TAG, "🔄 Command not handled, calling LLM")
                                            
                                            // Call LLM on IO dispatcher
                                            withContext(Dispatchers.IO) {
                                                Log.d(TAG, "🧵 LLM thread: ${Thread.currentThread().name}")
                                                val llmReply = EngineProvider.llmEngine.generateReply(userText)
                                                Log.d(TAG, "✅ LLM replied: ${llmReply.take(50)}...")
                                                llmReply
                                            }
                                        }
                                    }
                                }
                                
                                Log.d(TAG, "🔄 Updating UI with reply")
                                
                                // Remove "Processing..." message
                                messages.removeLastOrNull()
                                
                                // Add actual reply
                                messages.add(ChatMessage(reply, isUser = false))
                                
                                // ✅ FIX: Clear input only AFTER successful processing
                                input = ""
                                
                                Log.d(TAG, "✅ MESSAGE PROCESSING COMPLETE")
                                Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                                
                            } catch (e: TimeoutCancellationException) {
                                Log.e(TAG, "⏱️ TIMEOUT: Operation took longer than 30 seconds")
                                
                                // Remove "Processing..." message
                                messages.removeLastOrNull()
                                
                                // Add timeout message
                                messages.add(ChatMessage(
                                    "⏱️ Request timed out after 30 seconds. The model may be loading or the prompt is too complex. Try:\n" +
                                    "• A shorter message\n" +
                                    "• Checking if models are in /storage/emulated/0/JarvisModels/\n" +
                                    "• Restarting the app",
                                    isUser = false
                                ))
                                
                                // ✅ FIX: Keep input on timeout so user can retry or modify
                                Log.d(TAG, "ℹ️ Input preserved for retry: '$userText'")
                                
                            } catch (e: CancellationException) {
                                Log.e(TAG, "🚫 CANCELLED: Coroutine was cancelled")
                                
                                // Remove "Processing..." message
                                messages.removeLastOrNull()
                                
                                // Add cancellation message
                                messages.add(ChatMessage(
                                    "🚫 Processing cancelled. Try again.",
                                    isUser = false
                                ))
                                
                                // Re-throw to properly handle cancellation
                                throw e
                                
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ ERROR PROCESSING MESSAGE", e)
                                Log.e(TAG, "Error type: ${e.javaClass.simpleName}")
                                Log.e(TAG, "Error message: ${e.message}")
                                
                                // Remove "Processing..." message
                                messages.removeLastOrNull()
                                
                                // Add detailed error message
                                val errorMsg = when {
                                    e.message?.contains("timeout", ignoreCase = true) == true ->
                                        "⏱️ Timeout. The request took too long. Try a shorter prompt."
                                        
                                    e.message?.contains("model", ignoreCase = true) == true ->
                                        "🤖 Model error. Check if .gguf models are in:\n/storage/emulated/0/JarvisModels/"
                                        
                                    e.message?.contains("permission", ignoreCase = true) == true ->
                                        "🔒 Permission error. Grant storage access in Settings."
                                        
                                    e.message?.contains("memory", ignoreCase = true) == true ->
                                        "💾 Memory error. Try a smaller model or restart the app."
                                        
                                    else ->
                                        "❌ Error: ${e.message ?: "Unknown error occurred"}\n\nCheck logs for details."
                                }
                                
                                messages.add(ChatMessage(errorMsg, isUser = false))
                                
                                // ✅ FIX: Keep input on error so user can retry
                                Log.d(TAG, "ℹ️ Input preserved for retry: '$userText'")
                                
                            } finally {
                                Log.d(TAG, "🔄 Resetting processing state")
                                Log.d(TAG, "isProcessing: true -> false")
                                isProcessing = false
                                Log.d(TAG, "✅ Processing state reset complete")
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
