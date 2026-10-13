package com.jarvis.voice.speech

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

class AndroidSpeechEngine(context: Context, private val onState: (SpeechState) -> Unit) : SpeechEngine {
    private val handler = Handler(Looper.getMainLooper())
    private var state = SpeechState.INITIALIZING
    private var activeId: String? = null
    private var closed = false
    private val tts = TextToSpeech(context.applicationContext) { status ->
        // Posting also ensures the tts field is assigned before accessing it.
        handler.post { initialize(status) }
    }
    override val maxTextLength: Int get() = TextToSpeech.getMaxSpeechInputLength()

    private fun initialize(status: Int) {
        if (closed) return
        if (status != TextToSpeech.SUCCESS) { update(SpeechState.ERROR); return }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = event(utteranceId, SpeechState.SPEAKING)
            override fun onDone(utteranceId: String?) = event(utteranceId, SpeechState.READY)
            @Deprecated("Android callback")
            override fun onError(utteranceId: String?) = event(utteranceId, SpeechState.ERROR)
            override fun onError(utteranceId: String?, errorCode: Int) = event(utteranceId, SpeechState.ERROR)
        })
        val language = tts.setLanguage(Locale.getDefault())
        update(if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED)
            SpeechState.ERROR else SpeechState.READY)
    }

    private fun event(id: String?, next: SpeechState) {
        handler.post {
            if (!closed && id != null && id == activeId) {
                if (next != SpeechState.SPEAKING) activeId = null
                update(next)
            }
        }
    }
    private fun update(next: SpeechState) { state = next; onState(next) }

    override fun speak(request: SpeechRequest): SpeechResult {
        val text = request.text.trim()
        if (text.isEmpty() || text.length > maxTextLength) return SpeechResult.InvalidText
        if (closed || state == SpeechState.ERROR) return SpeechResult.Unavailable
        if (state == SpeechState.INITIALIZING) return SpeechResult.NotReady
        val id = UUID.randomUUID().toString()
        activeId = id
        val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        return if (result == TextToSpeech.SUCCESS) {
            update(SpeechState.SPEAKING)
            SpeechResult.Accepted
        } else {
            activeId = null
            update(SpeechState.ERROR)
            SpeechResult.Unavailable
        }
    }
    override fun stop() {
        activeId = null
        tts.stop()
        if (!closed && state == SpeechState.SPEAKING) update(SpeechState.READY)
    }
    override fun close() {
        if (closed) return
        closed = true
        activeId = null
        handler.removeCallbacksAndMessages(null)
        tts.stop()
        tts.shutdown()
        state = SpeechState.CLOSED
    }
}
