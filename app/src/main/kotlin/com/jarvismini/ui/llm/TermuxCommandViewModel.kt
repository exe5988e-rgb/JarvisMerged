package com.jarvismini.ui.llm

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarvismini.llm.TermuxLlamaClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for Termux Command Generator
 * 
 * FIXED: Removed Termux intent execution
 * Now uses FIFO for ALL command execution (edited or generated)
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
    
    /**
     * ✅ FIXED: Execute command using FIFO for ALL commands
     * No more conditional logic - always use llamaClient.executeCommand()
     */
    fun executeCommand(command: String, query: String, isEdited: Boolean) {
        viewModelScope.launch {
            try {
                _uiState.value = LlamaUiState.Executing
                
                // ✅ ALWAYS use FIFO execution (works for generated AND edited commands)
                val result = llamaClient.executeCommand(command)
                
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
