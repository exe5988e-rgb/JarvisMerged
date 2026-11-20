package com.jarvismini.ui.llm

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jarvismini.ui.theme.GlassSurface
import com.jarvismini.ui.theme.JarvisBackground
import com.jarvismini.ui.theme.JarvisBlack
import com.jarvismini.ui.theme.JarvisCyan
import com.jarvismini.ui.theme.JarvisMotion
import com.jarvismini.ui.theme.JarvisOffline
import com.jarvismini.ui.theme.JarvisOnline
import com.jarvismini.ui.theme.JarvisRadius
import com.jarvismini.ui.theme.JarvisTextPrimary
import com.jarvismini.ui.theme.JarvisTextSecondary
import com.jarvismini.ui.theme.pressScale
import com.jarvismini.ui.theme.rememberMotionEnabled

/**
 * Main screen for Termux command generation
 *
 * ✅ FIXED: ViewModel moved to separate file
 * Now uses FIFO for all command execution (no more Termux intent)
 *
 * UI: HUD background, glass on the query cluster and the confirm/result card only; the command
 * output and the history list stay flat and solid so they remain readable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermuxCommandScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel = remember { TermuxCommandViewModel(context) }

    val uiState by viewModel.uiState.collectAsState()
    val serverStatus by viewModel.serverStatus.collectAsState()
    val commandHistory by viewModel.commandHistory.collectAsState()

    var currentQuery by remember { mutableStateOf("") }
    var showHistory by remember { mutableStateOf(false) }

    // Slide/fade between Generator and History; instant when animations are off or battery saver is on.
    val motionEnabled = rememberMotionEnabled()
    val slide = JarvisMotion.Duration.Normal
    val easing = JarvisMotion.Standard

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(JarvisBlack, JarvisBackground, JarvisBlack)))
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Termux Command Generator",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.semantics { heading() }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, "Back")
                        }
                    },
                    actions = {
                        ServerStatusChip(
                            status = serverStatus,
                            onRefresh = { viewModel.checkServerStatus() }
                        )

                        IconButton(onClick = { showHistory = !showHistory }) {
                            Icon(
                                if (showHistory) Icons.Default.Clear else Icons.Default.History,
                                if (showHistory) "Close command history" else "Open command history"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = JarvisCyan.copy(alpha = 0.08f),
                        titleContentColor = JarvisCyan,
                        navigationIconContentColor = JarvisCyan,
                        actionIconContentColor = JarvisCyan
                    )
                )
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = !showHistory,
                    enter = if (motionEnabled) {
                        fadeIn(tween(slide, easing = easing)) + slideInHorizontally(tween(slide, easing = easing)) { -it / 8 }
                    } else EnterTransition.None,
                    exit = if (motionEnabled) {
                        fadeOut(tween(JarvisMotion.Duration.Fast, easing = JarvisMotion.Exit)) +
                            slideOutHorizontally(tween(JarvisMotion.Duration.Fast, easing = JarvisMotion.Exit)) { -it / 8 }
                    } else ExitTransition.None
                ) {
                    MainContent(
                        modifier = Modifier.padding(padding),
                        uiState = uiState,
                        currentQuery = currentQuery,
                        onQueryChange = { currentQuery = it },
                        onGenerateCommand = {
                            if (currentQuery.isNotBlank()) {
                                viewModel.generateCommand(currentQuery)
                            }
                        },
                        onUpdateCommand = { newCommand ->
                            viewModel.updateCommand(newCommand)
                        },
                        onExecuteCommand = { command, isEdited ->
                            viewModel.executeCommand(command, currentQuery, isEdited)
                            currentQuery = ""
                        },
                        onCancelCommand = { viewModel.cancelCommand() },
                        onReset = { viewModel.reset() }
                    )
                }

                AnimatedVisibility(
                    visible = showHistory,
                    enter = if (motionEnabled) {
                        fadeIn(tween(slide, easing = easing)) + slideInHorizontally(tween(slide, easing = easing)) { it / 8 }
                    } else EnterTransition.None,
                    exit = if (motionEnabled) {
                        fadeOut(tween(JarvisMotion.Duration.Fast, easing = JarvisMotion.Exit)) +
                            slideOutHorizontally(tween(JarvisMotion.Duration.Fast, easing = JarvisMotion.Exit)) { it / 8 }
                    } else ExitTransition.None
                ) {
                    HistoryView(
                        modifier = Modifier.padding(padding),
                        history = commandHistory,
                        onSelectCommand = { item ->
                            currentQuery = item.query
                            showHistory = false
                        },
                        onClearHistory = { viewModel.clearHistory() }
                    )
                }
            }
        }
    }
}

// ─── Shared building blocks ───────────────────────────────────────────────────

/** Filled HUD button: >= 48dp tall, spring press feedback, dark label on cyan for contrast. */
@Composable
private fun HudButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    container: Color = JarvisCyan,
    content: @Composable RowScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 48.dp)
            .pressScale(interaction),
        enabled = enabled,
        interactionSource = interaction,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = JarvisBackground,
            disabledContainerColor = container.copy(alpha = 0.2f),
            disabledContentColor = JarvisTextSecondary.copy(alpha = 0.6f)
        ),
        content = content
    )
}

/** Centered progress message. The text is announced politely; the spinner is dropped under reduced motion. */
@Composable
private fun ProgressState(message: String, color: Color) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        ) {
            if (rememberMotionEnabled()) {
                CircularProgressIndicator(
                    color = color,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                color = color,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ServerStatusChip(
    status: TermuxCommandViewModel.ServerStatus,
    onRefresh: () -> Unit
) {
    val (text, color) = when (status) {
        is TermuxCommandViewModel.ServerStatus.Online -> "Online" to JarvisOnline
        is TermuxCommandViewModel.ServerStatus.Offline -> "Offline" to JarvisOffline
        is TermuxCommandViewModel.ServerStatus.Checking -> "Checking..." to JarvisCyan
        is TermuxCommandViewModel.ServerStatus.Unknown -> "Unknown" to JarvisTextSecondary
    }

    // State is carried by the word as well as the dot colour (not colour alone).
    AssistChip(
        onClick = onRefresh,
        modifier = Modifier.semantics { contentDescription = "Server $text. Tap to refresh." },
        label = { Text(text, fontSize = MaterialTheme.typography.bodySmall.fontSize) },
        leadingIcon = {
            Icon(
                Icons.Default.Circle,
                null,
                modifier = Modifier.size(8.dp),
                tint = color
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = color.copy(alpha = 0.2f),
            labelColor = JarvisTextPrimary
        )
    )
}

@Composable
private fun MainContent(
    modifier: Modifier = Modifier,
    uiState: TermuxCommandViewModel.LlamaUiState,
    currentQuery: String,
    onQueryChange: (String) -> Unit,
    onGenerateCommand: () -> Unit,
    onUpdateCommand: (String) -> Unit,
    onExecuteCommand: (String, Boolean) -> Unit,
    onCancelCommand: () -> Unit,
    onReset: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        QueryInputSection(
            query = currentQuery,
            onQueryChange = onQueryChange,
            onGenerate = onGenerateCommand,
            enabled = uiState is TermuxCommandViewModel.LlamaUiState.Idle
        )

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (uiState) {
                is TermuxCommandViewModel.LlamaUiState.Idle -> IdleState()
                is TermuxCommandViewModel.LlamaUiState.CheckingServer -> CheckingServerState()
                is TermuxCommandViewModel.LlamaUiState.Generating -> GeneratingState()
                is TermuxCommandViewModel.LlamaUiState.WaitingConfirmation -> {
                    EditableConfirmationState(
                        command = uiState.command,
                        isEdited = uiState.isEdited,
                        onCommandChange = onUpdateCommand,
                        onExecute = { command: String, isEdited: Boolean ->
                            onExecuteCommand(command, isEdited)
                        },
                        onCancel = onCancelCommand
                    )
                }
                is TermuxCommandViewModel.LlamaUiState.Executing -> ExecutingState()
                is TermuxCommandViewModel.LlamaUiState.Success -> {
                    SuccessState(
                        output = uiState.output,
                        exitCode = uiState.exitCode,
                        executionMethod = uiState.executionMethod,
                        onReset = onReset
                    )
                }
                is TermuxCommandViewModel.LlamaUiState.Error -> {
                    ErrorState(
                        message = uiState.message,
                        onRetry = onReset
                    )
                }
            }
        }
    }
}

@Composable
private fun QueryInputSection(
    query: String,
    onQueryChange: (String) -> Unit,
    onGenerate: () -> Unit,
    enabled: Boolean
) {
    // The query field + Generate button are the one control cluster on this screen: glass.
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(JarvisRadius.Card)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("What do you want to do in Termux?") },
                placeholder = { Text("e.g., update all packages, list files, check disk space") },
                enabled = enabled,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = JarvisCyan,
                    unfocusedBorderColor = JarvisCyan.copy(alpha = 0.5f),
                    cursorColor = JarvisCyan,
                    focusedTextColor = JarvisTextPrimary,
                    unfocusedTextColor = JarvisTextPrimary,
                    focusedLabelColor = JarvisCyan,
                    unfocusedLabelColor = JarvisTextSecondary,
                    focusedPlaceholderColor = JarvisTextSecondary.copy(alpha = 0.8f),
                    unfocusedPlaceholderColor = JarvisTextSecondary.copy(alpha = 0.8f)
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            HudButton(
                onClick = onGenerate,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled && query.isNotBlank()
            ) {
                Icon(Icons.Default.AutoAwesome, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Generate Command")
            }
        }
    }
}

// ✅ FIX: Added missing IdleState Composable
@Composable
private fun IdleState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Enter a query to generate a Termux command",
            style = MaterialTheme.typography.bodyLarge,
            color = JarvisTextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

// ✅ FIX: Added missing CheckingServerState Composable
@Composable
private fun CheckingServerState() {
    ProgressState("Checking server status...", JarvisCyan)
}

// ✅ FIX: Added missing GeneratingState Composable
@Composable
private fun GeneratingState() {
    ProgressState("Generating command...", JarvisCyan)
}

/**
 * Editable confirmation state
 * Shows TextField for command editing
 */
@Composable
private fun EditableConfirmationState(
    command: String,
    isEdited: Boolean,
    onCommandChange: (String) -> Unit,
    onExecute: (String, Boolean) -> Unit,
    onCancel: () -> Unit
) {
    var editableCommand by remember { mutableStateOf(command) }

    // Update when command changes from ViewModel
    LaunchedEffect(command) {
        editableCommand = command
    }

    // Edited commands switch the card from cyan to green; the title text says so too.
    val accent = if (isEdited) JarvisOnline else JarvisCyan

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(JarvisRadius.Card),
            tint = accent
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isEdited) Icons.Default.Edit else Icons.Default.Warning,
                        null,
                        tint = if (isEdited) JarvisOnline else Color.Yellow,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isEdited) "Edited Command" else "Confirm Command Execution",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                        modifier = Modifier.semantics { heading() }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    if (isEdited)
                        "You've edited the command. Review and execute when ready."
                    else
                        "Review the generated command and edit if needed before execution.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = JarvisTextSecondary
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = editableCommand,
                    onValueChange = {
                        editableCommand = it
                        onCommandChange(it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(JarvisRadius.Small))
                        .background(JarvisBlack.copy(alpha = 0.3f)),
                    label = { Text("Command") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        color = if (isEdited) JarvisOnline else JarvisTextPrimary
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accent,
                        unfocusedBorderColor = accent.copy(alpha = 0.5f),
                        cursorColor = accent,
                        focusedLabelColor = accent,
                        unfocusedLabelColor = JarvisTextSecondary
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val cancelInteraction = remember { MutableInteractionSource() }
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .pressScale(cancelInteraction),
                        interactionSource = cancelInteraction,
                        border = BorderStroke(1.dp, JarvisOffline.copy(alpha = 0.7f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = JarvisOffline
                        )
                    ) {
                        Icon(Icons.Default.Close, null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cancel")
                    }

                    HudButton(
                        onClick = { onExecute(editableCommand, isEdited) },
                        modifier = Modifier.weight(1f),
                        container = JarvisOnline
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Execute")
                    }
                }
            }
        }
    }
}

@Composable
private fun ExecutingState() {
    ProgressState("Executing command...", JarvisOnline)
}

@Composable
private fun SuccessState(
    output: String,
    exitCode: Int,
    executionMethod: String?,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                // the result replaces the progress message: announce it
                .semantics { liveRegion = LiveRegionMode.Polite },
            shape = RoundedCornerShape(JarvisRadius.Card),
            tint = JarvisOnline
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CheckCircle,
                        null,
                        tint = JarvisOnline,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Command Executed Successfully",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = JarvisOnline,
                        modifier = Modifier.semantics { heading() }
                    )
                }

                if (executionMethod != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Execution method: $executionMethod",
                        style = MaterialTheme.typography.bodySmall,
                        color = JarvisTextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Exit Code: $exitCode",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    color = JarvisOnline
                )

                if (output.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "Output:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = JarvisTextPrimary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Terminal output stays flat and solid (no glass). Height-capped so the inner
                    // scroll works, and selectable so it can be copied.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .clip(RoundedCornerShape(JarvisRadius.Small))
                            .background(JarvisBlack.copy(alpha = 0.6f))
                            .padding(12.dp)
                    ) {
                        SelectionContainer {
                            Text(
                                output,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                color = JarvisTextPrimary,
                                modifier = Modifier.verticalScroll(rememberScrollState())
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                HudButton(
                    onClick = onReset,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Run Another Command")
                }
            }
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                // errors are announced when they appear
                .semantics { liveRegion = LiveRegionMode.Assertive },
            shape = RoundedCornerShape(JarvisRadius.Card),
            tint = JarvisOffline
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Error,
                        null,
                        tint = JarvisOffline,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Error",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = JarvisOffline,
                        modifier = Modifier.semantics { heading() }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = JarvisTextPrimary
                )

                Spacer(modifier = Modifier.height(16.dp))

                HudButton(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Try Again")
                }
            }
        }
    }
}

@Composable
private fun HistoryView(
    modifier: Modifier = Modifier,
    history: List<TermuxCommandViewModel.CommandHistoryItem>,
    onSelectCommand: (TermuxCommandViewModel.CommandHistoryItem) -> Unit,
    onClearHistory: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Command History",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = JarvisTextPrimary,
                modifier = Modifier.semantics { heading() }
            )

            if (history.isNotEmpty()) {
                TextButton(
                    onClick = onClearHistory,
                    modifier = Modifier.semantics { contentDescription = "Clear command history" }
                ) {
                    Text("Clear", color = JarvisCyan)
                }
            }
        }

        if (history.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No command history yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = JarvisTextSecondary
                )
            }
        } else {
            // Dense list: flat solid rows, no glass.
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp)
            ) {
                items(history) { item ->
                    CommandHistoryItemCard(
                        item = item,
                        onClick = { onSelectCommand(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CommandHistoryItemCard(
    item: TermuxCommandViewModel.CommandHistoryItem,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .pressScale(interaction),
        interactionSource = interaction,
        shape = RoundedCornerShape(JarvisRadius.Card),
        colors = CardDefaults.cardColors(containerColor = JarvisCyan.copy(alpha = 0.06f)),
        border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Outcome is spoken, not just coloured.
                Icon(
                    if (item.success) Icons.Default.CheckCircle else Icons.Default.Error,
                    if (item.success) "Succeeded" else "Failed",
                    tint = if (item.success) JarvisOnline else JarvisOffline,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    item.query,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = JarvisTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                item.command,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = JarvisTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (item.executionMethod != null) {
                Text(
                    "via ${item.executionMethod}",
                    style = MaterialTheme.typography.bodySmall,
                    color = JarvisTextSecondary
                )
            }
        }
    }
}
