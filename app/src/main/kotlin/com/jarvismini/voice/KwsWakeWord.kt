package com.jarvismini.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig

/**
 * Offline wake word ("Jarvis" / "Hey Jarvis") using sherpa-onnx keyword spotting.
 * No access key, no activation limit. Model + keywords live in assets/kws/.
 *
 * start() opens the mic (16 kHz mono PCM16) on a background thread and calls onDetected()
 * when a keyword fires. stop() joins the thread and releases the mic so another recognizer can use it.
 */
class KwsWakeWord(private val context: Context, private val onDetected: () -> Unit) {

    private companion object {
        const val TAG = "KwsWakeWord"
        const val DIR = "kws"
        const val SAMPLE_RATE = 16000
    }

    private var spotter: KeywordSpotter? = null
    @Volatile private var running = false
    private var thread: Thread? = null

    /** Loads the model once; returns false if it can't be loaded. */
    private fun ensureSpotter(): Boolean {
        if (spotter != null) return true
        return try {
            val transducer = OnlineTransducerModelConfig().apply {
                encoder = "$DIR/encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx"
                decoder = "$DIR/decoder-epoch-12-avg-2-chunk-16-left-64.onnx"
                joiner = "$DIR/joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx"
            }
            val model = OnlineModelConfig().apply {
                this.transducer = transducer
                tokens = "$DIR/tokens.txt"
                numThreads = 2
                provider = "cpu"
                modelType = "zipformer2"
            }
            val cfg = KeywordSpotterConfig().apply {
                featConfig = FeatureConfig().apply { sampleRate = SAMPLE_RATE; featureDim = 80 }
                modelConfig = model
                keywordsFile = "$DIR/keywords.txt"
                keywordsScore = 1.5f
                keywordsThreshold = 0.25f
            }
            spotter = KeywordSpotter(context.assets, cfg)
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load KWS model: ${e.message}", e)
            false
        }
    }

    @Synchronized
    fun start(): Boolean {
        if (running) return true
        if (!ensureSpotter()) return false
        running = true
        thread = Thread({ loop() }, "kws-wakeword").also { it.start() }
        return true
    }

    @Synchronized
    fun stop() {
        running = false
        try { thread?.join(800) } catch (_: InterruptedException) {}
        thread = null
    }

    /** Stops and frees the model. */
    fun delete() {
        stop()
        try { spotter?.release() } catch (_: Throwable) {}
        spotter = null
    }

    private fun loop() {
        val sp = spotter ?: return
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val chunk = SAMPLE_RATE / 10  // 100 ms
        var rec: AudioRecord? = null
        val stream = sp.createStream("")
        try {
            rec = AudioRecord(
                MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, chunk * 2 * 4)
            )
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord not initialised (mic busy or permission missing)")
                return
            }
            rec.startRecording()
            Log.i(TAG, "Wake word listening")
            val shorts = ShortArray(chunk)
            val floats = FloatArray(chunk)
            while (running) {
                val n = rec.read(shorts, 0, shorts.size)
                if (n <= 0) continue
                for (i in 0 until n) floats[i] = shorts[i] / 32768f
                stream.acceptWaveform(if (n == floats.size) floats else floats.copyOf(n), SAMPLE_RATE)
                while (sp.isReady(stream)) {
                    sp.decode(stream)
                    val kw = sp.getResult(stream).keyword
                    if (kw.isNotBlank()) {
                        Log.i(TAG, "Keyword detected: $kw")
                        sp.reset(stream)
                        running = false
                        onDetected()
                        return
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Wake word loop failed: ${e.message}", e)
        } finally {
            try { rec?.stop() } catch (_: Throwable) {}
            try { rec?.release() } catch (_: Throwable) {}
            try { stream.release() } catch (_: Throwable) {}
        }
    }
}
