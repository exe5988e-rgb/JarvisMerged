package com.jarvismini.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Loopback-only control bridge for the Termux MCP "jarvis_*" tools.
 *
 * Lets the MCP server read the screen and act on this phone without adb, so it keeps
 * working with Wi-Fi off. Safety model:
 *  - binds 127.0.0.1 only, and every request needs the X-Jarvis-Token header
 *    (random token generated on first start, kept in this app's private prefs);
 *  - refuses to read or act on banking/payment/installer apps (same rule as the MCP side);
 *  - runs only while the user has the accessibility service enabled.
 */
class A11yBridge(private val svc: AccessibilityService) {

    companion object {
        const val PORT = 38181
        private const val TAG = "A11yBridge"
        private const val MAX_BODY = 64 * 1024
        private const val MAX_NODES = 3000
        private const val MAX_DEPTH = 60

        @Volatile private var lastPkg: String = ""
        @Volatile private var lastClass: String = ""

        private val SENSITIVE = Regex(
            "(^|[._])(bank|banking|upi|paisa|phonepe|paytm|wallet|bhim|razorpay|gpay|" +
                "payment|payments|netbanking|sbi|hdfc|icici|axisbank|kotak)([._]|$)"
        )
        private val BLOCKED = setOf(
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller"
        )

        /** Called from the service for every accessibility event (cheap). */
        fun noteEvent(event: AccessibilityEvent) {
            if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
            val pkg = event.packageName?.toString() ?: return
            val cls = event.className?.toString() ?: return
            lastPkg = pkg
            lastClass = cls
        }

        fun isSensitive(pkg: String): Boolean {
            if (pkg.isEmpty()) return false
            val p = pkg.lowercase()
            return p in BLOCKED || p.contains("bank") || SENSITIVE.containsMatchIn(p)
        }
    }

    private var server: ServerSocket? = null
    private val pool = Executors.newCachedThreadPool()
    private val shotExec = Executors.newSingleThreadExecutor()
    private val actLock = Any()
    private val token: String = loadToken()

    // ---------------------------------------------------------------- lifecycle

    @Synchronized
    fun start() {
        stop()
        try {
            val ss = ServerSocket()
            ss.reuseAddress = true
            ss.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), PORT), 8)
            server = ss
            Thread({ serveLoop(ss) }, "a11y-bridge").apply { isDaemon = true }.start()
            Log.i(TAG, "listening on 127.0.0.1:$PORT")
        } catch (e: Exception) {
            Log.e(TAG, "could not start: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    @Synchronized
    fun stop() {
        try { server?.close() } catch (_: Exception) {}
        server = null
    }

    private fun loadToken(): String {
        val prefs = svc.getSharedPreferences("a11y_bridge", Context.MODE_PRIVATE)
        prefs.getString("token", null)?.let { if (it.length >= 32) return it }
        val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val t = bytes.joinToString("") { "%02x".format(it) }
        prefs.edit().putString("token", t).apply()
        return t
    }

    // --------------------------------------------------------------- http plumbing

    private fun serveLoop(ss: ServerSocket) {
        while (!ss.isClosed) {
            val s = try { ss.accept() } catch (_: Exception) { break }
            try { pool.execute { handle(s) } } catch (_: Exception) { try { s.close() } catch (_: Exception) {} }
        }
    }

    private fun readLine(inp: BufferedInputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = inp.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) break
            if (b != '\r'.code) sb.append(b.toChar())
            if (sb.length > 8192) return null
        }
        return sb.toString()
    }

    private fun readBody(inp: BufferedInputStream, len: Int): ByteArray {
        val buf = ByteArray(len)
        var off = 0
        while (off < len) {
            val n = inp.read(buf, off, len - off)
            if (n < 0) break
            off += n
        }
        return buf.copyOf(off)
    }

    private fun handle(sock: Socket) {
        sock.use {
            try {
                sock.soTimeout = 10000
                val inp = BufferedInputStream(sock.getInputStream())
                val reqLine = readLine(inp) ?: return
                val parts = reqLine.split(" ")
                if (parts.size < 2) return
                val method = parts[0]
                val path = parts[1].substringBefore('?')
                val headers = HashMap<String, String>()
                while (true) {
                    val l = readLine(inp) ?: break
                    if (l.isEmpty()) break
                    val i = l.indexOf(':')
                    if (i > 0) headers[l.substring(0, i).trim().lowercase()] = l.substring(i + 1).trim()
                }
                val len = headers["content-length"]?.toIntOrNull() ?: 0
                if (len < 0 || len > MAX_BODY) {
                    respond(sock, 413, "application/json", err("body too large")); return
                }
                val body = if (len > 0) String(readBody(inp, len), Charsets.UTF_8) else ""
                if (!tokenOk(headers["x-jarvis-token"])) {
                    respond(sock, 401, "application/json", err("unauthorized")); return
                }
                route(sock, method, path, body)
            } catch (e: Exception) {
                Log.w(TAG, "request failed: ${e.javaClass.simpleName}")
            }
        }
    }

    private fun tokenOk(given: String?): Boolean {
        if (given == null) return false
        return MessageDigest.isEqual(given.toByteArray(), token.toByteArray())
    }

    private fun err(msg: String): ByteArray =
        JSONObject().put("ok", false).put("error", msg).toString().toByteArray()

    private fun respond(sock: Socket, code: Int, type: String, body: ByteArray) {
        val reason = when (code) {
            200 -> "OK"; 400 -> "Bad Request"; 401 -> "Unauthorized"; 403 -> "Forbidden"
            404 -> "Not Found"; 413 -> "Payload Too Large"; 503 -> "Service Unavailable"
            else -> "Error"
        }
        val out = sock.getOutputStream()
        out.write(
            ("HTTP/1.1 $code $reason\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\n" +
                "Cache-Control: no-store\r\nConnection: close\r\n\r\n").toByteArray()
        )
        out.write(body)
        out.flush()
    }

    private fun json(sock: Socket, code: Int, o: JSONObject) =
        respond(sock, code, "application/json", o.toString().toByteArray())

    // ------------------------------------------------------------------- routing

    private fun route(sock: Socket, method: String, path: String, body: String) {
        when {
            method == "GET" && path == "/health" ->
                json(sock, 200, JSONObject().put("ok", true).put("sdk", Build.VERSION.SDK_INT))

            method == "GET" && path == "/size" -> {
                val (w, h) = realSize()
                json(sock, 200, JSONObject().put("ok", true).put("w", w).put("h", h))
            }

            method == "GET" && path == "/foreground" -> {
                val pkg = foregroundPkg()
                val act = if (pkg.isNotEmpty() && pkg == lastPkg) lastClass else ""
                json(sock, 200, JSONObject().put("ok", true).put("pkg", pkg).put("activity", act))
            }

            method == "GET" && path == "/status" -> json(sock, 200, status())

            method == "GET" && path == "/ui.xml" -> {
                val root = svc.rootInActiveWindow
                    ?: return json(sock, 503, JSONObject().put("ok", false).put("error", "no active window"))
                val pkg = root.packageName?.toString() ?: ""
                if (isSensitive(pkg)) {
                    return json(sock, 403, JSONObject().put("ok", false).put("error", "sensitive app in foreground: $pkg"))
                }
                respond(sock, 200, "application/xml; charset=utf-8", dumpXml(root).toByteArray(Charsets.UTF_8))
            }

            method == "GET" && path == "/screenshot" -> {
                if (Build.VERSION.SDK_INT < 30) {
                    return json(sock, 503, JSONObject().put("ok", false).put("error", "screenshot needs Android 11+"))
                }
                val pkg = foregroundPkg()
                if (isSensitive(pkg)) {
                    return json(sock, 403, JSONObject().put("ok", false).put("error", "sensitive app in foreground: $pkg"))
                }
                val png = screenshotPng()
                    ?: return json(sock, 503, JSONObject().put("ok", false).put("error", "screenshot failed"))
                respond(sock, 200, "image/png", png)
            }

            method == "POST" && path == "/act" -> {
                val o = try { JSONObject(body) } catch (_: Exception) {
                    return json(sock, 400, JSONObject().put("ok", false).put("error", "invalid json"))
                }
                val problem = synchronized(actLock) { act(o) }
                if (problem == null) json(sock, 200, JSONObject().put("ok", true))
                else json(sock, 400, JSONObject().put("ok", false).put("error", problem))
            }

            else -> json(sock, 404, JSONObject().put("ok", false).put("error", "not found"))
        }
    }

    // ------------------------------------------------------------------- reading

    private fun foregroundPkg(): String = svc.rootInActiveWindow?.packageName?.toString() ?: ""

    @Suppress("DEPRECATION")
    private fun realSize(): Pair<Int, Int> {
        val wm = svc.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.maximumWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val m = DisplayMetrics()
            wm.defaultDisplay.getRealMetrics(m)
            m.widthPixels to m.heightPixels
        }
    }

    private fun status(): JSONObject {
        val o = JSONObject().put("ok", true)
        val b = svc.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (b != null) {
            val level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            o.put("level", if (level >= 0) level * 100 / scale else -1)
            o.put("status", b.getIntExtra(BatteryManager.EXTRA_STATUS, 0))
            o.put("plugged", b.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0)
            o.put("temp", b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0))
        }
        val cm = svc.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        o.put(
            "net", when {
                caps == null -> "offline"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile"
                else -> "other"
            }
        )
        return o
    }

    private fun esc(s: String?): String {
        if (s.isNullOrEmpty()) return ""
        val sb = StringBuilder(s.length + 8)
        for (c in s) {
            when {
                c == '&' -> sb.append("&amp;")
                c == '<' -> sb.append("&lt;")
                c == '>' -> sb.append("&gt;")
                c == '"' -> sb.append("&quot;")
                c == '\t' || c == '\n' || c == '\r' -> sb.append(' ')
                c.code < 0x20 || c.code == 0x7f -> {}
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    /** uiautomator-compatible hierarchy so the MCP parser needs no changes. */
    private fun dumpXml(root: AccessibilityNodeInfo): String {
        val sb = StringBuilder(32768)
        sb.append("<?xml version='1.0' encoding='UTF-8' standalone='yes' ?><hierarchy rotation=\"0\">")
        var count = 0
        val r = Rect()

        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            if (count >= MAX_NODES || depth > MAX_DEPTH) return
            if (!n.isVisibleToUser) return
            count++
            n.getBoundsInScreen(r)
            val hint = Build.VERSION.SDK_INT >= 26 && n.isShowingHintText
            val text = if (n.isPassword || hint) "" else n.text?.toString()
            sb.append("<node index=\"0\" text=\"").append(esc(text))
                .append("\" resource-id=\"").append(esc(n.viewIdResourceName))
                .append("\" class=\"").append(esc(n.className?.toString()))
                .append("\" package=\"").append(esc(n.packageName?.toString()))
                .append("\" content-desc=\"").append(esc(n.contentDescription?.toString()))
                .append("\" checkable=\"").append(n.isCheckable)
                .append("\" checked=\"").append(n.isChecked)
                .append("\" clickable=\"").append(n.isClickable)
                .append("\" enabled=\"").append(n.isEnabled)
                .append("\" focusable=\"").append(n.isFocusable)
                .append("\" focused=\"").append(n.isFocused)
                .append("\" scrollable=\"").append(n.isScrollable)
                .append("\" long-clickable=\"").append(n.isLongClickable)
                .append("\" password=\"").append(n.isPassword)
                .append("\" selected=\"").append(n.isSelected)
                .append("\" bounds=\"[").append(r.left).append(',').append(r.top)
                .append("][").append(r.right).append(',').append(r.bottom).append("]\">")
            for (i in 0 until n.childCount) {
                val c = n.getChild(i) ?: continue
                walk(c, depth + 1)
            }
            sb.append("</node>")
        }

        walk(root, 0)
        sb.append("</hierarchy>")
        return sb.toString()
    }

    private fun screenshotPng(): ByteArray? {
        if (Build.VERSION.SDK_INT < 30) return null
        val latch = CountDownLatch(1)
        val out = AtomicReference<ByteArray?>(null)
        svc.takeScreenshot(
            Display.DEFAULT_DISPLAY, shotExec,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    try {
                        val hw = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                        val bmp = hw?.copy(Bitmap.Config.ARGB_8888, false)
                        if (bmp != null) {
                            val bos = ByteArrayOutputStream(1 shl 20)
                            bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
                            out.set(bos.toByteArray())
                            bmp.recycle()
                        }
                        hw?.recycle()
                    } catch (e: Exception) {
                        Log.w(TAG, "screenshot encode failed: ${e.javaClass.simpleName}")
                    } finally {
                        try { result.hardwareBuffer.close() } catch (_: Exception) {}
                        latch.countDown()
                    }
                }

                override fun onFailure(errorCode: Int) {
                    Log.w(TAG, "screenshot failed code=$errorCode")
                    latch.countDown()
                }
            }
        )
        latch.await(8, TimeUnit.SECONDS)
        return out.get()
    }

    // -------------------------------------------------------------------- acting

    /** Returns null on success, or an error message. */
    private fun act(o: JSONObject): String? {
        val op = o.optString("op").lowercase()
        val pkg = foregroundPkg()
        if (isSensitive(pkg)) {
            val key = o.optString("key").lowercase()
            val allowed = op == "key" && (key == "back" || key == "home")
            if (!allowed) return "sensitive app in foreground: $pkg (only back/home allowed)"
        }
        return when (op) {
            "tap" -> gesture(o.optDouble("x"), o.optDouble("y"), o.optDouble("x"), o.optDouble("y"), 50)
            "long_press" -> gesture(
                o.optDouble("x"), o.optDouble("y"), o.optDouble("x"), o.optDouble("y"),
                o.optLong("ms", 800).coerceIn(300, 3000)
            )
            "swipe" -> gesture(
                o.optDouble("x"), o.optDouble("y"), o.optDouble("x2"), o.optDouble("y2"),
                o.optLong("ms", 300).coerceIn(100, 2000)
            )
            "text" -> typeText(o.optString("text"))
            "key" -> pressKey(o.optString("key").lowercase())
            "launch" -> launch(o.optString("package"))
            else -> "unknown op"
        }
    }

    private fun gesture(x1: Double, y1: Double, x2: Double, y2: Double, dur: Long): String? {
        if (x1.isNaN() || y1.isNaN() || x2.isNaN() || y2.isNaN()) return "missing coordinates"
        val (w, h) = realSize()
        if (x1 < 0 || y1 < 0 || x2 < 0 || y2 < 0 || x1 >= w || x2 >= w || y1 >= h || y2 >= h) {
            return "coordinates outside ${w}x$h"
        }
        val p = Path().apply {
            moveTo(x1.toFloat(), y1.toFloat())
            if (x1 != x2 || y1 != y2) lineTo(x2.toFloat(), y2.toFloat())
        }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0, dur.coerceAtLeast(1)))
            .build()
        val latch = CountDownLatch(1)
        val ok = AtomicBoolean(false)
        val sent = svc.dispatchGesture(g, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                ok.set(true); latch.countDown()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                latch.countDown()
            }
        }, null)
        if (!sent) return "gesture not dispatched (is canPerformGestures enabled?)"
        latch.await(dur + 3000, TimeUnit.MILLISECONDS)
        return if (ok.get()) null else "gesture cancelled"
    }

    private fun focusedInput(): AccessibilityNodeInfo? =
        svc.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

    private fun currentText(n: AccessibilityNodeInfo): String {
        val hint = Build.VERSION.SDK_INT >= 26 && n.isShowingHintText
        if (hint) return ""
        val raw = n.text?.toString() ?: ""
        // Some apps (WhatsApp's search bar) report the placeholder as real text, often behind
        // zero-width characters, without setting isShowingHintText. Treat that as an empty field
        // so typing replaces the placeholder instead of being spliced into it.
        val bare = raw.filter { it != '\u200b' && it != '\u200c' && it != '\u200d' && it != '\ufeff' }
        if (bare.isEmpty()) return ""
        val h = if (Build.VERSION.SDK_INT >= 26) n.hintText?.toString()?.trim() else null
        if (!h.isNullOrEmpty() && bare.trim() == h) return ""
        return raw
    }

    private fun setText(n: AccessibilityNodeInfo, value: String, cursor: Int): String? {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        if (!n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return "set text rejected by the app"
        val sel = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, cursor)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cursor)
        }
        n.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, sel)
        return null
    }

    private fun typeText(t: String): String? {
        if (t.isEmpty() || t.length > 300) return "text must be 1-300 chars"
        val n = focusedInput() ?: return "no focused input field"
        if (n.isPassword) return "refusing to type into a password field"
        val cur = currentText(n)
        val s = n.textSelectionStart
        val e = n.textSelectionEnd
        val a = if (s >= 0 && e >= 0) minOf(s, e).coerceAtMost(cur.length) else cur.length
        val b = if (s >= 0 && e >= 0) maxOf(s, e).coerceAtMost(cur.length) else cur.length
        return setText(n, cur.substring(0, a) + t + cur.substring(b), a + t.length)
    }

    private fun pressKey(k: String): String? = when (k) {
        "back" -> if (svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)) null else "back failed"
        "home" -> if (svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)) null else "home failed"
        "recents" -> if (svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)) null else "recents failed"
        "enter" -> {
            val n = focusedInput()
            if (n == null) "no focused input field"
            else if (Build.VERSION.SDK_INT < 30) "enter needs Android 11+"
            else if (n.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)) null
            else "enter rejected by the app"
        }
        "delete" -> {
            val n = focusedInput()
            if (n == null) "no focused input field"
            else if (n.isPassword) "refusing to edit a password field"
            else {
                val cur = currentText(n)
                val s = n.textSelectionStart
                val e = n.textSelectionEnd
                val a = if (s >= 0 && e >= 0) minOf(s, e).coerceAtMost(cur.length) else cur.length
                val b = if (s >= 0 && e >= 0) maxOf(s, e).coerceAtMost(cur.length) else cur.length
                when {
                    a != b -> setText(n, cur.substring(0, a) + cur.substring(b), a)
                    a > 0 -> setText(n, cur.substring(0, a - 1) + cur.substring(a), a - 1)
                    else -> null
                }
            }
        }
        "wake" -> "wake is not supported over accessibility"
        else -> "unknown key"
    }

    private fun launch(pkg: String): String? {
        if (!Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$").matches(pkg)) return "invalid package name"
        if (isSensitive(pkg)) return "$pkg looks like banking/payments or a permission/installer app"
        val i = svc.packageManager.getLaunchIntentForPackage(pkg) ?: return "no launcher activity for $pkg"
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return try { svc.startActivity(i); null } catch (e: Exception) { "launch failed: ${e.javaClass.simpleName}" }
    }
}
