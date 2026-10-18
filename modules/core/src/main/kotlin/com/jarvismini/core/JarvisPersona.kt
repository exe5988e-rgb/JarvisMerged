package com.jarvismini.core

/**
 * Persona = HOW Jarvis behaves (style, how chatty it is).
 * JarvisMode = WHAT situation the user is in (work, sleep, driving...).
 * They are kept separate so adding a persona never breaks the exhaustive
 * `when (JarvisMode)` blocks elsewhere in the repo.
 */
enum class JarvisPersona(val label: String, private val aliases: List<String>) {
    JARVIS("Jarvis Mode", listOf("jarvis mode", "normal persona")),
    SILENT_GUARDIAN("Silent Guardian", listOf("silent guardian", "guardian mode")),
    COMBAT("Combat Mode", listOf("combat mode", "high alert")),
    HUMOR("Humor Mode", listOf("humor mode", "humour mode", "joke mode")),
    STUDY("Study Focus Mode", listOf("study focus", "study mode")),
    CALM_WHISPER("Calm Whisper Mode", listOf("calm whisper", "whisper mode"));

    companion object {
        private const val KEY = "persona"

        fun current(): JarvisPersona {
            val saved = JarvisPrefs.getString(KEY, JARVIS.name) ?: JARVIS.name
            return values().firstOrNull { it.name == saved } ?: JARVIS
        }

        fun set(persona: JarvisPersona) {
            JarvisPrefs.putString(KEY, persona.name)
        }

        fun fromPhrase(text: String): JarvisPersona? {
            val t = text.lowercase()
            return values().firstOrNull { p -> p.aliases.any { t.contains(it) } }
        }
    }
}
