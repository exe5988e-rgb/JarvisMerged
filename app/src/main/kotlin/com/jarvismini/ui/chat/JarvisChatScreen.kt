package com.jarvismini.ui.chat

import android.app.Activity
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jarvismini.core.JarvisMode
import com.jarvismini.core.JarvisState
import com.jarvismini.core.ModeCommands
import com.jarvismini.core.announce.AnnouncementEngine
import com.jarvismini.core.WorkModeManager
import com.jarvismini.llm.TermuxLlamaClient
import com.jarvismini.ui.components.GridBackground
import com.jarvismini.ui.ChatMessage
import com.jarvismini.ui.theme.JarvisBlue
import com.jarvismini.ui.theme.GlassSurface
import com.jarvismini.ui.theme.JarvisMotion
import com.jarvismini.ui.theme.JarvisRadius
import com.jarvismini.ui.theme.JarvisTextPrimary
import com.jarvismini.ui.theme.JarvisTextSecondary
import com.jarvismini.ui.theme.pressScale
import com.jarvismini.ui.theme.rememberMotionEnabled
import kotlinx.coroutines.*

private const val TAG = "JarvisChatScreen"

/** A bubble younger than this plays its enter animation; older ones (scrolled back into view) do not. */
private const val FRESH_NANOS = 600_000_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JarvisChatScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val motionEnabled = rememberMotionEnabled()

    // ================= TERMUX CLIENT =================
    val llamaClient = remember { TermuxLlamaClient(context) }
    val openRouter = remember { com.jarvismini.llm.OpenRouterClient(context) }

    // ================= INIT =================
    LaunchedEffect(Unit) {
        Log.d(TAG, "Initializing JarvisChatScreen")
        JarvisState.init(context)

        val isHealthy = llamaClient.checkHealth()
        Log.d(TAG, "Termux server health: $isHealthy")
    }

    // ================= PERMISSIONS =================
    LaunchedEffect(Unit) {
        if (activity == null) return@LaunchedEffect

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
    val listState = rememberLazyListState()
    val toggleSource = remember { MutableInteractionSource() }
    val sendSource = remember { MutableInteractionSource() }

    // With stable keys the list stays anchored on the old newest item, so a new message would sit
    // below the fold: follow the conversation unless the user has scrolled up to read history.
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && listState.firstVisibleItemIndex <= 2) {
            if (motionEnabled) listState.animateScrollToItem(0) else listState.scrollToItem(0)
        }
    }

    // ================= SEND =================
    fun send() {
        val userText = input.trim()
        if (userText.isEmpty() || isProcessing) return

        // Persona / lockdown / digest commands run on-device, no LLM server needed.
        ModeCommands.handle(userText)?.let { local ->
            messages.add(ChatMessage(userText, isUser = true))
            messages.add(ChatMessage(local, isUser = false))
            AnnouncementEngine.respond(local)
            input = ""
            return
        }

        isProcessing = true
        messages.add(ChatMessage(userText, isUser = true))
        messages.add(ChatMessage("Processing…", isUser = false))

        scope.launch {
            try {
                val reply = withContext(Dispatchers.IO) {
                    val orReply = if (openRouter.isConfigured())
                        openRouter.chat(userText).getOrNull() else null
                    if (orReply != null) {
                        orReply
                    } else {
                        val result = llamaClient.chat(
                            query = userText,
                            timeoutSeconds = 150
                        )
                        if (result.success && result.response != null)
                            result.response
                        else
                            "❌ ${result.error ?: "Unknown error"}"
                    }
                }

                messages.removeLastOrNull()
                messages.add(ChatMessage(reply, isUser = false))
                if (!reply.startsWith("❌")) AnnouncementEngine.respond(reply)
                input = ""

            } catch (e: Exception) {
                messages.removeLastOrNull()
                messages.add(
                    ChatMessage(
                        "❌ ${e.message ?: "Unknown error"}",
                        isUser = false
                    )
                )
            } finally {
                isProcessing = false
            }
        }
    }

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

        Column(
            modifier = Modifier
                .fillMaxSize()
                // IME first, then the nav bar: the keyboard inset already covers the nav bar, so
                // the input bar neither hides under the gesture bar nor gets double padding.
                .imePadding()
                .navigationBarsPadding()
                .padding(12.dp)
        ) {

            // ===== HEADER =====
            TopAppBar(
                title = {
                    Text(
                        "J.A.R.V.I.S CHAT",
                        color = JarvisBlue,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.semantics { heading() }
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
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = JarvisBlue,
                        unfocusedTextColor = JarvisBlue,
                        focusedLabelColor = JarvisBlue,
                        unfocusedLabelColor = JarvisBlue,
                        focusedIndicatorColor = JarvisBlue,
                        unfocusedIndicatorColor = JarvisBlue
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    modes.forEach { mode ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    mode.name,
                                    color = JarvisBlue,
                                    fontFamily = FontFamily.Monospace
                                )
                            },
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

            // ===== WORK MODE TOGGLE BUTTON =====
            Button(
                onClick = {
                    WorkModeManager.toggle(context)
                    currentMode = JarvisState.currentMode
                    Log.d(TAG, "Work mode toggled")
                },
                interactionSource = toggleSource,
                colors = ButtonDefaults.buttonColors(
                    containerColor = JarvisBlue.copy(alpha = 0.7f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .pressScale(toggleSource)
            ) {
                Text("Toggle Work Mode", color = Color.Black)
            }

            HorizontalDivider(
                color = JarvisBlue.copy(alpha = 0.3f),
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // ===== CHAT LOG =====
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (messages.isEmpty()) {
                    Text(
                        text = "No messages yet. Ask JARVIS anything.",
                        color = JarvisTextSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    val latestId = messages.last().id
                    // Plain (non-blurred) bubbles: glass is not used inside lists. User right, Jarvis left.
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        reverseLayout = true,
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(messages.reversed(), key = { it.id }) { msg ->
                            val isLatestReply = !msg.isUser && msg.id == latestId
                            ChatBubble(
                                msg = msg,
                                thinking = isLatestReply && isProcessing,
                                announce = isLatestReply
                            )
                        }
                    }
                }
            }

            // ===== INPUT BAR =====
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(JarvisRadius.Card),
                cornerTicks = true
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        // readOnly (not disabled) keeps focus and the keyboard open between turns.
                        readOnly = isProcessing,
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send() }),
                        placeholder = {
                            Text(
                                if (isProcessing) "Processing..."
                                else "Chat with JARVIS…",
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

                    IconButton(
                        onClick = { send() },
                        enabled = !isProcessing,
                        interactionSource = sendSource,
                        modifier = Modifier
                            .pressScale(sendSource)
                            .semantics { if (isProcessing) stateDescription = "Waiting for reply" }
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
}

/**
 * One message bubble. Speaker label is read with the text ("JARVIS, ..."), the newest reply is a
 * polite live region, and only a freshly created bubble animates in (fade + 8dp rise, Normal/Standard).
 */
@Composable
private fun ChatBubble(msg: ChatMessage, thinking: Boolean, announce: Boolean) {
    val motionEnabled = rememberMotionEnabled()
    val isFresh = remember { System.nanoTime() - msg.createdAtNanos < FRESH_NANOS }
    var shown by remember { mutableStateOf(!(motionEnabled && isFresh)) }
    LaunchedEffect(Unit) { shown = true }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(JarvisMotion.Duration.Normal, easing = JarvisMotion.Standard),
        label = "bubbleEnter"
    )
    val rise = with(LocalDensity.current) { JarvisMotion.Distance.Small.toPx() }
    val maxBubble = (LocalConfiguration.current.screenWidthDp * 0.88f).dp

    // Chat-tail corner (4dp) on the speaker's side; the other three follow the card radius.
    val radius = JarvisRadius.Card
    val tail = 4.dp
    val shape = if (msg.isUser)
        RoundedCornerShape(radius, radius, tail, radius)
    else
        RoundedCornerShape(radius, radius, radius, tail)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .graphicsLayer {
                alpha = progress
                translationY = (1f - progress) * rise
            },
        contentAlignment = if (msg.isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = maxBubble)
                .background(JarvisBlue.copy(alpha = if (msg.isUser) 0.16f else 0.07f), shape)
                .border(1.dp, JarvisBlue.copy(alpha = if (msg.isUser) 0.5f else 0.25f), shape)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .semantics(mergeDescendants = true) {
                    if (thinking) {
                        contentDescription = "Jarvis is thinking"
                        liveRegion = LiveRegionMode.Polite
                    } else if (announce) {
                        liveRegion = LiveRegionMode.Polite
                    }
                }
        ) {
            Text(
                text = if (msg.isUser) "YOU" else "JARVIS",
                color = JarvisTextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            if (thinking) {
                ThinkingDots()
            } else {
                SelectionContainer {
                    Text(
                        text = msg.text,
                        color = JarvisTextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

/** Three pulsing dots while a reply is pending; a static ellipsis when motion is off. */
@Composable
private fun ThinkingDots() {
    if (rememberMotionEnabled()) {
        AnimatedDots()
    } else {
        Text("…", color = JarvisTextPrimary, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
    }
}

@Composable
private fun AnimatedDots() {
    val transition = rememberInfiniteTransition(label = "thinkingDots")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(JarvisMotion.Duration.Slow * 3, easing = LinearEasing)
        ),
        label = "thinkingPhase"
    )
    Row(
        modifier = Modifier.height(20.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { i ->
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .graphicsLayer {
                        // Cyclic distance from this dot's turn; read in the draw phase, no recomposition.
                        val x = (phase - i + 3f) % 3f
                        alpha = 0.3f + 0.7f * maxOf(0f, 1f - minOf(x, 3f - x))
                    }
                    .background(JarvisBlue, CircleShape)
            )
        }
    }
}
