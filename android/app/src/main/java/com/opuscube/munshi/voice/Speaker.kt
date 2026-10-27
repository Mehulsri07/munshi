package com.opuscube.munshi.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID

interface Speaker {
    /** Speaks after anything already queued; returns when done or stopped. */
    suspend fun say(line: Line)
    /** Stops the current utterance (barge-in). Queued lines still play. */
    fun stop()
}

// One FIFO queue (Mutex is fair): announcements wait behind a question, never cut it off.
// Devanagari goes to a hi-IN voice; Roman Hinglish to en-IN; no voice at all = captions only.
class AndroidSpeaker(context: Context) : Speaker {
    private val ready = CompletableDeferred<Boolean>()
    private val queue = Mutex()
    @Volatile private var current: CompletableDeferred<Unit>? = null
    private var hindi = false
    private var english = false
    private val hiIN = Locale.forLanguageTag("hi-IN")
    private val enIN = Locale.forLanguageTag("en-IN")
    private lateinit var tts: TextToSpeech

    init {
        tts = TextToSpeech(context.applicationContext) { status -> onInit(status) }
    }

    private fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            hindi = tts.isLanguageAvailable(hiIN) >= TextToSpeech.LANG_AVAILABLE
            english = tts.isLanguageAvailable(enIN) >= TextToSpeech.LANG_AVAILABLE
            tts.setSpeechRate(0.95f)
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { current?.complete(Unit) }
                @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) { current?.complete(Unit) }
                override fun onStop(utteranceId: String?, interrupted: Boolean) { current?.complete(Unit) }
            })
        }
        ready.complete(status == TextToSpeech.SUCCESS)
    }

    override suspend fun say(line: Line) {
        queue.withLock {
            if (!ready.await()) return@withLock
            val (text, locale) = when {
                line.spoken != null && hindi -> line.spoken to hiIN
                english -> line.text to enIN
                hindi -> line.text to hiIN
                else -> return@withLock
            }
            tts.language = locale
            val done = CompletableDeferred<Unit>().also { current = it }
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
            // Some engines never report done; cap by roughly how long the line takes to say.
            withTimeoutOrNull(minOf(15_000L, 2_500L + text.length * 80L)) { done.await() }
        }
    }

    override fun stop() { if (::tts.isInitialized) tts.stop(); current?.complete(Unit) }
}
