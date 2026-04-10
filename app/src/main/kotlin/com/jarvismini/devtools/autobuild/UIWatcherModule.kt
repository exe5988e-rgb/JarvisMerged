package com.jarvismini.devtools.autobuild

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

class UIWatcherModule(
    private val service: AccessibilityService
) {
    companion object {
        private const val TAG = "UIWatcherModule"
        private const val CLAUDE_PKG = "com.anthropic.claude"
        private const val DOWNLOADS_DIR = "/sdcard/Download"
        private const val CANONICAL_DOWNLOAD_PATH = "$DOWNLOADS_DIR/ai-output.txt"
        private const val MAX_DOWNLOAD_WAIT_MS = 60_000L
        private const val POLL_INTERVAL_MS = 500L

        private const val CARD_MIN_HEIGHT_PX = 120
        private const val CARD_MAX_HEIGHT_PX = 400
        private const val CARD_MIN_WIDTH_FRACTION = 0.60f
        private const val CARD_MAX_HEIGHT_FRACTION = 0.60f

        private val CARD_CONFIDENCE_KEYWORDS = listOf("output", "txt", "code", "ai-output", "ai_output")

        private const val FILES_TILE_FALLBACK_X = 1002f
        private const val FILES_TILE_FALLBACK_Y = 650f

        private const val PICKER_SEARCH_BTN_X = 975f
        private const val PICKER_SEARCH_BTN_Y = 244f
        private const val PICKER_MENU_BTN_X = 97f
        private const val PICKER_MENU_BTN_Y = 244f

        private const val ADD_TO_CHAT_BTN_X = 74f
        private const val ADD_TO_CHAT_BTN_Y = 2650f

        private const val DEBUG_UI_TREE = true
        private const val DUMP_MAX_DEPTH = 8
    }

    // ─── Debug helpers (unchanged) ────────────────────────────────────────

    fun logAccessibilityEvent(event: AccessibilityEvent) {
        val typeStr = AccessibilityEvent.eventTypeToString(event.eventType)
        val pkg  = event.packageName ?: "null"
        val cls  = event.className ?: "null"
        val text = event.text?.joinToString("|") ?: "null"
        val desc = event.contentDescription ?: "null"
        Log.d(TAG, "Event type=$typeStr pkg=$pkg cls=$cls text=$text desc=$desc")
    }

    private fun logWindowInfo() {
        try {
            val windows = service.windows
            Log.d(TAG, "Windows scan: total=${windows.size}")
            for (w in windows) {
                val typeStr = when (w.type) {
                    AccessibilityWindowInfo.TYPE_APPLICATION           -> "APPLICATION"
                    AccessibilityWindowInfo.TYPE_INPUT_METHOD          -> "INPUT_METHOD"
                    AccessibilityWindowInfo.TYPE_SYSTEM                -> "SYSTEM"
                    AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> "ACCESSIBILITY_OVERLAY"
                    AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER  -> "SPLIT_SCREEN_DIVIDER"
                    else -> "UNKNOWN(${w.type})"
                }
                val bounds = Rect(); w.getBoundsInScreen(bounds)
                Log.d(TAG, "Window id=${w.id} type=$typeStr bounds=$bounds title=${w.title} root=${w.root != null}")
                w.root?.recycle()
            }
        } catch (e: Exception) { Log.e(TAG, "logWindowInfo failed", e) }
    }

    private fun logRootSnapshot(root: AccessibilityNodeInfo?, label: String = "") {
        if (root == null) { Log.w(TAG, "logRootSnapshot[$label]: root is null"); return }
        Log.d(TAG, "RootSnapshot[$label]: class=${root.className} childCount=${root.childCount} pkg=${root.packageName}")
    }

    private fun logNodeSearch(root: AccessibilityNodeInfo, query: String) {
        try {
            val results = root.findAccessibilityNodeInfosByText(query)
            Log.d(TAG, "search '$query' -> ${results.size} nodes")
            for (node in results) {
                val bounds = Rect(); node.getBoundsInScreen(bounds)
                Log.d(TAG, "  node bounds=$bounds clickable=${node.isClickable} text='${node.text}' desc='${node.contentDescription}'")
                node.recycle()
            }
        } catch (e: Exception) { Log.e(TAG, "logNodeSearch('$query') failed", e) }
    }

    private fun logClickAttempt(node: AccessibilityNodeInfo, label: String = "") {
        val bounds = Rect(); node.getBoundsInScreen(bounds)
        Log.d(TAG, "Attempting click[$label] bounds=$bounds class=${node.className} clickable=${node.isClickable}")
    }

    private fun dumpNodeTree(node: AccessibilityNodeInfo?, depth: Int = 0, maxDepth: Int = DUMP_MAX_DEPTH) {
        if (node == null || depth > maxDepth) return
        val indent = "  ".repeat(depth)
        val bounds = Rect(); node.getBoundsInScreen(bounds)
        Log.d(TAG, "${indent}depth=$depth class=${node.className} text=\"${node.text}\" desc=\"${node.contentDescription}\" clickable=${node.isClickable} bounds=$bounds")
        for (i in 0 until node.childCount) { val c = node.getChild(i) ?: continue; dumpNodeTree(c, depth+1, maxDepth); c.recycle() }
    }

    private fun dumpAllWindowsIfEnabled(label: String) {
        if (!DEBUG_UI_TREE) return
        Log.d(TAG, "=== AllWindowsDump[$label] START ===")
        try {
            for ((idx, window) in service.windows.withIndex()) {
                val bounds = Rect(); window.getBoundsInScreen(bounds)
                Log.d(TAG, "--- Window[$idx] id=${window.id} type=${window.type} bounds=$bounds ---")
                window.root?.let { dumpNodeTree(it, 0, DUMP_MAX_DEPTH); it.recycle() }
            }
        } catch (e: Exception) { Log.e(TAG, "dumpAllWindowsIfEnabled failed", e) }
        Log.d(TAG, "=== AllWindowsDump[$label] END ===")
    }

    private fun dumpRootIfEnabled(label: String) { if (DEBUG_UI_TREE) dumpAllWindowsIfEnabled(label) }

    private fun allRoots(): List<AccessibilityNodeInfo> {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        val seen  = mutableSetOf<Int>()
        try {
            for (window in service.windows) {
                if (!seen.add(window.id)) continue
                val r = window.root ?: continue
                Log.d(TAG, "allRoots: window id=${window.id} type=${window.type}")
                roots.add(r)
            }
        } catch (e: Exception) { Log.e(TAG, "allRoots failed", e) }
        if (roots.isEmpty()) { service.rootInActiveWindow?.let { roots.add(it) } }
        Log.d(TAG, "allRoots: ${roots.size} root(s)")
        return roots
    }

    // ─── Existing: handleDownloadAiOutput (unchanged) ─────────────────────

    suspend fun handleDownloadAiOutput(): Boolean = withContext(Dispatchers.IO) {
        if (!bringClaudeToFront()) { Log.e(TAG, "Failed to bring Claude to foreground"); return@withContext false }
        delay(2000)
        val rootNode = service.rootInActiveWindow ?: run { Log.e(TAG, "No root node"); return@withContext false }
        val clickTimestamp = System.currentTimeMillis()
        val downloadsDir   = File(DOWNLOADS_DIR)
        val beforeSnapshot = downloadsDir.listFiles()?.map { it.name }?.toSet() ?: emptySet()
        Log.d(TAG, "Downloads snapshot: ${beforeSnapshot.size} files")
        val scrollContainer = findScrollContainer(rootNode)
        if (scrollContainer != null) {
            var scrolled = true
            while (scrolled) { scrolled = scrollContainer.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD); if (scrolled) Thread.sleep(250) }
            Log.d(TAG, "Reached bottom of chat")
            scrollContainer.recycle()
        }
        rootNode.recycle()
        delay(500)
        val rootNode2 = service.rootInActiveWindow ?: run { Log.e(TAG, "No root after scroll"); return@withContext false }
        logRootSnapshot(rootNode2, "pre-download-click")
        logNodeSearch(rootNode2, "ai-output")
        val screenWidth  = service.resources.displayMetrics.widthPixels
        val screenHeight = service.resources.displayMetrics.heightPixels
        var clickDispatched = false
        var gestureTarget: Rect? = null
        val semanticNode = findSemanticDownloadCard(rootNode2)
        if (semanticNode != null) {
            val bounds = Rect(); semanticNode.getBoundsInScreen(bounds)
            logClickAttempt(semanticNode, "semantic-download")
            if (semanticNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { clickDispatched = true }
            else { gestureTarget = bounds }
            semanticNode.recycle()
        }
        if (!clickDispatched && gestureTarget == null) {
            val candidate = findGeometricDownloadCard(rootNode2, screenWidth, screenHeight)
            if (candidate != null) {
                val bounds = Rect(); candidate.getBoundsInScreen(bounds)
                if (candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { clickDispatched = true }
                else { gestureTarget = bounds }
                candidate.recycle()
            } else {
                dumpRootIfEnabled("geometric-exhausted"); rootNode2.recycle(); return@withContext false
            }
        }
        rootNode2.recycle()
        if (!clickDispatched && gestureTarget != null) { dispatchTap(gestureTarget.centerX().toFloat(), gestureTarget.centerY().toFloat()); clickDispatched = true }
        delay(1500)
        dismissResolverIfPresent()
        val newFile = waitForNewDownload(downloadsDir, beforeSnapshot, clickTimestamp) ?: run { Log.e(TAG, "Download timeout"); return@withContext false }
        val canonical = File(CANONICAL_DOWNLOAD_PATH)
        if (newFile.absolutePath != canonical.absolutePath) { newFile.renameTo(canonical); Log.d(TAG, "Renamed '${newFile.name}' -> 'ai-output.txt'") }
        Log.d(TAG, "Download complete: ${canonical.absolutePath} (${canonical.length()} bytes)")
        true
    }

    // ─── Existing: handleBuildFailure (unchanged) ─────────────────────────

    suspend fun handleBuildFailure(): Boolean = withContext(Dispatchers.IO) {
        if (!bringClaudeToFront()) { Log.e(TAG, "Failed to bring Claude to foreground"); return@withContext false }
        delay(3000)
        val errorReportPath = "/sdcard/ai-automation/build-error-report.txt"
        if (!File(errorReportPath).exists()) { Log.e(TAG, "build-error-report.txt not found"); return@withContext false }
        if (!ensureAttachmentSheetOpen()) return@withContext false
        val sheetReady = waitForAttachmentSheet(8000)
        if (!sheetReady) { Log.e(TAG, "Sheet never appeared"); dumpRootIfEnabled("sheet-wait-timeout"); return@withContext false }
        logWindowInfo()
        val filesClicked = clickFilesTile()
        if (!filesClicked) { Log.e(TAG, "Failed to click Files tile"); dumpRootIfEnabled("files-tile-not-found"); return@withContext false }
        delay(2000)
        val fileItem = findFileInPicker("build-error-report") ?: run { Log.e(TAG, "build-error-report.txt not found in picker"); dumpRootIfEnabled("build-error-report-not-found"); return@withContext false }
        logClickAttempt(fileItem, "build-error-report")
        if (!fileItem.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { Log.e(TAG, "Failed to select build-error-report.txt"); fileItem.recycle(); return@withContext false }
        fileItem.recycle()
        delay(1000)
        val message = "Build failed. See attached error report."
        if (!setInputAndSend(message)) return@withContext false
        true
    }

    // ─── Session 20: handleSendDump — agent loop variant ──────────────────
    // Attaches new_dump.txt from /sdcard/Download/ and sends the task prompt.
    // Mirrors handleBuildFailure() but targets new_dump.txt and uses the
    // provided task string as the message instead of a fixed error string.

    suspend fun handleSendDump(task: String, dumpName: String = "new_dump.txt"): Boolean =
        withContext(Dispatchers.IO) {
            Log.d(TAG, "handleSendDump: task=${task.take(80)} dump=$dumpName")

            if (!bringClaudeToFront()) {
                Log.e(TAG, "handleSendDump: failed to bring Claude to foreground")
                return@withContext false
            }
            delay(3000)

            val dumpPath = "/sdcard/Download/$dumpName"
            if (!File(dumpPath).exists()) {
                Log.e(TAG, "handleSendDump: dump file not found at $dumpPath")
                return@withContext false
            }

            // Open attachment sheet
            if (!ensureAttachmentSheetOpen()) return@withContext false
            val sheetReady = waitForAttachmentSheet(8000)
            if (!sheetReady) {
                Log.e(TAG, "handleSendDump: attachment sheet never appeared")
                dumpRootIfEnabled("agent-sheet-wait-timeout")
                return@withContext false
            }

            // Click Files tile
            val filesClicked = clickFilesTile()
            if (!filesClicked) {
                Log.e(TAG, "handleSendDump: failed to click Files tile")
                dumpRootIfEnabled("agent-files-tile-not-found")
                return@withContext false
            }
            delay(2000)

            // Find dump file in picker (search by name without extension)
            val searchName = dumpName.removeSuffix(".txt")
            val fileItem = findFileInPicker(searchName) ?: run {
                Log.e(TAG, "handleSendDump: $dumpName not found in picker")
                dumpRootIfEnabled("agent-dump-not-found")
                return@withContext false
            }

            logClickAttempt(fileItem, "agent-dump-file")
            if (!fileItem.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                Log.e(TAG, "handleSendDump: failed to select $dumpName")
                fileItem.recycle()
                return@withContext false
            }
            Log.d(TAG, "handleSendDump: selected $dumpName")
            fileItem.recycle()
            delay(1000)

            // Set task message and send
            if (!setInputAndSend(task)) return@withContext false

            Log.d(TAG, "handleSendDump: dump sent successfully")
            true
        }

    // ─── Shared helpers ───────────────────────────────────────────────────

    /** Opens attachment sheet — shared by handleBuildFailure and handleSendDump. */
    private suspend fun ensureAttachmentSheetOpen(): Boolean {
        if (isAttachmentSheetOpen()) return true
        var opened = false
        for (attempt in 1..3) {
            logWindowInfo()
            val btn = findAddToChatButton()
            if (btn != null) {
                logClickAttempt(btn, "add-to-chat-attempt$attempt")
                val clicked = btn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                btn.recycle()
                if (clicked) {
                    delay(1200)
                    if (isAttachmentSheetOpen()) { opened = true; break }
                }
            }
            Log.w(TAG, "Node click failed attempt $attempt — gesture tap")
            dispatchTap(ADD_TO_CHAT_BTN_X, ADD_TO_CHAT_BTN_Y)
            delay(1500)
            if (isAttachmentSheetOpen()) { opened = true; break }
        }
        if (!opened) {
            Log.e(TAG, "Could not open attachment sheet after 3 attempts")
            dumpRootIfEnabled("add-to-chat-not-found")
        }
        return opened
    }

    /**
     * Finds a file in the system file picker by searching for a filename keyword.
     * Tries Recent first, then picker search, then folder navigation.
     * Extracted from handleBuildFailure so both modes can reuse it.
     */
    private suspend fun findFileInPicker(keyword: String): AccessibilityNodeInfo? {
        // Poll Recent for up to 6s
        val deadline = System.currentTimeMillis() + 6000
        while (System.currentTimeMillis() < deadline) {
            val roots = allRoots()
            for (root in roots) {
                logNodeSearch(root, keyword)
                val item = findClickableByFuzzyText(root, keyword)
                if (item != null) {
                    roots.filter { it != root }.forEach { runCatching { it.recycle() } }
                    return item
                }
                root.recycle()
            }
            delay(500)
        }

        // Try picker search
        Log.w(TAG, "findFileInPicker: '$keyword' not in Recent — trying picker search")
        dispatchTap(PICKER_SEARCH_BTN_X, PICKER_SEARCH_BTN_Y)
        delay(800)
        val searchRoots = allRoots()
        var searchField: AccessibilityNodeInfo? = null
        for (root in searchRoots) {
            val field = findEditableNode(root)
            if (field != null) { searchField = field; break }
            root.recycle()
        }
        if (searchField != null) {
            searchField.performAction(
                AccessibilityNodeInfo.ACTION_SET_TEXT,
                android.os.Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, keyword)
                }
            )
            searchField.recycle()
            searchRoots.forEach { runCatching { it.recycle() } }
            delay(1000)
            val searchDeadline = System.currentTimeMillis() + 5000
            while (System.currentTimeMillis() < searchDeadline) {
                val roots = allRoots()
                for (root in roots) {
                    val item = findClickableByFuzzyText(root, keyword)
                    if (item != null) { roots.filter { it != root }.forEach { runCatching { it.recycle() } }; return item }
                    root.recycle()
                }
                delay(500)
            }
        } else {
            searchRoots.forEach { runCatching { it.recycle() } }
        }

        // Last resort: browse
        Log.w(TAG, "findFileInPicker: search failed — trying Browse navigation")
        dispatchTap(PICKER_MENU_BTN_X, PICKER_MENU_BTN_Y)
        delay(800)
        val drawerRoots = allRoots()
        for (root in drawerRoots) {
            val browse = findClickableByFuzzyText(root, "Browse")
                ?: findClickableByFuzzyText(root, "Internal Storage")
                ?: findClickableByFuzzyText(root, "Files")
            if (browse != null) {
                browse.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                browse.recycle(); root.recycle()
                drawerRoots.filter { it != root }.forEach { runCatching { it.recycle() } }
                break
            }
            root.recycle()
        }
        delay(1000)
        val navDeadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < navDeadline) {
            val roots = allRoots()
            for (root in roots) {
                val item = findClickableByFuzzyText(root, keyword)
                if (item != null) { roots.filter { it != root }.forEach { runCatching { it.recycle() } }; return item }
                root.recycle()
            }
            delay(500)
        }
        return null
    }

    /** Set message text in Claude input field and tap Send. */
    private suspend fun setInputAndSend(message: String): Boolean {
        val inputRoots = allRoots()
        var inputField: AccessibilityNodeInfo? = null
        for (root in inputRoots) {
            val field = findEditableNode(root)
            if (field != null) { inputField = field; break }
            root.recycle()
        }
        if (inputField == null) {
            Log.e(TAG, "setInputAndSend: input field not found")
            dumpRootIfEnabled("input-field-not-found")
            inputRoots.forEach { runCatching { it.recycle() } }
            return false
        }
        val ok = inputField.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            android.os.Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, message)
            }
        )
        inputField.recycle()
        inputRoots.forEach { runCatching { it.recycle() } }
        if (!ok) { Log.e(TAG, "setInputAndSend: failed to set text"); return false }
        delay(500)

        val sendRoots = allRoots()
        var sendButton: AccessibilityNodeInfo? = null
        for (root in sendRoots) {
            val btn = findNodeByContentDescriptionFuzzy(root, "Send")
            if (btn != null) { sendButton = btn; break }
            root.recycle()
        }
        if (sendButton == null) {
            Log.e(TAG, "setInputAndSend: Send button not found")
            dumpRootIfEnabled("send-button-not-found")
            sendRoots.forEach { runCatching { it.recycle() } }
            return false
        }
        logClickAttempt(sendButton, "send")
        val sent = sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        sendButton.recycle()
        sendRoots.forEach { runCatching { it.recycle() } }
        if (!sent) { Log.e(TAG, "setInputAndSend: failed to click Send"); return false }
        Log.d(TAG, "setInputAndSend: message sent: ${message.take(60)}")
        return true
    }

    // ─── Attachment sheet helpers (unchanged) ────────────────────────────

    private fun isAttachmentSheetOpen(): Boolean {
        val roots = allRoots()
        for ((idx, root) in roots.withIndex()) {
            val nodes = root.findAccessibilityNodeInfosByText("Close sheet")
            if (nodes.isNotEmpty()) {
                nodes.forEach { it.recycle() }; root.recycle()
                roots.drop(idx + 1).forEach { runCatching { it.recycle() } }
                return true
            }
            nodes.forEach { it.recycle() }
            if (subtreeContainsDesc(root, "Close sheet")) {
                root.recycle(); roots.drop(idx + 1).forEach { runCatching { it.recycle() } }
                return true
            }
            root.recycle()
        }
        return false
    }

    private fun subtreeContainsDesc(node: AccessibilityNodeInfo, desc: String): Boolean {
        if ((node.contentDescription?.toString() ?: "").contains(desc, ignoreCase = true)) return true
        for (i in 0 until node.childCount) { val c = node.getChild(i) ?: continue; val f = subtreeContainsDesc(c, desc); c.recycle(); if (f) return true }
        return false
    }

    private suspend fun waitForAttachmentSheet(timeoutMs: Long = 8000): Boolean = withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() + timeoutMs
        var polls = 0
        while (System.currentTimeMillis() < deadline) {
            polls++
            if (isAttachmentSheetOpen()) { Log.d(TAG, "Sheet confirmed after $polls polls"); return@withContext true }
            delay(200)
        }
        Log.e(TAG, "waitForAttachmentSheet timed out after $polls polls")
        false
    }

    private fun findAddToChatButton(): AccessibilityNodeInfo? {
        for (root in allRoots()) {
            val btn = findNodeByContentDescriptionFuzzy(root, "Add to chat") ?: findClickableByFuzzyText(root, "Add to chat")
            root.recycle()
            if (btn != null) return btn
        }
        return null
    }

    private fun clickFilesTile(): Boolean {
        val roots = allRoots()
        for ((idx, root) in roots.withIndex()) {
            val sheetScrollView = findSheetScrollView(root)
            if (sheetScrollView != null) {
                val tileRow = sheetScrollView.getChild(0)
                if (tileRow != null) {
                    val lastTileIdx = tileRow.childCount - 1
                    if (lastTileIdx >= 0) {
                        val filesTile = tileRow.getChild(lastTileIdx)
                        if (filesTile != null) {
                            val fb = Rect(); filesTile.getBoundsInScreen(fb)
                            val clicked = filesTile.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            filesTile.recycle(); tileRow.recycle(); sheetScrollView.recycle(); root.recycle()
                            roots.drop(idx + 1).forEach { runCatching { it.recycle() } }
                            if (clicked) return true
                            dispatchTap(fb.centerX().toFloat(), fb.centerY().toFloat())
                            return true
                        }
                    }
                    tileRow?.recycle()
                }
                sheetScrollView.recycle()
            }
            root.recycle()
        }
        Log.w(TAG, "clickFilesTile: fallback gesture at ($FILES_TILE_FALLBACK_X,$FILES_TILE_FALLBACK_Y)")
        dispatchTap(FILES_TILE_FALLBACK_X, FILES_TILE_FALLBACK_Y)
        return true
    }

    private fun findSheetScrollView(root: AccessibilityNodeInfo): AccessibilityNodeInfo? = findSheetScrollViewRecursive(root)

    private fun findSheetScrollViewRecursive(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.className?.toString() == "android.widget.ScrollView") {
            val parent = node.parent
            if (parent != null) {
                for (i in 0 until parent.childCount) {
                    val sibling = parent.getChild(i) ?: continue
                    val hasDrag = subtreeContainsDesc(sibling, "Drag handle")
                    sibling.recycle()
                    if (hasDrag) { parent.recycle(); return node }
                }
                parent.recycle()
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findSheetScrollViewRecursive(child)
            if (result != null) { child.recycle(); return result }
            child.recycle()
        }
        return null
    }

    private suspend fun waitForNewDownload(dir: File, beforeSnapshot: Set<String>, clickTimestamp: Long): File? = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < MAX_DOWNLOAD_WAIT_MS) {
            val allFiles = dir.listFiles()?.filter { it.length() > 0 } ?: emptyList()
            allFiles.firstOrNull { it.name !in beforeSnapshot }?.let { return@withContext it }
            allFiles.filter { it.lastModified() > clickTimestamp }.maxByOrNull { it.lastModified() }?.let { return@withContext it }
            delay(POLL_INTERVAL_MS)
        }
        null
    }

    private fun findSemanticDownloadCard(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        findClickableByFuzzyText(root, "output")?.let { return it }
        for (desc in listOf("output", "ai-output", "ai_output")) {
            findNodeByContentDescriptionFuzzy(root, desc)?.let { return it }
        }
        return null
    }

    private fun findGeometricDownloadCard(root: AccessibilityNodeInfo, screenWidth: Int, screenHeight: Int): AccessibilityNodeInfo? {
        val candidates = mutableListOf<Pair<AccessibilityNodeInfo, Rect>>()
        collectGeometricCandidates(root, screenWidth, screenHeight, candidates)
        if (candidates.isEmpty()) return null
        candidates.sortWith(compareByDescending<Pair<AccessibilityNodeInfo, Rect>> { if (subtreeContainsKeyword(it.first, CARD_CONFIDENCE_KEYWORDS)) 1 else 0 }.thenByDescending { it.second.centerY() })
        val winner = candidates.first()
        candidates.drop(1).forEach { it.first.recycle() }
        return winner.first
    }

    private fun collectGeometricCandidates(node: AccessibilityNodeInfo, screenWidth: Int, screenHeight: Int, results: MutableList<Pair<AccessibilityNodeInfo, Rect>>) {
        val bounds = Rect(); node.getBoundsInScreen(bounds)
        val height = bounds.height(); val width = bounds.width(); val centerY = bounds.centerY()
        val minCardWidth  = (screenWidth * CARD_MIN_WIDTH_FRACTION).toInt()
        val maxCardHeight = minOf(CARD_MAX_HEIGHT_PX, (screenHeight * CARD_MAX_HEIGHT_FRACTION).toInt())
        val cls = node.className?.toString() ?: ""
        if (node.isClickable && !node.isScrollable && height >= CARD_MIN_HEIGHT_PX && height <= maxCardHeight && width >= minCardWidth && centerY > screenHeight * 0.30f && cls.contains("View") && !cls.contains("EditText") && !cls.contains("Button"))
            results.add(Pair(node, Rect(bounds)))
        for (i in 0 until node.childCount) { val c = node.getChild(i) ?: continue; collectGeometricCandidates(c, screenWidth, screenHeight, results) }
    }

    private fun subtreeContainsKeyword(node: AccessibilityNodeInfo, keywords: List<String>): Boolean {
        val text = node.text?.toString() ?: ""; val desc = node.contentDescription?.toString() ?: ""
        if (keywords.any { text.contains(it, ignoreCase = true) || desc.contains(it, ignoreCase = true) }) return true
        for (i in 0 until node.childCount) { val c = node.getChild(i) ?: continue; val f = subtreeContainsKeyword(c, keywords); c.recycle(); if (f) return true }
        return false
    }

    private fun dispatchTap(x: Float, y: Float) {
        try {
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0, 50)
            service.dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
        } catch (e: Exception) { Log.e(TAG, "dispatchTap failed", e) }
    }

    private fun dismissResolverIfPresent() {
        val root = service.rootInActiveWindow ?: return
        if (root.packageName?.toString() != "android") { root.recycle(); return }
        Log.d(TAG, "Resolver detected — dismissing")
        val cancelNode = findClickableByFuzzyText(root, "Cancel") ?: findNodeByContentDescriptionFuzzy(root, "Cancel")
        if (cancelNode != null) { cancelNode.performAction(AccessibilityNodeInfo.ACTION_CLICK); cancelNode.recycle() }
        else { service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) }
        root.recycle()
    }

    private fun findScrollContainer(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (root.isScrollable && root.packageName?.toString() == CLAUDE_PKG) return root
        for (i in 0 until root.childCount) { val c = root.getChild(i) ?: continue; val r = findScrollContainer(c); if (r != null) { c.recycle(); return r }; c.recycle() }
        return null
    }

    private fun findClickableByFuzzyText(root: AccessibilityNodeInfo, keyword: String): AccessibilityNodeInfo? {
        val nodes = root.findAccessibilityNodeInfosByText(keyword)
        for (node in nodes) {
            if (node.text?.toString()?.contains(keyword, ignoreCase = true) == true) {
                if (node.isClickable) return node
                var parent = node.parent
                while (parent != null) { if (parent.isClickable) return parent; val next = parent.parent; if (next == parent) break; parent = next }
            }
        }
        return null
    }

    private fun findNodeByContentDescriptionFuzzy(root: AccessibilityNodeInfo, keyword: String): AccessibilityNodeInfo? {
        if ((root.contentDescription?.toString() ?: "").contains(keyword, ignoreCase = true)) {
            if (root.isClickable) return root
            var parent = root.parent
            while (parent != null) { if (parent.isClickable) return parent; val next = parent.parent; if (next == parent) break; parent = next }
        }
        for (i in 0 until root.childCount) { val c = root.getChild(i) ?: continue; val r = findNodeByContentDescriptionFuzzy(c, keyword); if (r != null) return r; c.recycle() }
        return null
    }

    private fun findEditableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (root.isEditable) return root
        for (i in 0 until root.childCount) { val c = root.getChild(i) ?: continue; val r = findEditableNode(c); if (r != null) return r; c.recycle() }
        return null
    }

    private fun bringClaudeToFront(): Boolean {
        return try {
            val intent = service.packageManager.getLaunchIntentForPackage(CLAUDE_PKG)
            if (intent != null) { intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK); service.startActivity(intent); true }
            else false
        } catch (e: Exception) { Log.e(TAG, "Failed to launch Claude", e); false }
    }
}
