package com.jarvismini.core.announce

/**
 * Lets library modules (call handler etc.) ask the user a spoken yes/no question.
 * The app installs the implementation; if it is not installed callers just announce.
 * args: line to speak, dedupe key, action to run on "yes" (returns the spoken confirmation).
 */
object AskHook {
    @Volatile var ask: ((String, String, () -> String) -> Unit)? = null
}
