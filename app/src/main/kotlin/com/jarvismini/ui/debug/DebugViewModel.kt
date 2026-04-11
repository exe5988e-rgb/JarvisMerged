package com.jarvismini.ui.debug

import android.app.ActivityManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jarvismini.agent.AgentRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DebugState(
    val logs:      List<DebugLogEntry> = emptyList(),
    val cpuPct:    String              = "–",
    val ramMb:     String              = "–",
    val connected: Boolean             = false,
)

data class DebugLogEntry(
    val id:      Long,
    val level:   String,
    val message: String,
)

class DebugViewModel(private val context: Context) : ViewModel() {

    private val _state = MutableStateFlow(DebugState())
    val state: StateFlow<DebugState> = _state.asStateFlow()

    // ── cpu baseline ──────────────────────────────────────────────────────────
    private var prevIdle:  Long = 0L
    private var prevTotal: Long = 0L

    init {
        loadLogTail()
        startLogStream()
        startSystemPolling()
    }

    // ── log tail on open ──────────────────────────────────────────────────────
    private fun loadLogTail() {
        viewModelScope.launch {
            val lines = AgentRepository.fetchLogTail(50)
            if (lines.isNotEmpty()) {
                val entries = lines.map { line ->
                    DebugLogEntry(
                        id      = System.nanoTime(),
                        level   = classifyLevel(line),
                        message = line,
                    )
                }
                _state.update { it.copy(logs = entries, connected = true) }
            }
        }
    }

    // ── live SSE stream ───────────────────────────────────────────────────────
    private fun startLogStream() {
        viewModelScope.launch {
            AgentRepository.streamLogs().collect { line ->
                val entry = DebugLogEntry(
                    id      = System.nanoTime(),
                    level   = classifyLevel(line),
                    message = line,
                )
                _state.update { s ->
                    val logs = (s.logs + entry).takeLast(200)
                    s.copy(logs = logs, connected = true)
                }
            }
        }
    }

    // ── cpu + ram polling every 2s ────────────────────────────────────────────
    private fun startSystemPolling() {
        viewModelScope.launch {
            while (true) {
                _state.update { it.copy(cpuPct = readCpu(), ramMb = readRam()) }
                delay(2_000)
            }
        }
    }

    private fun readCpu(): String {
        return try {
            val line  = java.io.File("/proc/stat").readLines().firstOrNull() ?: return "–"
            val parts = line.trim().split("\\s+".toRegex())
            val user  = parts[1].toLong()
            val nice  = parts[2].toLong()
            val sys   = parts[3].toLong()
            val idle  = parts[4].toLong()
            val iowait= parts[5].toLong()
            val irq   = parts[6].toLong()
            val sirq  = parts[7].toLong()
            val total = user + nice + sys + idle + iowait + irq + sirq
            val dTotal = total - prevTotal
            val dIdle  = idle  - prevIdle
            prevTotal = total
            prevIdle  = idle
            if (dTotal == 0L) "0%" else "${((dTotal - dIdle) * 100 / dTotal)}%"
        } catch (e: Exception) {
            "–"
        }
    }

    private fun readRam(): String {
        return try {
            val am   = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            val usedMb = (info.totalMem - info.availMem) / (1024 * 1024)
            "${usedMb}MB"
        } catch (e: Exception) {
            "–"
        }
    }

    fun clearLogs() = _state.update { it.copy(logs = emptyList()) }

    private fun classifyLevel(line: String): String = when {
        line.contains("error",   ignoreCase = true) ||
        line.contains("✗")                          -> "ERROR"
        line.contains("warn",    ignoreCase = true) -> "WARNING"
        line.contains("debug",   ignoreCase = true) -> "DEBUG"
        else                                         -> "INFO"
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
            DebugViewModel(context) as T
    }
}
