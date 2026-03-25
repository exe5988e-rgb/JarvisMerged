package com.jarvismini.agent

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarvismini.core.JarvisPrefs
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

    private var logStreamJob:  Job? = null
    private var statusPollJob: Job? = null
    private var autoSpeakJob:  Job? = null   // watches for task completion and speaks

    init {
        checkServer()
        observeDoneForAutoSpeak()
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
        val s = _state.value
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
        }
    }

    fun stopAgent() {
        viewModelScope.launch {
            AgentRepository.stopAgent()
            pushLog("[dashboard] Stop signal sent", LogLevel.SYSTEM)
        }
    }

    fun clearLogs() = _state.update { it.copy(logs = emptyList()) }

    // Manual "Speak" button — speaks last SUCCESS/STEP log line
    fun speakResult(context: Context? = null) {
        val last = _state.value.logs
            .filter { it.level == LogLevel.SUCCESS || it.level == LogLevel.STEP }
            .lastOrNull()?.text ?: return

        val voiceId = resolveVoiceId()
        viewModelScope.launch {
            AgentRepository.speak(last, voiceId)
        }
    }

    // ── Auto-speak watcher ────────────────────────────────────────────────────
    // Fires automatically when the agent finishes (done flips true) and TTS is on.
    // Speaks the last SUCCESS line so Sir hears the result hands-free.
    private fun observeDoneForAutoSpeak() {
        autoSpeakJob?.cancel()
        autoSpeakJob = viewModelScope.launch {
            var prevDone = false
            state.collect { s ->
                if (s.done && !prevDone && s.ttsEnabled) {
                    // Task just completed — find best line to speak
                    val toSpeak = s.logs
                        .filter { it.level == LogLevel.SUCCESS }
                        .lastOrNull()?.text
                        ?: s.logs.lastOrNull()?.text
                    if (!toSpeak.isNullOrBlank()) {
                        val voiceId = resolveVoiceId()
                        AgentRepository.speak(toSpeak, voiceId)
                    }
                }
                prevDone = s.done
            }
        }
    }

    // Reads the voice ID that was saved in Settings (elevenlabs_voice_id pref).
    // Falls back to the Jarvis custom voice if not set.
    private fun resolveVoiceId(): String {
        val saved = JarvisPrefs.getString("elevenlabs_voice_id")
        return if (!saved.isNullOrBlank()) saved else "lNiTyQyEeDoFcsYb4RUT"  // Jarvis voice
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
        autoSpeakJob?.cancel()
        super.onCleared()
    }
}
