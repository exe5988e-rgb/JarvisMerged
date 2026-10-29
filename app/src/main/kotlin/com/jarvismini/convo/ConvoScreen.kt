@file:OptIn(ExperimentalMaterial3Api::class)

package com.jarvismini.convo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CvMagenta = Color(0xFFE040FB)
private val CvDim = Color(0xFF4A0070)
private val CvBgDark = Color(0xFF080810)
private val CvCard = Color(0xFF0F0A1A)
private val CvMine = Color(0xFF1B0B2E)
private val CvGreen = Color(0xFF00FF88)
private val CvRed = Color(0xFFFF4466)
private val CvYellow = Color(0xFFFFCC00)
private val CvWhite70 = Color(0xB3FFFFFF)
private val CvWhite40 = Color(0x66FFFFFF)

/**
 * Multi-agent (Convo) tab: roster with presence, a polled timeline grouped by broadcast with read
 * receipts, a compose bar with recipient chips, filters, and settings (server URL, admin token,
 * notifications). The token is only ever passed to the view model; it is never logged or shown.
 */
@Composable
fun ConvoScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val factory = remember { ConvoViewModelFactory(context.applicationContext) }
    val vm: ConvoViewModel = viewModel<ConvoViewModel>(factory = factory)
    val state by vm.state.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    BackHandler(onBack = onBack)

    // Poll only while the screen is started; the notification service covers the background.
    DisposableEffect(lifecycleOwner, vm) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> vm.startPolling()
                Lifecycle.Event.ON_STOP -> vm.stopPolling()
                Lifecycle.Event.ON_RESUME -> {
                    ConvoNotifier.screenVisible = true
                    ConvoNotifier.clearAll(context)
                }
                Lifecycle.Event.ON_PAUSE -> ConvoNotifier.screenVisible = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            ConvoNotifier.screenVisible = false
            vm.stopPolling()
        }
    }

    val timeFormat = remember { SimpleDateFormat("d MMM HH:mm", Locale.getDefault()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(CvBgDark, Color(0xFF05020F)))),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp),
        ) {
            ConvoTopBar(onBack = onBack, onSettings = { showSettings = true })

            if (!state.hasToken) {
                ConvoBanner("No admin token yet. Tap the gear to set it up.", CvYellow)
            } else if (state.error != null) {
                ConvoBanner(state.error.orEmpty(), CvRed)
            }

            ConvoRoster(state)
            Spacer(Modifier.height(6.dp))
            ConvoFilters(state, vm)
            Spacer(Modifier.height(6.dp))

            ConvoTimeline(
                state = state,
                vm = vm,
                timeFormat = timeFormat,
                modifier = Modifier.weight(1f),
            )

            ConvoComposeBar(state, vm)
        }
    }

    state.selected?.let { bubble ->
        ModalBottomSheet(
            onDismissRequest = { vm.select(null) },
            containerColor = CvCard,
        ) {
            ConvoReceiptSheet(bubble, timeFormat)
        }
    }

    if (showSettings) {
        ConvoSettingsDialog(
            state = state,
            onDismiss = { showSettings = false },
            onSave = { url, token, notify ->
                vm.onSaveSettings(url, token)
                if (state.hasToken || token.isNotBlank()) ConvoNotifier.setEnabled(context, notify)
                showSettings = false
            },
        )
    }
}

@Composable
private fun ConvoTopBar(onBack: () -> Unit, onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, "Back", tint = CvMagenta)
        }
        Text(
            "MULTI-AGENT",
            fontSize = 18.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 4.sp,
            color = CvMagenta,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onSettings) {
            Icon(Icons.Default.Settings, "Convo settings", tint = CvWhite70, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ConvoBanner(text: String, color: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Warning, null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = color, fontSize = 12.sp)
    }
}

@Composable
private fun ConvoRoster(state: ConvoUiState) {
    val agents = state.agents.messageable()
    if (agents.isEmpty()) return
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(agents, key = { it.id }) { agent ->
            Row(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, CvDim, RoundedCornerShape(16.dp))
                    .background(CvCard)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (agent.online) CvGreen else CvWhite40),
                )
                Spacer(Modifier.width(6.dp))
                Column {
                    Text(
                        agent.displayName.ifBlank { agent.id },
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        if (agent.online) "online" else formatLastSeen(agent.lastSeen, state.now),
                        color = CvWhite40,
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConvoChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Text(
        label,
        color = if (selected) CvMagenta else CvWhite70,
        fontSize = 12.sp,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) CvDim else Color.Transparent)
            .border(1.dp, if (selected) CvMagenta else CvDim, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun ConvoFilters(state: ConvoUiState, vm: ConvoViewModel) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(ConvoFilter.values().toList(), key = { it.name }) { f ->
            ConvoChip(f.label, state.filter == f) { vm.setFilter(f) }
        }
    }
}

@Composable
private fun ConvoTimeline(
    state: ConvoUiState,
    vm: ConvoViewModel,
    timeFormat: SimpleDateFormat,
    modifier: Modifier,
) {
    val visible = state.visible
    val listState = rememberLazyListState()
    val lastKey = visible.lastOrNull()?.key
    val headerOffset = if (state.hasOlder) 1 else 0

    LaunchedEffect(lastKey) {
        if (visible.isNotEmpty()) listState.animateScrollToItem(visible.lastIndex + headerOffset)
    }

    Box(modifier.fillMaxWidth()) {
        if (visible.isEmpty()) {
            Text(
                when {
                    !state.hasToken -> "Set the admin token to load messages."
                    state.filter != ConvoFilter.All -> "Nothing matches this filter."
                    else -> "No messages yet."
                },
                color = CvWhite40,
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.hasOlder) {
                    item(key = "older") {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            TextButton(onClick = vm::onLoadOlder, enabled = !state.loadingOlder) {
                                Text(
                                    if (state.loadingOlder) "Loading…" else "Load older messages",
                                    color = CvMagenta,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }
                items(visible, key = { it.key }) { bubble ->
                    ConvoBubbleRow(bubble, timeFormat) { vm.select(bubble.key) }
                }
            }
        }
    }
}

@Composable
private fun ConvoBubbleRow(bubble: ConvoBubble, timeFormat: SimpleDateFormat, onClick: () -> Unit) {
    val mine = bubble.senderId == ADMIN_ID
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .clip(shape)
                .background(if (mine) CvMine else CvCard)
                .border(1.dp, if (bubble.hasUnread) CvYellow.copy(alpha = 0.35f) else CvDim, shape)
                .clickable(onClick = onClick)
                .padding(10.dp),
        ) {
            if (!mine) {
                Text(
                    bubble.senderId,
                    color = CvMagenta,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Text(bubble.text, color = Color.White, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                val to = recipientLabel(bubble)
                Text(
                    (if (to.isEmpty()) "" else "to $to · ") + timeFormat.format(Date(bubble.createdAt)),
                    color = CvWhite40,
                    fontSize = 10.sp,
                )
                if (bubble.receipts.isNotEmpty()) {
                    Spacer(Modifier.width(4.dp))
                    val read = bubble.state == ReceiptState.Read
                    Icon(
                        if (read) Icons.Default.DoneAll else Icons.Default.Done,
                        contentDescription = if (read) "Read" else "Delivered",
                        tint = if (read) CvGreen else CvWhite40,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ConvoComposeBar(state: ConvoUiState, vm: ConvoViewModel) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item(key = "all") {
                ConvoChip("All", state.recipients.isEmpty()) { vm.allRecipients() }
            }
            items(state.agents.messageable(), key = { it.id }) { agent ->
                ConvoChip(
                    agent.displayName.ifBlank { agent.id },
                    state.recipients.contains(agent.id),
                ) { vm.toggleRecipient(agent.id) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.draft,
                onValueChange = vm::onDraft,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        if (state.recipients.isEmpty()) "Message all agents" else "Message ${state.recipients.sorted().joinToString(", ")}",
                        fontSize = 13.sp,
                    )
                },
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = CvMagenta,
                    unfocusedBorderColor = CvDim,
                    cursorColor = CvMagenta,
                    focusedPlaceholderColor = CvWhite40,
                    unfocusedPlaceholderColor = CvWhite40,
                ),
            )
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = vm::onSend, enabled = state.draft.isNotBlank() && !state.sending) {
                if (state.sending) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = CvMagenta, strokeWidth = 2.dp)
                } else {
                    Icon(
                        Icons.Default.Send,
                        "Send",
                        tint = if (state.draft.isNotBlank()) CvMagenta else CvWhite40,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConvoReceiptSheet(bubble: ConvoBubble, timeFormat: SimpleDateFormat) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
        Text(
            "${bubble.senderId} · ${timeFormat.format(Date(bubble.createdAt))}",
            color = CvMagenta,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.height(6.dp))
        Text(bubble.text, color = Color.White, fontSize = 15.sp)
        Spacer(Modifier.height(16.dp))

        Text("Read by", color = CvGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        val read = bubble.receipts.filter { it.isRead }
        if (read.isEmpty()) {
            Text("Nobody yet", color = CvWhite40, fontSize = 13.sp)
        } else {
            for (r in read) {
                val at = r.readAt?.let { " · " + timeFormat.format(Date(it)) }.orEmpty()
                Text(r.agentId + at, color = CvWhite70, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Unread", color = CvYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        val unread = bubble.unreadBy
        Text(
            if (unread.isEmpty()) "Everyone has read this" else unread.joinToString(", "),
            color = if (unread.isEmpty()) CvWhite40 else CvWhite70,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun ConvoSettingsDialog(
    state: ConvoUiState,
    onDismiss: () -> Unit,
    onSave: (url: String, token: String, notify: Boolean) -> Unit,
) {
    val context = LocalContext.current
    var url by remember { mutableStateOf(state.baseUrl) }
    var token by remember { mutableStateOf("") }
    var notify by remember {
        mutableStateOf(if (ConvoNotifier.hasChoice(context)) ConvoNotifier.isEnabled(context) else true)
    }
    val systemAllows = remember { NotificationManagerCompat.from(context).areNotificationsEnabled() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Convo settings") },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Server URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text(if (state.hasToken) "Admin token (saved, blank keeps it)" else "Admin token") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Message notifications", modifier = Modifier.weight(1f))
                    Switch(checked = notify, onCheckedChange = { notify = it })
                }
                if (notify && !systemAllows) {
                    Text(
                        "Notifications are blocked for this app in system settings.",
                        color = CvRed,
                        fontSize = 12.sp,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(url, token, notify) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
