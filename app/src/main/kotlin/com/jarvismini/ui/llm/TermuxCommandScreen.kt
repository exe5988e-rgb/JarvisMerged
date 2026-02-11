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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarvismini.llm.TermuxLlamaClient
// Fixed: Import individual colors instead of JarvisColors object
import com.jarvismini.ui.theme.JarvisBlue
import com.jarvismini.ui.theme.JarvisCyan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for managing Termux LLM interactions
 */
class TermuxCommandViewModel : ViewModel() {
    private val llamaClient = TermuxLlamaClient()
    
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
        data class WaitingConfirmation(val command: String) : LlamaUiState()
        object Executing : LlamaUiState()
        data class Success(val output: String, val exitCode: Int) : LlamaUiState()
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
                    _uiState.value = LlamaUiState.WaitingConfirmation(result.command)
                } else {
                    _uiState.value = LlamaUiState.Error(
                        result.error ?: "Failed to generate command"
                    )
                    
                    // Add to history as failed
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
    
    fun executeCommand(command: String, query: String) {
        viewModelScope.launch {
            try {
                _uiState.value = LlamaUiState.Executing
                
                val result = llamaClient.executeCommand(command)
                
                _uiState.value = LlamaUiState.Success(
                    output = result.output ?: "",
                    exitCode = result.exitCode ?: -1
                )
                
                // Add to history
                addToHistory(
                    CommandHistoryItem(
                        query = query,
                        command = command,
                        output = result.output,
                        success = result.success
                    )
                )
            } catch (e: Exception) {
                _uiState.value = LlamaUiState.Error(e.message ?: "Execution failed")
            }
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
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermuxCommandScreen(
    viewModel: TermuxCommandViewModel,
    onNavigateBack: () -> Unit = {}
) {
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
                    // Server status indicator
                    ServerStatusChip(
                        status = serverStatus,
                        onRefresh = { viewModel.checkServerStatus() }
                    )
                    
                    // History button
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
            // Main content
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
                    onExecuteCommand = { command ->
                        viewModel.executeCommand(command, currentQuery)
                        currentQuery = ""
                    },
                    onCancelCommand = { viewModel.cancelCommand() },
                    onReset = { viewModel.reset() }
                )
            }
            
            // History view
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
    onExecuteCommand: (String) -> Unit,
    onCancelCommand: () -> Unit,
    onReset: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Query input
        QueryInputSection(
            query = currentQuery,
            onQueryChange = onQueryChange,
            onGenerate = onGenerateCommand,
            enabled = uiState is TermuxCommandViewModel.LlamaUiState.Idle
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // State-based content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (uiState) {
                is TermuxCommandViewModel.LlamaUiState.Idle -> IdleState()
                is TermuxCommandViewModel.LlamaUiState.Generating -> GeneratingState()
                is TermuxCommandViewModel.LlamaUiState.WaitingConfirmation -> {
                    ConfirmationState(
                        command = uiState.command,
                        onExecute = { onExecuteCommand(uiState.command) },
                        onCancel = onCancelCommand
                    )
                }
                is TermuxCommandViewModel.LlamaUiState.Executing -> ExecutingState()
                is TermuxCommandViewModel.LlamaUiState.Success -> {
                    SuccessState(
                        output = uiState.output,
                        exitCode = uiState.exitCode,
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
                placeholder = { Text("e.g., Find all PDF files in Downloads") },
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
        
        // Example suggestions
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

@Composable
private fun ConfirmationState(
    command: String,
    onExecute: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = JarvisCyan.copy(alpha = 0.1f)
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(JarvisCyan)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    null,
                    tint = Color.Yellow,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Confirm Command Execution",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = JarvisCyan
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(12.dp)
            ) {
                Text(
                    command,
                    fontFamily = FontFamily.Monospace,
                    color = Color.Green,
                    style = MaterialTheme.typography.bodyMedium
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
                    onClick = onExecute,
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
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                "Exit code: $exitCode",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f)
            )
            
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
        // Header
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
        
        // History list
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
        }
    }
}
