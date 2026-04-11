package com.jarvismini.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HealDashboardState(
    val serverOnline: Boolean       = false,
    val healing:      Boolean       = false,
    val done:         Boolean       = false,
    val error:        String?       = null,
    val logs:         List<LogLine> = emptyList(),
    val taskInput:    String        = "",
    val ttsEnabled:   Boolean       = true,
)

class HealDashboardViewModel : ViewModel() {

    private val _state = MutableStateFlow(HealDashboardState())
    val state: StateFlow<HealDashboardState> = _state.asStateFlow()

    private var logStreamJob: Job? = null

    init { checkServer() }

    fun checkServer() {
        viewModelScope.launch {
            val online = AgentRepository.isAgentServerReachable()
            _state.update { it.copy(serverOnline = online) }
            if (online) restoreLogs()
        }
    }

    fun onTaskInput(v: String)  = _state.update { it.copy(taskInput = v) }
    fun onTtsToggle(v: Boolean) = _state.update { it.copy(ttsEnabled = v) }
    fun clearLogs()             = _state.update { it.copy(logs = emptyList()) }

    fun startHeal() {
        val task = _state.value.taskInput.trim()
        if (task.isEmpty()) return

        viewModelScope.launch {
            _state.update { it.copy(logs = emptyList(), done = false, error = null, healing = true) }
            pushLog("[heal] Sending task to self-repair pipeline...", LogLevel.SYSTEM)

            AgentRepository.runHealTask(task)
                .onSuccess {
                    pushLog("[heal] Pipeline started — DevTools will open Claude shortly", LogLevel.SYSTEM)
                    startLogStream()
                    watchForCompletion()
                }
                .onFailure {
                    pushLog("[heal] ✗ Failed to start: ${it.message}", LogLevel.ERROR)
                    _state.update { s -> s.copy(healing = false, error = it.message) }
                }
        }
    }

    private fun watchForCompletion() {
        viewModelScope.launch {
            AgentRepository.streamLogs().collect { line ->
                pushLog(line, classifyHealLog(line))
                when {
                    line.contains("ai-output.txt received") || line.contains("Pipeline complete") -> {
                        _state.update { it.copy(healing = false, done = true) }
                        if (_state.value.ttsEnabled) {
                            AgentRepository.speak("Self-heal complete. Patches applied.")
                        }
                        logStreamJob?.cancel()
                        return@collect
                    }
                    line.contains("timeout waiting") || line.contains("Pipeline failed") -> {
                        _state.update { it.copy(healing = false, error = line) }
                        logStreamJob?.cancel()
                        return@collect
                    }
                }
            }
        }
    }

    private fun startLogStream() {
        logStreamJob?.cancel()
        logStreamJob = viewModelScope.launch {
            AgentRepository.streamLogs().collect { line ->
                pushLog(line, classifyHealLog(line))
            }
        }
    }

    private fun restoreLogs() {
        viewModelScope.launch {
            if (_state.value.logs.isNotEmpty()) return@launch
            val lines = AgentRepository.fetchLogTail(50)
            if (lines.isEmpty()) return@launch
            pushLog("[heal] ── restored last ${lines.size} lines ──", LogLevel.SYSTEM)
            lines.forEach { pushLog(it, classifyHealLog(it)) }
        }
    }

    private fun classifyHealLog(line: String): LogLevel = when {
        line.contains("✅") || line.contains("received") || line.contains("complete") -> LogLevel.SUCCESS
        line.contains("✗") || line.contains("failed", ignoreCase = true) || line.contains("error:", ignoreCase = true) -> LogLevel.ERROR
        line.contains("waiting") || line.contains("retrying") -> LogLevel.WARN
        line.contains("[heal]") || line.contains("[self_repair]") -> LogLevel.SYSTEM
        line.contains("AGENT_") -> LogLevel.STEP
        else -> LogLevel.INFO
    }

    private fun pushLog(text: String, level: LogLevel = LogLevel.INFO) {
        _state.update { s ->
            val logs = (s.logs + LogLine(id = System.nanoTime(), text = text, level = level)).takeLast(200)
            s.copy(logs = logs)
        }
    }

    override fun onCleared() {
        logStreamJob?.cancel()
        super.onCleared()
    }
}
