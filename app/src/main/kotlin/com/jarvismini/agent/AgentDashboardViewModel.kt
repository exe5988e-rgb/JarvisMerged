package com.jarvismini.agent

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AgentDashboardState(
    val serverOnline:  Boolean        = false,
    val running:       Boolean        = false,
    val task:          String         = "",
    val step:          Int            = 0,
    val done:          Boolean        = false,
    val error:         String?        = null,
    val logs:          List<LogLine>  = emptyList(),
    val taskInput:     String         = "",
    val deviceInput:   String         = "192.168.29.48:40657",
    val maxSteps:      Int            = 20,
    val ttsEnabled:    Boolean        = true,
)

data class LogLine(
    val text:  String,
    val level: LogLevel = LogLevel.INFO,
)

enum class LogLevel { INFO, STEP, SUCCESS, ERROR, WARN, SYSTEM }

fun classifyLog(line: String): LogLevel = when {
    line.contains("✓") || line.contains("Task complete") || line.contains("completed") -> LogLevel.SUCCESS
    line.contains("✗") || line.contains("ERROR") || line.contains("error:", ignoreCase = true) -> LogLevel.ERROR
    line.contains("WARNING") || line.contains("WARN") || line.contains("Retry") -> LogLevel.WARN
    line.contains("STEP") && line.contains("|") -> LogLevel.STEP
    line.startsWith("[server]") || line.startsWith("[agent_server]") -> LogLevel.SYSTEM
    else -> LogLevel.INFO
}

class AgentDashboardViewModel : ViewModel() {

    private val _state = MutableStateFlow(AgentDashboardState())
    val state: StateFlow<AgentDashboardState> = _state.asStateFlow()

    private var logStreamJob:      Job? = null
    private var statusPollJob:     Job? = null
    private var completionWatcher: Job? = null

    init {
        checkServer()
    }

    fun checkServer() {
        viewModelScope.launch {
            val online = AgentRepository.isAgentServerReachable()
            _state.update { it.copy(serverOnline = online) }
            if (online) pollStatus()
        }
    }

    fun onTaskInput(v: String)   = _state.update { it.copy(taskInput = v) }
    fun onDeviceInput(v: String) = _state.update { it.copy(deviceInput = v) }
    fun onMaxStepsInput(v: Int)  = _state.update { it.copy(maxSteps = v) }
    fun onTtsToggle(v: Boolean)  = _state.update { it.copy(ttsEnabled = v) }

    fun startAgent() {
        val s      = _state.value
        val task   = s.taskInput.trim()
        val device = s.deviceInput.trim()
        if (task.isEmpty()) return

        viewModelScope.launch {
            _state.update { it.copy(logs = emptyList(), done = false, error = null) }
            pushLog("[dashboard] Sending task to agent server...", LogLevel.SYSTEM)

            AgentRepository.runTask(task, device, s.maxSteps)
                .onSuccess { pushLog("[dashboard] Agent started: $it", LogLevel.SYSTEM) }
                .onFailure { pushLog("[dashboard] ✗ Failed: ${it.message}", LogLevel.ERROR) }

            startLogStream()
            watchForCompletion()   // ← auto-speak when done
        }
    }

    fun stopAgent() {
        viewModelScope.launch {
            AgentRepository.stopAgent()
            pushLog("[dashboard] Stop signal sent", LogLevel.SYSTEM)
        }
    }

    fun clearLogs() = _state.update { it.copy(logs = emptyList()) }

    fun speakResult(context: Context) {
        val last = _state.value.logs
            .filter { it.level == LogLevel.SUCCESS || it.level == LogLevel.STEP }
            .lastOrNull()?.text ?: return

        viewModelScope.launch {
            AgentRepository.speak(last)
        }
    }

    /**
     * Watches state for done=true, then auto-speaks the last SUCCESS/STEP log line.
     * Fires once per task run. Respects ttsEnabled toggle.
     */
    private fun watchForCompletion() {
        completionWatcher?.cancel()
        completionWatcher = viewModelScope.launch {
            state
                .filter { it.done && !it.running }
                .take(1)
                .collect {
                    if (_state.value.ttsEnabled) {
                        val last = _state.value.logs
                            .filter { l -> l.level == LogLevel.SUCCESS || l.level == LogLevel.STEP }
                            .lastOrNull()?.text
                        if (!last.isNullOrBlank()) {
                            pushLog("[dashboard] Speaking result via TTS...", LogLevel.SYSTEM)
                            AgentRepository.speak(last)
                        }
                    }
                }
        }
    }

    private fun startLogStream() {
        logStreamJob?.cancel()
        logStreamJob = viewModelScope.launch {
            AgentRepository.streamLogs().collect { line ->
                pushLog(line, classifyLog(line))
            }
        }
    }

    private fun pollStatus() {
        statusPollJob?.cancel()
        statusPollJob = viewModelScope.launch {
            while (true) {
                delay(2000)
                val status = AgentRepository.getStatus()
                _state.update { it.copy(
                    running      = status.running,
                    task         = status.task,
                    step         = status.step,
                    done         = status.done,
                    error        = status.error,
                    serverOnline = true,
                )}
            }
        }
    }

    private fun pushLog(text: String, level: LogLevel = LogLevel.INFO) {
        _state.update { s ->
            val logs = (s.logs + LogLine(text, level)).takeLast(200)
            s.copy(logs = logs)
        }
    }

    override fun onCleared() {
        logStreamJob?.cancel()
        statusPollJob?.cancel()
        completionWatcher?.cancel()
        super.onCleared()
    }
}
