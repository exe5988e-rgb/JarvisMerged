package com.jarvismini.ui.llm

import android.content.Context
import android.content.Intent
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarvismini.llm.TermuxLlamaClient
import com.jarvismini.ui.theme.JarvisBlue
import com.jarvismini.ui.theme.JarvisCyan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for managing Termux LLM interactions
 * UPDATED: Supports editable commands and Termux intent execution
 */
class TermuxCommandViewModel(private val context: Context) : ViewModel() {
    private val llamaClient = TermuxLlamaClient(context = context)
    
    private val _uiState = MutableStateFlow<LlamaUiState>(LlamaUiState.Idle)
    val uiState: StateFlow<LlamaUiState> = _uiState.asStateFlow()
    
    private val _commandHistory = MutableStateFlow<List<CommandHistoryItem>>(emptyList())
    val commandHistory: StateFlow<List<CommandHistoryItem>> = _commandHistory.asStateFlow()
    
    private val _serverStatus = MutableStateFlow<ServerStatus>(ServerStatus.Unknown)
    val serverStatus: StateFlow<ServerStatus> = _serverStatus.asStateFlow()
    
    sealed class LlamaUiState {
        object Idle : LlamaUiState()
        object CheckingServer : LlamaUiState()
        object Generating : LlamaUiState()
        data class WaitingConfirmation(
            val command: String,
            val isEdited: Boolean = false
        ) : LlamaUiState()
        object Executing : LlamaUiState()
        data class Success(
            val output: String,
            val exitCode: Int,
            val executionMethod: String? = null
        ) : LlamaUiState()
        data class Error(val message: String) : LlamaUiState()
    }
    
    sealed class ServerStatus {
        object Unknown : ServerStatus()
        object Checking : ServerStatus()
        object Online : ServerStatus()
        object Offline : ServerStatus()
    }
    
    data class CommandHistoryItem(
        val query: String,
        val command: String,
        val output: String?,
        val success: Boolean,
        val executionMethod: String? = null,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    init {
        checkServerStatus()
    }
    
    fun checkServerStatus() {
        viewModelScope.launch {
            _serverStatus.value = ServerStatus.Checking
            val isHealthy = llamaClient.checkHealth()
            _serverStatus.value = if (isHealthy) ServerStatus.Online else ServerStatus.Offline
        }
    }
    
    fun generateCommand(query: String) {
        viewModelScope.launch {
            try {
                _uiState.value = LlamaUiState.Generating
                
                val result = llamaClient.generateCommand(query)
                
                if (result.success && result.command != null) {
                    _uiState.value = LlamaUiState.WaitingConfirmation(
                        command = result.command,
                        isEdited = false
                    )
                } else {
                    _uiState.value = LlamaUiState.Error(
                        result.error ?: "Failed to generate command"
                    )
                    
                    addToHistory(
                        CommandHistoryItem(
                            query = query,
                            command = "",
                            output = result.error,
                            success = false
                        )
                    )
                }
            } catch (e: Exception) {
                _uiState.value = LlamaUiState.Error(e.message ?: "Unknown error")
            }
        }
    }
    
    fun updateCommand(newCommand: String) {
        val currentState = _uiState.value
        if (currentState is LlamaUiState.WaitingConfirmation) {
            _uiState.value = LlamaUiState.WaitingConfirmation(
                command = newCommand,
                isEdited = true
            )
        }
    }
    
    fun executeCommand(command: String, query: String, isEdited: Boolean) {
        viewModelScope.launch {
            try {
                _uiState.value = LlamaUiState.Executing
                
                val result = if (isEdited) {
                    // Use Termux intent for manually edited commands
                    executeViaTermuxIntent(command)
                } else {
                    // Use HTTP proxy for generated commands
                    llamaClient.executeCommand(command)
                }
                
                if (result.success) {
                    _uiState.value = LlamaUiState.Success(
                        output = result.output ?: "Command sent to Termux",
                        exitCode = result.exitCode ?: 0,
                        executionMethod = result.method
                    )
                    
                    addToHistory(
                        CommandHistoryItem(
                            query = query,
                            command = command,
                            output = result.output,
                            success = true,
                            executionMethod = result.method
                        )
                    )
                } else {
                    _uiState.value = LlamaUiState.Error(
                        result.error ?: "Execution failed"
                    )
                    
                    addToHistory(
                        CommandHistoryItem(
                            query = query,
                            command = command,
                            output = result.error,
                            success = false,
                            executionMethod = result.method
                        )
                    )
                }
            } catch (e: Exception) {
                _uiState.value = LlamaUiState.Error(e.message ?: "Execution failed")
            }
        }
    }
    
    private suspend fun executeViaTermuxIntent(command: String): TermuxLlamaClient.CommandResult {
        return try {
            // Execute command directly in Termux via intent
            val intent = Intent().apply {
                setClassName("com.termux", "com.termux.app.RunCommandService")
                action = "com.termux.RUN_COMMAND"
                putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command))
                putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
            }
            
            context.startService(intent)
            
            TermuxLlamaClient.CommandResult(
                success = true,
                output = "✅ Command sent to Termux. Check Termux app for output.",
                exitCode = 0,
                method = "termux_intent"
            )
        } catch (e: Exception) {
            TermuxLlamaClient.CommandResult(
                success = false,
                error = "Failed to launch Termux: ${e.message}\n\nMake sure Termux app is installed.",
                exitCode = -1,
                method = "termux_intent_error"
            )
        }
    }
    
    fun cancelCommand() {
        _uiState.value = LlamaUiState.Idle
    }
    
    fun reset() {
        _uiState.value = LlamaUiState.Idle
    }
    
    private fun addToHistory(item: CommandHistoryItem) {
        _commandHistory.value = listOf(item) + _commandHistory.value
    }
    
    fun clearHistory() {
        _commandHistory.value = emptyList()
    }
}

/**
 * Main screen for Termux command generation
 * UPDATED: Editable command field with Termux intent execution
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
                is TermuxCommandViewModel.LlamaUiState.Generating -> GeneratingState()
                is TermuxCommandViewModel.LlamaUiState.WaitingConfirmation -> {
                    EditableConfirmationState(
                        command = uiState.command,
                        isEdited = uiState.isEdited,
                        onCommandChange = onUpdateCommand,
                        onExecute = { command, isEdited -> 
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
                else -> {}
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = JarvisBlue.copy(alpha = 0.1f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "What would you like to do?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = JarvisBlue
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g., list files") },
                enabled = enabled,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = JarvisBlue,
                    unfocusedBorderColor = JarvisBlue.copy(alpha = 0.5f)
                )
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Button(
                onClick = onGenerate,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled && query.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = JarvisBlue
                )
            ) {
                Icon(Icons.Default.Send, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Generate Command")
            }
        }
    }
}

@Composable
private fun IdleState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Terminal,
            null,
            modifier = Modifier.size(64.dp),
            tint = JarvisBlue.copy(alpha = 0.5f)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            "Enter a command in natural language",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.6f)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            "Try asking:",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = JarvisBlue
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        SuggestionCard("List all files in my Downloads folder")
        SuggestionCard("Show my current directory size")
        SuggestionCard("Find files modified in the last 7 days")
    }
}

@Composable
private fun SuggestionCard(text: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = JarvisCyan.copy(alpha = 0.1f)
        )
    ) {
        Text(
            text,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun GeneratingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = JarvisBlue)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Generating command...",
            style = MaterialTheme.typography.bodyLarge,
            color = JarvisBlue
        )
    }
}

/**
 * UPDATED: Editable confirmation state
 * Shows TextField instead of Text for command editing
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
                    if (isEdited) "Edited Command (Termux Intent)" else "Confirm Command Execution",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isEdited) Color.Green else JarvisCyan
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // EDITABLE COMMAND FIELD
            OutlinedTextField(
                value = editableCommand,
                onValueChange = { newValue ->
                    editableCommand = newValue
                    onCommandChange(newValue)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = Color.Green
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Black.copy(alpha = 0.5f),
                    unfocusedContainerColor = Color.Black.copy(alpha = 0.5f),
                    focusedBorderColor = Color.Green,
                    unfocusedBorderColor = Color.Green.copy(alpha = 0.5f),
                    cursorColor = Color.Green,
                    focusedTextColor = Color.Green,
                    unfocusedTextColor = Color.Green
                ),
                placeholder = {
                    Text(
                        "Enter command...",
                        fontFamily = FontFamily.Monospace,
                        color = Color.Green.copy(alpha = 0.5f)
                    )
                }
            )
            
            if (isEdited) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "ℹ️ Edited commands will execute via Termux intent",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Cyan.copy(alpha = 0.8f)
                )
            }
            
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
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = Color.Green)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Executing command...",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.Green
        )
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
                    "Command Executed",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Green
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                "Exit code: $exitCode",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f)
            )
            
            if (executionMethod != null) {
                Text(
                    "Method: $executionMethod",
                    style = MaterialTheme.typography.bodySmall,
                    color = JarvisCyan.copy(alpha = 0.8f)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    output.ifBlank { "(No output)" },
                    fontFamily = FontFamily.Monospace,
                    color = Color.Green,
                    style = MaterialTheme.typography.bodySmall
                )
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
