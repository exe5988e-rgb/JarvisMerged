package com.jarvismini.ui.llm

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jarvismini.ui.theme.JarvisBlue
import com.jarvismini.ui.theme.JarvisCyan

/**
 * Main screen for Termux command generation
 * 
 * ✅ FIXED: ViewModel moved to separate file
 * Now uses FIFO for all command execution (no more Termux intent)
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
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Termux Command Generator") },
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
                            "Command History"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = JarvisBlue.copy(alpha = 0.1f)
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = !showHistory,
                enter = fadeIn() + slideInHorizontally(),
                exit = fadeOut() + slideOutHorizontally()
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
                enter = fadeIn() + slideInHorizontally { it },
                exit = fadeOut() + slideOutHorizontally { it }
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

@Composable
private fun ServerStatusChip(
    status: TermuxCommandViewModel.ServerStatus,
    onRefresh: () -> Unit
) {
    val (text, color) = when (status) {
        is TermuxCommandViewModel.ServerStatus.Online -> "Online" to Color.Green
        is TermuxCommandViewModel.ServerStatus.Offline -> "Offline" to Color.Red
        is TermuxCommandViewModel.ServerStatus.Checking -> "Checking..." to JarvisCyan
        is TermuxCommandViewModel.ServerStatus.Unknown -> "Unknown" to Color.Gray
    }
    
    AssistChip(
        onClick = onRefresh,
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
            containerColor = color.copy(alpha = 0.2f)
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
    Column {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("What do you want to do in Termux?") },
            placeholder = { Text("e.g., update all packages, list files, check disk space") },
            enabled = enabled,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = JarvisCyan,
                unfocusedBorderColor = JarvisCyan.copy(alpha = 0.5f)
            )
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Button(
            onClick = onGenerate,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled && query.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                containerColor = JarvisBlue
            )
        ) {
            Icon(Icons.Default.AutoAwesome, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Generate Command")
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
            color = Color.White.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
}

// ✅ FIX: Added missing CheckingServerState Composable
@Composable
private fun CheckingServerState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = JarvisCyan,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Checking server status...",
                style = MaterialTheme.typography.bodyLarge,
                color = JarvisCyan
            )
        }
    }
}

// ✅ FIX: Added missing GeneratingState Composable
@Composable
private fun GeneratingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = JarvisCyan,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Generating command...",
                style = MaterialTheme.typography.bodyLarge,
                color = JarvisCyan
            )
        }
    }
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
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isEdited) Color(0xFF2D5016) else JarvisCyan.copy(alpha = 0.1f)
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (isEdited) Color.Green else JarvisCyan
            )
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isEdited) Icons.Default.Edit else Icons.Default.Warning,
                    null,
                    tint = if (isEdited) Color.Green else Color.Yellow,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (isEdited) "Edited Command" else "Confirm Command Execution",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isEdited) Color.Green else JarvisCyan
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                if (isEdited) 
                    "You've edited the command. Review and execute when ready." 
                else 
                    "Review the generated command and edit if needed before execution.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f)
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
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.3f)),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = if (isEdited) Color.Green else Color.White
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = if (isEdited) Color.Green else JarvisCyan,
                    unfocusedBorderColor = if (isEdited) Color.Green.copy(alpha = 0.5f) else JarvisCyan.copy(alpha = 0.5f)
                )
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.Red
                    )
                ) {
                    Icon(Icons.Default.Close, null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Cancel")
                }
                
                Button(
                    onClick = { onExecute(editableCommand, isEdited) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Green
                    )
                ) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Execute")
                }
            }
        }
    }
}

@Composable
private fun ExecutingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = Color.Green,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Executing command...",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Green
            )
        }
    }
}

@Composable
private fun SuccessState(
    output: String,
    exitCode: Int,
    executionMethod: String?,
    onReset: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.Green.copy(alpha = 0.1f)
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(Color.Green)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CheckCircle,
                    null,
                    tint = Color.Green,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Command Executed Successfully",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Green
                )
            }
            
            if (executionMethod != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Execution method: $executionMethod",
                    style = MaterialTheme.typography.bodySmall,
                    color = JarvisCyan.copy(alpha = 0.8f)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                "Exit Code: $exitCode",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color.Green
            )
            
            if (output.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    "Output:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.3f))
                        .padding(12.dp)
                ) {
                    Text(
                        output,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onReset,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = JarvisBlue
                )
            ) {
                Text("Run Another Command")
            }
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.Red.copy(alpha = 0.1f)
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(Color.Red)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Error,
                    null,
                    tint = Color.Red,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Error",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Red
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = JarvisBlue
                )
            ) {
                Icon(Icons.Default.Refresh, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Try Again")
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
                fontWeight = FontWeight.Bold
            )
            
            if (history.isNotEmpty()) {
                TextButton(onClick = onClearHistory) {
                    Text("Clear")
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
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                history.forEach { item ->
                    CommandHistoryItemCard(
                        item = item,
                        onClick = { onSelectCommand(item) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
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
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (item.success) Icons.Default.CheckCircle else Icons.Default.Error,
                    null,
                    tint = if (item.success) Color.Green else Color.Red,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    item.query,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                item.command,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1
            )
            
            if (item.executionMethod != null) {
                Text(
                    "via ${item.executionMethod}",
                    style = MaterialTheme.typography.bodySmall,
                    color = JarvisCyan.copy(alpha = 0.6f)
                )
            }
        }
    }
}
