package com.jarvismini.convo

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ConvoUiState(
    val agents: List<ConvoAgent> = emptyList(),
    val bubbles: List<ConvoBubble> = emptyList(),
    val filter: ConvoFilter = ConvoFilter.All,
    /** Empty set = send to all. */
    val recipients: Set<String> = emptySet(),
    val draft: String = "",
    val error: String? = null,
    val sending: Boolean = false,
    val hasOlder: Boolean = false,
    val loadingOlder: Boolean = false,
    val selectedKey: String? = null,
    val baseUrl: String = DEFAULT_CONVO_BASE_URL,
    val hasToken: Boolean = false,
    val now: Long = 0L,
) {
    val visible: List<ConvoBubble> get() = bubbles.filtered(filter)
    val selected: ConvoBubble? get() = bubbles.firstOrNull { it.key == selectedKey }
}

class ConvoViewModel(
    private val repo: ConvoRepository,
    private val settings: ConvoSettingsStore,
    private val pollIntervalMs: Long = 10_000L,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {

    private val _state = MutableStateFlow(
        ConvoUiState(baseUrl = settings.baseUrl, hasToken = settings.token.isNotBlank()),
    )
    val state: StateFlow<ConvoUiState> = _state.asStateFlow()

    private val store = HashMap<Long, ConvoMessage>()
    private var cursor = 0L
    private var oldestId = Long.MAX_VALUE
    private var hasOlder = false
    private var pollJob: Job? = null
    private val mutex = Mutex()

    // ── polling lifecycle (driven by the screen) ─────────────────────────────

    fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                refresh()
                delay(pollIntervalMs)
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    // ── data ─────────────────────────────────────────────────────────────────

    suspend fun refresh() {
        mutex.withLock {
            val agentsRes = repo.agents()
            agentsRes.getOrNull()?.let { list -> _state.update { it.copy(agents = list) } }
            var err: Throwable? = agentsRes.exceptionOrNull()
            val msgErr = syncMessages()
            if (err == null) err = msgErr
            ackIncoming()
            publish(err)
        }
    }

    private suspend fun syncMessages(): Throwable? {
        if (cursor == 0L && store.isEmpty()) {
            val r = repo.messages(beforeId = LATEST, limit = PAGE)
            r.getOrNull()?.let { page ->
                mergePage(page)
                hasOlder = page.messages.size >= PAGE
            }
            return r.exceptionOrNull()
        }
        var pages = 0
        while (pages++ < MAX_CATCHUP_PAGES) {
            val page = repo.messages(afterId = cursor, limit = CATCHUP_LIMIT).getOrElse { return it }
            mergePage(page)
            if (page.messages.size < CATCHUP_LIMIT) break
        }
        // Read receipts change on rows we already have: re-read the latest window while anything is unread.
        if (store.values.any { it.readAt == null }) {
            val r = repo.messages(beforeId = LATEST, limit = PAGE)
            r.getOrNull()?.let { mergePage(it) }
            return r.exceptionOrNull()
        }
        return null
    }

    /** Messages addressed to the admin count as read once the Convo screen is open (polling runs only while visible). */
    private suspend fun ackIncoming() {
        val unread = store.values
            .filter { it.recipientId == ADMIN_ID && it.readAt == null }
            .sortedBy { it.id }
            .take(MAX_ACKS_PER_REFRESH)
        for (m in unread) {
            if (repo.ack(m.id).isFailure) break
            store[m.id] = m.copy(readAt = clock())
        }
    }

    private fun mergePage(page: MessagePage) {
        for (m in page.messages) {
            store[m.id] = m
            if (m.id > cursor) cursor = m.id
            if (m.id < oldestId) oldestId = m.id
        }
        page.nextCursor?.let { if (it > cursor) cursor = it }
    }

    private fun publish(err: Throwable?) {
        _state.update {
            it.copy(
                bubbles = groupMessages(store.values),
                error = err?.let { e -> e.message ?: "Convo error" },
                hasOlder = hasOlder,
                loadingOlder = false,
                now = clock(),
            )
        }
    }

    suspend fun loadOlder() {
        mutex.withLock {
            if (oldestId == Long.MAX_VALUE) return@withLock
            _state.update { it.copy(loadingOlder = true) }
            val r = repo.messages(beforeId = oldestId, limit = PAGE)
            r.getOrNull()?.let { page ->
                mergePage(page)
                hasOlder = page.messages.size >= PAGE
            }
            publish(r.exceptionOrNull())
        }
    }

    fun onLoadOlder() {
        viewModelScope.launch { loadOlder() }
    }

    // ── compose / send ───────────────────────────────────────────────────────

    fun onDraft(text: String) = _state.update { it.copy(draft = text) }

    fun allRecipients() = _state.update { it.copy(recipients = emptySet()) }

    fun toggleRecipient(id: String) = _state.update {
        val next = if (id in it.recipients) it.recipients - id else it.recipients + id
        it.copy(recipients = next)
    }

    suspend fun send() {
        val s = _state.value
        val text = s.draft.trim()
        if (text.isEmpty() || s.sending) return
        _state.update { it.copy(sending = true) }
        val targets = s.recipients.takeIf { it.isNotEmpty() }?.sorted()
        val r = repo.broadcast(text, targets)
        if (r.isSuccess) {
            _state.update { it.copy(draft = "", sending = false, error = null) }
            refresh()
        } else {
            val msg = r.exceptionOrNull()?.message ?: "unknown error"
            _state.update { it.copy(sending = false, error = "Send failed: $msg") }
        }
    }

    fun onSend() {
        viewModelScope.launch { send() }
    }

    // ── view state ───────────────────────────────────────────────────────────

    fun setFilter(f: ConvoFilter) = _state.update { it.copy(filter = f) }

    fun select(key: String?) = _state.update { it.copy(selectedKey = key) }

    /** A blank token keeps the saved one. Changing the server resets the loaded timeline. */
    suspend fun applySettings(baseUrl: String, token: String) {
        val url = normalizeBaseUrl(baseUrl)
        val changed = url != settings.baseUrl
        settings.baseUrl = url
        if (token.isNotBlank()) settings.token = token.trim()
        mutex.withLock {
            if (changed) {
                store.clear()
                cursor = 0L
                oldestId = Long.MAX_VALUE
                hasOlder = false
            }
            _state.update {
                it.copy(
                    baseUrl = settings.baseUrl,
                    hasToken = settings.token.isNotBlank(),
                    bubbles = groupMessages(store.values),
                )
            }
        }
        refresh()
    }

    fun onSaveSettings(baseUrl: String, token: String) {
        viewModelScope.launch { applySettings(baseUrl, token) }
    }

    override fun onCleared() {
        stopPolling()
        super.onCleared()
    }

    private companion object {
        const val PAGE = 100
        const val CATCHUP_LIMIT = 200
        const val MAX_CATCHUP_PAGES = 20
        const val MAX_ACKS_PER_REFRESH = 50
        const val LATEST = 2_000_000_000L
    }
}

class ConvoViewModelFactory(private val appContext: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val store = EncryptedConvoSettings(appContext)
        val repo = HttpConvoRepository(baseUrl = { store.baseUrl }, token = { store.token })
        return ConvoViewModel(repo, store) as T
    }
}
