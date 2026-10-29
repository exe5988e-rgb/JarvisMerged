package com.jarvismini.convo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Handles the notification's inline Reply and Mark-as-read actions. Not exported. */
class ConvoReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val agent = intent.getStringExtra(ConvoNotifier.EXTRA_AGENT) ?: return
        when (intent.action) {
            ConvoNotifier.ACTION_MARK_READ -> ConvoNotifier.markRead(context, agent)
            ConvoNotifier.ACTION_REPLY -> reply(context, intent, agent)
        }
    }

    private fun reply(context: Context, intent: Intent, agent: String) {
        val text = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(ConvoNotifier.KEY_REPLY)
            ?.toString()
            ?.trim()
            .orEmpty()
        val app = context.applicationContext
        if (text.isEmpty()) {
            // Nothing typed: just stop the reply spinner by reposting the conversation as-is.
            ConvoNotifier.onReplyFailed(app, agent, "empty reply")
            return
        }
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val settings = EncryptedConvoSettings(app)
                val repo = HttpConvoRepository(baseUrl = { settings.baseUrl }, token = { settings.token })
                val result = repo.broadcast(text, listOf(agent))
                if (result.isSuccess) {
                    ConvoNotifier.onReplySent(app, agent, text)
                } else {
                    ConvoNotifier.onReplyFailed(app, agent, result.exceptionOrNull()?.message)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
