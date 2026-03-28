package com.jarvismini.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

/**
 * VoiceTriggerManager — OPT 13: Voice Trigger (Speech-to-Text input)
 *
 * Wraps Android SpeechRecognizer lifecycle. Call from a single composable
 * or ViewModel; must be created on the main thread.
 *
 * Usage:
 *   val vtm = remember { VoiceTriggerManager(context) }
 *   DisposableEffect(Unit) { onDispose { vtm.destroy() } }
 *   vtm.start(onResult = { task -> ... })
 *   vtm.stop()
 */
class VoiceTriggerManager(private val context: Context) {

    companion object {
        private const val TAG = "VoiceTriggerManager"
    }

    private var recognizer: SpeechRecognizer? = null

    private val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

    /**
     * Start listening.
     * @param onResult   called with the recognised text (trimmed, non-blank)
     * @param onError    called with a human-readable error string
     * @param onReady    called when mic is open and recording has begun
     */
    fun start(
        onResult: (String) -> Unit,
        onError:  (String) -> Unit = {},
        onReady:  ()       -> Unit = {},
    ) {
        destroyRecognizer()

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech recognition not available on this device")
            return
        }

        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "Ready for speech")
                    onReady()
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.trim() ?: ""
                    Log.d(TAG, "Result: $text")
                    if (text.isNotBlank()) onResult(text)
                }
                override fun onError(error: Int) {
                    val msg = errorMessage(error)
                    Log.w(TAG, "STT error $error: $msg")
                    onError(msg)
                }
                override fun onBeginningOfSpeech()           {}
                override fun onEndOfSpeech()                 {}
                override fun onRmsChanged(rmsdB: Float)      {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onPartialResults(partial: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            startListening(intent)
        }
    }

    fun stop() {
        recognizer?.stopListening()
    }

    fun destroy() {
        destroyRecognizer()
    }

    private fun destroyRecognizer() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun errorMessage(code: Int) = when (code) {
        SpeechRecognizer.ERROR_AUDIO                 -> "Audio recording error"
        SpeechRecognizer.ERROR_CLIENT                -> "Client-side error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Missing RECORD_AUDIO permission"
        SpeechRecognizer.ERROR_NETWORK               -> "Network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT       -> "Network timeout"
        SpeechRecognizer.ERROR_NO_MATCH              -> "No speech recognised — try again"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY       -> "Recogniser busy — try again"
        SpeechRecognizer.ERROR_SERVER                -> "Server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT        -> "No speech detected"
        else                                         -> "Unknown error ($code)"
    }
}
