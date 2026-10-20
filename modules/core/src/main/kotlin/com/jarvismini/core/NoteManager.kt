package com.jarvismini.core

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Voice notes: "take a note ...", "read my notes", "clear my notes". Stored on-device in JarvisPrefs. */
object NoteManager {
    private const val KEY = "notes_json"
    private const val MAX = 200
    private const val AWAIT_MS = 60_000L
    @Volatile private var awaitingUntil = 0L

    private val ADD = Regex("""^(?:please )?(?:take|make|add|save|create|write)(?: me)?(?: a| another)? note(?: that| of| to)?[,:]?\s*(.*)$""")
    private val ADD2 = Regex("""^(?:note that|note down|write down|jot down|remember that|make a note that)[,:]?\s*(.+)$""")

    fun handle(raw: String): String? {
        val t = raw.trim().lowercase().trimEnd('.', '!', '?')
        if (t.isEmpty()) return null

        if (System.currentTimeMillis() < awaitingUntil) {
            awaitingUntil = 0L
            if (t in setOf("cancel", "never mind", "nevermind", "forget it")) return "Very well, Sir. No note taken."
            return save(raw.trim())
        }
        if (t.contains("clear my notes") || t.contains("delete all notes") || t.contains("delete my notes") || t.contains("clear all notes")) {
            JarvisPrefs.putString(KEY, "[]")
            return "All notes cleared, Sir."
        }
        if (t.contains("read my notes") || t.contains("read notes") || t.contains("my notes") ||
            t.contains("show notes") || t.contains("list notes") || t.contains("what notes")) {
            return read()
        }
        ADD2.find(t)?.let { return save(raw.trim().substring(raw.trim().length - it.groupValues[1].length)) }
        ADD.find(t)?.let { m ->
            val body = m.groupValues[1].trim()
            if (body.isEmpty()) {
                awaitingUntil = System.currentTimeMillis() + AWAIT_MS
                return "Go ahead, Sir. What shall I note down?"
            }
            return save(raw.trim().substring(raw.trim().length - body.length))
        }
        return null
    }

    private fun load(): JSONArray = try { JSONArray(JarvisPrefs.getString(KEY) ?: "[]") } catch (_: Exception) { JSONArray() }

    private fun save(text: String): String {
        val clean = text.trim().trimEnd('.').ifBlank { return "I did not catch the note, Sir." }
        val arr = load()
        arr.put(JSONObject().put("t", System.currentTimeMillis()).put("text", clean))
        val out = JSONArray()
        for (i in maxOf(0, arr.length() - MAX) until arr.length()) out.put(arr.get(i))
        JarvisPrefs.putString(KEY, out.toString())
        return "Noted, Sir: $clean."
    }

    private fun read(): String {
        val arr = load()
        if (arr.length() == 0) return "You have no notes, Sir."
        val fmt = SimpleDateFormat("EEE h:mm a", Locale.getDefault())
        val take = minOf(5, arr.length())
        val parts = ArrayList<String>()
        for (i in arr.length() - 1 downTo arr.length() - take) {
            val o = arr.getJSONObject(i)
            parts.add("${fmt.format(Date(o.getLong("t")))}: ${o.getString("text")}")
        }
        val more = if (arr.length() > take) " Plus ${arr.length() - take} older." else ""
        return "You have ${arr.length()} note${if (arr.length() == 1) "" else "s"}. Latest first. " + parts.joinToString(". ") + "." + more
    }
}
