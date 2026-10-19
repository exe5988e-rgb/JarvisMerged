package com.jarvismini.core

import com.jarvismini.core.announce.SleepSummary
import com.jarvismini.core.learn.HabitMemory

/**
 * Voice / chat commands for personas, lockdown and the digest.
 * Returns the reply to speak, or null if the text is not one of these commands
 * (so the caller continues with normal chat / task routing).
 */
object ModeCommands {

    private val QUESTION_STARTS = listOf("what ", "what's", "how ", "why ", "who ", "explain", "tell me about")
    private val OFF_WORDS = setOf("off", "disable", "deactivate", "end", "stop", "cancel")
    private val NUMBER = Regex("""\+?\d[\d\s-]{6,}""")

    /** Extra command handler installed by the app (media, device control, LLM switch). */
    @Volatile var extra: ((String) -> String?)? = null

    const val VOICE_ENGINE_KEY = "voice_engine"   // auto | elevenlabs | local | system

    private fun voiceLabel(mode: String) = when (mode) {
        "elevenlabs" -> "ElevenLabs"
        "local" -> "local clone"
        "system" -> "system voice"
        else -> "automatic, ElevenLabs with local clone as backup"
    }

    /** "use local voice", "switch to elevenlabs", "voice auto", "which voice are you using". */
    private fun voiceEngineCommand(t: String): String? {
        if (!t.contains("voice") && !t.contains("eleven")) return null
        val current = JarvisPrefs.getString(VOICE_ENGINE_KEY) ?: "auto"
        if (t.contains("which voice") || t.contains("what voice") || t.contains("voice status") || t.contains("current voice"))
            return "Voice engine is ${voiceLabel(current)}."
        val words = t.split(Regex("[^a-z]+")).toSet()
        val swap = t.startsWith("voice ") ||
            words.any { it in setOf("use", "switch", "change", "set", "swap", "go", "back", "enable") }
        if (!swap) return null
        val mode = when {
            t.contains("eleven") || words.any { it in setOf("cloud", "online") } -> "elevenlabs"
            words.any { it in setOf("local", "offline", "clone", "cloned") } -> "local"
            words.any { it in setOf("system", "robot", "robotic", "android") } -> "system"
            words.any { it in setOf("auto", "automatic") } -> "auto"
            else -> return null
        }
        JarvisPrefs.putString(VOICE_ENGINE_KEY, mode)
        return "Voice set to ${voiceLabel(mode)}."
    }

    fun handle(input: String): String? {
        val t = input.trim().lowercase()
        if (t.isEmpty()) return null
        HabitMemory.recordFirstActivityOfDay()
        HabitMemory.recordCommand(t)

        if (t.contains("what have you learned") || t.contains("what do you know about me") || t.contains("my habits") || t.contains("my routine")) {
            val wake = HabitMemory.usualWakeHour()
            val topCmd = HabitMemory.topCommands(3).filter { it.second >= 2 }
            val topApp = HabitMemory.topApps(3).filter { it.second >= 2 }
            val msgHour = HabitMemory.busiestMessageHour()
            val parts = ArrayList<String>()
            if (wake != null) parts.add("you are usually first active around ${if (wake == 0) 12 else if (wake > 12) wake - 12 else wake}${if (wake < 12) " a.m." else " p.m."}")
            if (topApp.isNotEmpty()) parts.add("you open ${topApp.joinToString(" and ") { it.first }} most often")
            if (topCmd.isNotEmpty()) parts.add("your most used command is \"${topCmd.first().first}\"")
            if (msgHour != null) parts.add("messages tend to arrive around ${if (msgHour == 0) 12 else if (msgHour > 12) msgHour - 12 else msgHour}${if (msgHour < 12) " a.m." else " p.m."}")
            return if (parts.isEmpty()) "I am still learning your patterns. Ask me again in a few days."
                   else "So far: " + parts.joinToString(", ") + "."
        }

        if (t.contains("what did i miss") || t.contains("what i missed") || t.contains("any updates")) {
            val s = SleepSummary.build(System.currentTimeMillis() - 12 * 60 * 60_000L)
            return if (s != null) "In the last 12 hours: $s." else "Nothing new to report."
        }

        voiceEngineCommand(t)?.let { return it }

        if (QUESTION_STARTS.any { t.startsWith(it) }) return null

        if (t.contains("whitelist")) {
            val num = NUMBER.find(t)?.value
            // "whitelist" itself contains "list", so strip it before looking for show/list.
            val rest = t.replace("whitelist", " ")
            val wantsShow = rest.contains("show") || rest.contains("list") || rest.contains("read")
            return when {
                num == null && wantsShow -> {
                    val l = LockdownManager.whitelist()
                    if (l.isEmpty()) "The whitelist is empty." else "Whitelisted numbers: ${l.joinToString(", ")}."
                }
                num == null -> "Say whitelist followed by a number to add it."
                rest.contains("remove") || rest.contains("delete") ->
                    if (LockdownManager.removeFromWhitelist(num)) "Removed from the whitelist."
                    else "That number is not on the whitelist."
                else ->
                    if (LockdownManager.addToWhitelist(num)) "Added to the whitelist."
                    else "I could not add that number. The whitelist holds up to ${LockdownManager.MAX_WHITELIST} numbers."
            }
        }

        if (t.contains("lockdown")) {
            val words = t.split(Regex("[^a-z]+")).toSet()
            return if (words.any { it in OFF_WORDS }) {
                LockdownManager.disable(announce = false)
                "Lockdown mode deactivated. Calls are back to normal."
            } else {
                LockdownManager.enable(announce = false)
                if (LockdownManager.whitelist().isEmpty())
                    "Lockdown mode activated. All calls are blocked. The whitelist is empty."
                else
                    "Lockdown mode activated. All calls are blocked except whitelisted contacts."
            }
        }

        extra?.invoke(t)?.let { return it }

        val persona = JarvisPersona.fromPhrase(t) ?: return null
        JarvisPersona.set(persona)
        return when (persona) {
            JarvisPersona.JARVIS -> "Jarvis mode."
            JarvisPersona.SILENT_GUARDIAN -> "Silent Guardian engaged. I will only speak for critical events."
            JarvisPersona.COMBAT -> "Combat mode. High alert."
            JarvisPersona.HUMOR -> "Humor mode on."
            JarvisPersona.STUDY -> "Study focus engaged. Only important alerts will interrupt you."
            JarvisPersona.CALM_WHISPER -> "Calm whisper mode."
        }
    }
}
