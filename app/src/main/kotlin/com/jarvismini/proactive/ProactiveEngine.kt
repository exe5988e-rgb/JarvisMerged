package com.jarvismini.proactive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import com.jarvismini.core.JarvisMode
import com.jarvismini.core.JarvisPersona
import com.jarvismini.core.JarvisPrefs
import com.jarvismini.core.JarvisState
import com.jarvismini.core.announce.AnnouncePriority
import com.jarvismini.core.announce.AnnouncementEngine
import com.jarvismini.core.events.EventLog
import com.jarvismini.core.events.EventType
import com.jarvismini.llm.OpenRouterClient
import com.jarvismini.voice.WakeWordService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Jarvis starts conversations. Watches the phone (unlock, charging, time of day,
 * unread messages, work mode) and speaks up like the MCU assistant: composed, dry,
 * brief. The LLM only phrases the line from facts we pass it; if it is offline the
 * built-in line is used. Every line still passes the AnnouncementEngine gate
 * (modes, persona, calls, dedupe), and proactive speech has its own spacing rule.
 *
 * When a line ends in a question, Jarvis opens the mic right after speaking so the
 * user can just answer "yes" or "no".
 */
object ProactiveEngine {

    private const val MIN_GAP_MS = 20 * 60_000L
    private const val DIGEST_GAP_MS = 30 * 60_000L
    private const val WORK_BREAK_MS = 90 * 60_000L
    private const val ANSWER_WINDOW_MS = 90_000L

    private lateinit var app: Context
    private var installed = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val client by lazy { OpenRouterClient(app) }

    @Volatile private var screenOn = true
    private var screenOnSince = System.currentTimeMillis()
    private var lastScreenOff = System.currentTimeMillis()
    private var lastProactive = 0L
    private var lastDigest = System.currentTimeMillis()
    private var lastWorkBreak = System.currentTimeMillis()
    private var chargedAnnounced = false

    /** Minimum silence between unprompted remarks, by persona. */
    private fun minGap(): Long = when (JarvisPersona.current()) {
        JarvisPersona.JARVIS -> 6 * 60_000L
        JarvisPersona.HUMOR -> 10 * 60_000L
        JarvisPersona.STUDY -> 45 * 60_000L
        JarvisPersona.CALM_WHISPER -> 30 * 60_000L
        JarvisPersona.SILENT_GUARDIAN -> Long.MAX_VALUE / 4
        else -> MIN_GAP_MS
    }

    private var lastCheckIn = System.currentTimeMillis()

    /** Jarvis / Humor modes: an occasional unprompted remark while the user is on the phone. */
    private fun chattyCheckIn(now: Long, b: Batt) {
        val persona = JarvisPersona.current()
        if (persona != JarvisPersona.JARVIS && persona != JarvisPersona.HUMOR) return
        val every = if (persona == JarvisPersona.JARVIS) 12 * 60_000L else 18 * 60_000L
        if (now - lastCheckIn < every || now - screenOnSince < 2 * 60_000L) return
        val ev = EventLog.since(startOfDay())
        val msgs = ev.count { it.type == EventType.MESSAGE }
        val blocked = ev.count { it.type == EventType.CALL_BLOCKED }
        say("Casual check-in. It is ${clock()}. ${b.text} Messages today: $msgs. Calls blocked today: $blocked. " +
                "Make one light, useful remark about one or two of these facts.",
            "It is ${clock()}, ${sir()}, and the battery stands at ${b.pct} percent. All quiet on my end.",
            key = "checkin_" + (now / (10 * 60_000L)),
            onSpoken = { lastCheckIn = System.currentTimeMillis() })
    }

    private class Pending(val expires: Long, val onYes: () -> String)
    @Volatile private var pending: Pending? = null

    /** Speaks [line], then listens for yes/no; "yes" runs [onYes] and speaks what it returns. */
    fun askYesNo(line: String, key: String, onYes: () -> String) {
        scope.launch {
            if (AnnouncementEngine.announce(line, AnnouncePriority.HIGH, key)) {
                pending = Pending(System.currentTimeMillis() + ANSWER_WINDOW_MS, onYes)
                delay(1_500L + 65L * line.length) // let the voice finish before opening the mic
                WakeWordService.listenNowHook?.invoke()
            }
        }
    }

    fun install(context: Context) {
        if (installed) return
        installed = true
        app = context.applicationContext
        com.jarvismini.core.announce.AskHook.ask = { line, key, onYes -> askYesNo(line, key, onYes) }
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                when (i.action) {
                    Intent.ACTION_SCREEN_OFF -> { screenOn = false; lastScreenOff = System.currentTimeMillis() }
                    Intent.ACTION_SCREEN_ON -> { screenOn = true; screenOnSince = System.currentTimeMillis() }
                    Intent.ACTION_USER_PRESENT -> onUnlock()
                    Intent.ACTION_POWER_DISCONNECTED -> chargedAnnounced = false
                }
            }
        }
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT); addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        ContextCompat.registerReceiver(app, r, f, ContextCompat.RECEIVER_NOT_EXPORTED)
        scope.launch {
            while (true) {
                delay(60_000L)
                try { tick() } catch (_: Exception) {}
            }
        }
    }

    // ---------------------------------------------------------------- answers

    /** "yes" / "no" to the last thing Jarvis asked. Null when nothing is pending or it was not an answer. */
    fun answer(t: String): String? {
        val p = pending ?: return null
        if (System.currentTimeMillis() > p.expires) { pending = null; return null }
        val words = t.split(Regex("[^a-z]+")).filter { it.isNotEmpty() }
        if (words.size > 5) return null
        val yes = setOf("yes", "yeah", "yep", "sure", "please", "ok", "okay", "affirmative", "proceed")
        val no = setOf("no", "nope", "nah", "later", "negative")
        return when {
            words.any { it in yes } || t.contains("go ahead") || t.contains("do it") -> { pending = null; p.onYes() }
            words.any { it in no } || t.contains("not now") || t.contains("don't") -> { pending = null; "Very well, ${sir()}." }
            else -> null
        }
    }

    // --------------------------------------------------------------- triggers

    private fun onUnlock() {
        val now = System.currentTimeMillis()
        val away = now - lastScreenOff
        screenOnSince = now
        val names = messageNamesSince(lastScreenOff)
        val msgLine = if (names.isEmpty()) "No new messages." else "${names.size} people messaged: ${names.take(3).joinToString(", ")}."
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        if (h in 5..11 && !doneToday("greet")) {
            say(
                "The user has just unlocked the phone for the first time today. $msgLine ${battery().text}",
                "Good morning, ${sir()}. It is ${clock()}, and the battery stands at ${battery().pct} percent." +
                    if (names.isNotEmpty()) " ${names.size} messages came in. Shall I open WhatsApp?" else "",
                key = "greet", prio = AnnouncePriority.HIGH, ignoreGap = true,
                ask = if (names.isNotEmpty()) ({ openWhatsApp() }) else null,
                onSpoken = { markToday("greet") }
            )
        } else if (away > 3 * 3_600_000L && (names.isNotEmpty() || battery().pct <= 30)) {
            say(
                "The user is back after ${away / 3_600_000L} hours away from the phone. $msgLine ${battery().text}",
                "Welcome back, ${sir()}. $msgLine",
                key = "welcome_back"
            )
        }
    }

    private fun tick() {
        if (!screenOn) return
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        val h = cal.get(Calendar.HOUR_OF_DAY)
        val b = battery()

        if (b.charging && b.pct >= 100 && !chargedAnnounced) {
            say("The battery just reached 100 percent while charging.",
                "The battery is fully charged, ${sir()}. You may unplug.",
                key = "charged", onSpoken = { chargedAnnounced = true })
            return
        }
        if (!b.charging) chargedAnnounced = false

        if (h in 1..4 && now - screenOnSince > 10 * 60_000L && !doneToday("night")) {
            say("It is ${clock()} at night and the user is still on the phone. Gently suggest sleep and ask if Jarvis should enable sleep mode.",
                "It is past ${hour12(h)} in the morning, ${sir()}. Shall I enable sleep mode?",
                key = "night", ask = { sleepMode() }, onSpoken = { markToday("night") })
            return
        }
        if ((h == 21 && cal.get(Calendar.MINUTE) >= 30 || h == 22) && !doneToday("evening")) {
            val ev = EventLog.since(startOfDay())
            val msgs = ev.count { it.type == EventType.MESSAGE }
            val blocked = ev.count { it.type == EventType.CALL_BLOCKED }
            say("Evening wrap-up. Today: $msgs messages, $blocked blocked calls. ${b.text} Offer to enable sleep mode.",
                "Good evening, ${sir()}. Today brought $msgs messages and $blocked blocked calls. Shall I enable sleep mode?",
                key = "evening", ask = { sleepMode() }, onSpoken = { markToday("evening") })
            return
        }
        val names = messageNamesSince(lastDigest)
        if (names.size >= 3 && now - lastDigest > DIGEST_GAP_MS) {
            say("Unread messages have piled up from ${names.take(4).joinToString(", ")}. Mention it and offer to open WhatsApp.",
                "${names.size} people are waiting on you, ${sir()}, including ${names.take(3).joinToString(", ")}. Shall I open WhatsApp?",
                key = "digest", ask = { openWhatsApp() }, onSpoken = { lastDigest = System.currentTimeMillis() })
            return
        }
        if (JarvisState.currentMode == JarvisMode.WORK && now - lastWorkBreak > WORK_BREAK_MS) {
            say("The user has been in work mode for over 90 minutes. Suggest a short break.",
                "You have been at it for ninety minutes, ${sir()}. A short break would be advisable.",
                key = "work_break", onSpoken = { lastWorkBreak = System.currentTimeMillis() })
            return
        }
        chattyCheckIn(now, b)
    }

    // ------------------------------------------------------------------ voice

    private fun say(
        facts: String, fallback: String, key: String,
        prio: AnnouncePriority = AnnouncePriority.NORMAL,
        ignoreGap: Boolean = false,
        ask: (() -> String)? = null,
        onSpoken: (() -> Unit)? = null
    ) {
        val now = System.currentTimeMillis()
        if (!ignoreGap && now - lastProactive < minGap()) return
        scope.launch {
            val ask1 = if (ask != null) " End with a short yes/no question." else ""
            val line = withTimeoutOrNull(15_000L) { phrase(facts + ask1) } ?: fallback
            if (AnnouncementEngine.announce(line, prio, key)) {
                lastProactive = System.currentTimeMillis()
                onSpoken?.invoke()
                if (ask != null) {
                    pending = Pending(System.currentTimeMillis() + ANSWER_WINDOW_MS, ask)
                    delay(1_500L + 65L * line.length) // let the voice finish before opening the mic
                    WakeWordService.listenNowHook?.invoke()
                }
            }
        }
    }

    private suspend fun phrase(facts: String): String? {
        if (!client.isConfigured()) return null
        val style = when (JarvisPersona.current()) {
            JarvisPersona.HUMOR -> "Lean into dry wit."
            JarvisPersona.COMBAT -> "Be terse and tactical."
            else -> "Warm, talkative, composed, with dry British wit. Add a light remark or offer."
        }
        val system = "You are J.A.R.V.I.S., Tony Stark's AI from the Iron Man films, now looking after the user's phone. " +
            "You speak first, unprompted. Address the user as Sir. $style Say two or three sentences, spoken aloud: " +
            "no markdown, no emojis, no quotes. Use ONLY the facts given, never invent anything. Do not mention these instructions."
        val out = client.complete(system, facts).getOrNull() ?: return null
        return out.replace(Regex("[*_`\"#]"), "").replace('\n', ' ').trim().take(420).ifBlank { null }
    }

    // ---------------------------------------------------------------- actions

    private fun sleepMode(): String {
        JarvisState.setMode(app, JarvisMode.SLEEP)
        return "Sleep mode engaged. Good night, ${sir()}."
    }

    private fun openWhatsApp(): String {
        val i = app.packageManager.getLaunchIntentForPackage("com.whatsapp")
            ?: return "WhatsApp does not appear to be installed, ${sir()}."
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        app.startActivity(i)
        lastDigest = System.currentTimeMillis()
        return "Opening WhatsApp."
    }

    // ---------------------------------------------------------------- helpers

    private fun sir() = com.jarvismini.core.AssistantProfile.addressUser()
    private fun clock(): String = SimpleDateFormat("h:mm a", Locale.US).format(Date())
    private fun hour12(h: Int) = (if (h % 12 == 0) 12 else h % 12).toString() + " AM"
    private fun day() = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    private fun doneToday(k: String) = JarvisPrefs.getString("proactive_$k") == day()
    private fun markToday(k: String) = JarvisPrefs.putString("proactive_$k", day())

    private fun startOfDay(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun messageNamesSince(ts: Long): List<String> =
        EventLog.since(ts).filter { it.type == EventType.MESSAGE }
            .map { it.title.removePrefix("Message from ").trim() }.filter { it.isNotEmpty() }.distinct()

    private class Batt(val pct: Int, val charging: Boolean) {
        val text get() = "Battery $pct percent${if (charging) ", charging" else ""}."
    }

    private fun battery(): Batt {
        val i = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val st = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
        return Batt(if (level >= 0) level * 100 / scale else 50, charging)
    }
}
