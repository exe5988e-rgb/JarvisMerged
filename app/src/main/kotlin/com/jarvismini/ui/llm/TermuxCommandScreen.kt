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
import com.jarvismini.ui.theme.JarvisColors
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
    
    var queryText by remember { mutableStateOf("") }
    var showHistory by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Termux Command Assistant") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    // Server status indicator
                    ServerStatusChip(serverStatus, onClick = { viewModel.checkServerStatus() })
                    
                    // History toggle
                    IconButton(onClick = { showHistory = !showHistory }) {
                        Icon(
                            if (showHistory) Icons.Default.KeyboardArrowUp else Icons.Default.History,
                            "History"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = JarvisColors.background,
                    titleContentColor = JarvisColors.primary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(JarvisColors.background)
        ) {
            // Main content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (showHistory) {
                    CommandHistoryView(
                        history = commandHistory,
                        onClearHistory = { viewModel.clearHistory() },
                        onSelectCommand = { item ->
                            queryText = item.query
                            showHistory = false
                        }
                    )
                } else {
                    CommandGenerationView(
                        uiState = uiState,
                        queryText = queryText,
                        onQueryChange = { queryText = it },
                        onGenerate = { viewModel.generateCommand(queryText) },
                        onExecute = { command -> viewModel.executeCommand(command, queryText) },
                        onCancel = { viewModel.cancelCommand() },
                        onReset = { 
                            viewModel.reset()
                            queryText = ""
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerStatusChip(
    status: TermuxCommandViewModel.ServerStatus,
    onClick: () -> Unit
) {
    val (color, text, icon) = when (status) {
        is TermuxCommandViewModel.ServerStatus.Unknown -> 
            Triple(Color.Gray, "Unknown", Icons.Default.Help)
        is TermuxCommandViewModel.ServerStatus.Checking -> 
            Triple(Color.Yellow, "Checking", Icons.Default.Refresh)
        is TermuxCommandViewModel.ServerStatus.Online -> 
            Triple(Color.Green, "Online", Icons.Default.CheckCircle)
        is TermuxCommandViewModel.ServerStatus.Offline -> 
            Triple(Color.Red, "Offline", Icons.Default.Cancel)
    }
    
    AssistChip(
        onClick = onClick,
        label = { Text(text) },
        leadingIcon = { Icon(icon, null, tint = color) }
    )
}

@Composable
private fun CommandGenerationView(
    uiState: TermuxCommandViewModel.LlamaUiState,
    queryText: String,
    onQueryChange: (String) -> Unit,
    onGenerate: () -> Unit,
    onExecute: (String) -> Unit,
    onCancel: () -> Unit,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Query input
        OutlinedTextField(
            value = queryText,
            onValueChange = onQueryChange,
            label = { Text("Describe what you want to do") },
            placeholder = { Text("e.g., list all pdf files in downloads") },
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState is TermuxCommandViewModel.LlamaUiState.Idle,
            leadingIcon = {
                Icon(Icons.Default.Terminal, "Command")
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = JarvisColors.primary,
                focusedLabelColor = JarvisColors.primary
            )
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Generate button
        AnimatedVisibility(
            visible = uiState is TermuxCommandViewModel.LlamaUiState.Idle
        ) {
            Button(
                onClick = onGenerate,
                enabled = queryText.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = JarvisColors.primary
                )
            ) {
                Icon(Icons.Default.AutoAwesome, "Generate")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Generate Command")
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // State-based UI
        when (val state = uiState) {
            is TermuxCommandViewModel.LlamaUiState.Generating -> {
                LoadingCard("Generating command with AI...")
            }
            
            is TermuxCommandViewModel.LlamaUiState.WaitingConfirmation -> {
                CommandConfirmationCard(
                    command = state.command,
                    onExecute = { onExecute(state.command) },
                    onCancel = onCancel
                )
            }
            
            is TermuxCommandViewModel.LlamaUiState.Executing -> {
                LoadingCard("Executing command...")
            }
            
            is TermuxCommandViewModel.LlamaUiState.Success -> {
                OutputCard(
                    output = state.output,
                    exitCode = state.exitCode,
                    onReset = onReset
                )
            }
            
            is TermuxCommandViewModel.LlamaUiState.Error -> {
                ErrorCard(
                    message = state.message,
                    onRetry = onReset
                )
            }
            
            else -> {
                // Idle or unknown state - show suggestions
                SuggestionsCard(onSuggestionClick = onQueryChange)
            }
        }
    }
}

@Composable
private fun LoadingCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = JarvisColors.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = JarvisColors.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = JarvisColors.textPrimary
            )
        }
    }
}

@Composable
private fun CommandConfirmationCard(
    command: String,
    onExecute: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = JarvisColors.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    "Review",
                    tint = Color(0xFFFFB74D),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Review Command",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = JarvisColors.textPrimary
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Command display
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E1E1E))
                    .padding(12.dp)
            ) {
                Text(
                    text = command,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF4EC9B0),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Close, "Cancel")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Cancel")
                }
                
                Button(
                    onClick = onExecute,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JarvisColors.primary
                    )
                ) {
                    Icon(Icons.Default.PlayArrow, "Execute")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Execute")
                }
            }
        }
    }
}

@Composable
private fun OutputCard(
    output: String,
    exitCode: Int,
    onReset: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = JarvisColors.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (exitCode == 0) Icons.Default.CheckCircle else Icons.Default.Error,
                    "Result",
                    tint = if (exitCode == 0) Color.Green else Color.Red,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (exitCode == 0) "Success" else "Command Failed",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = JarvisColors.textPrimary
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "Exit: $exitCode",
                    style = MaterialTheme.typography.bodySmall,
                    color = JarvisColors.textSecondary
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Output display
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E1E1E))
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = output.ifEmpty { "(No output)" },
                    fontFamily = FontFamily.Monospace,
                    color = if (exitCode == 0) Color(0xFFCCCCCC) else Color(0xFFF48771),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onReset,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, "New Command")
                Spacer(modifier = Modifier.width(8.dp))
                Text("New Command")
            }
        }
    }
}

@Composable
private fun ErrorCard(
    message: String,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF3D1F1F)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Error,
                    "Error",
                    tint = Color(0xFFF44336),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Error",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFFFCDD2)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Try Again")
            }
        }
    }
}

@Composable
private fun SuggestionsCard(onSuggestionClick: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = JarvisColors.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "💡 Suggestions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = JarvisColors.textPrimary
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            val suggestions = listOf(
                "list all pdf files in downloads",
                "show memory usage",
                "find files larger than 100MB",
                "count lines in all kotlin files",
                "compress my pictures folder"
            )
            
            suggestions.forEach { suggestion ->
                AssistChip(
                    onClick = { onSuggestionClick(suggestion) },
                    label = { Text(suggestion) },
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun CommandHistoryView(
    history: List<TermuxCommandViewModel.CommandHistoryItem>,
    onClearHistory: () -> Unit,
    onSelectCommand: (TermuxCommandViewModel.CommandHistoryItem) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
                    color = JarvisColors.textSecondary
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
                color = JarvisColors.textSecondary,
                maxLines = 1
            )
        }
    }
}
