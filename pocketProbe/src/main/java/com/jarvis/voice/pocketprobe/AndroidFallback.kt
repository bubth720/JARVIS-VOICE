package com.jarvis.voice.pocketprobe

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

/** Explicit fallback selection; never invoked automatically on a Pocket failure. */
class AndroidFallback(context: Context, private val state: (Boolean, String) -> Unit, private val done: () -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var closed = false
    private var activeId: String? = null
    var ready = false
        private set
    init {
        val packages = context.packageManager.queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0)
            .map { it.serviceInfo.packageName }.distinct().filterNot { it.contains("pocket", ignoreCase = true) }
        val preferred = Settings.Secure.getString(context.contentResolver, "tts_default_synth")
        val chosen = preferred?.takeIf { it in packages } ?: packages.firstOrNull()
        if (chosen == null) handler.post { state(false, "Aucun moteur Android de secours détecté") }
        else tts = TextToSpeech(context.applicationContext, { status -> handler.post {
            if (closed) return@post
            val engine = tts ?: return@post
            if (status != TextToSpeech.SUCCESS) { state(false, "Initialisation Android TTS impossible"); return@post }
            engine.language = Locale.FRENCH
            val voice = engine.voices.orEmpty().filter { it.locale.language == "fr" && !it.isNetworkConnectionRequired }
                .sortedBy { if (it.locale.country == "FR") 0 else 1 }.firstOrNull()
            ready = voice != null && engine.setVoice(voice) == TextToSpeech.SUCCESS
            state(ready, if (ready) "Secours : $chosen, voix française hors ligne" else "Installer une voix française hors ligne pour $chosen")
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) = Unit
                override fun onDone(id: String?) = complete(id)
                @Deprecated("Android callback") override fun onError(id: String?) = complete(id)
                override fun onError(id: String?, code: Int) {
                    handler.post { if (!closed && id == activeId) state(ready, "Erreur Android TTS : $code") }; complete(id)
                }
            })
        } }, chosen)
    }
    private fun complete(id: String?) { handler.post { if (!closed && id != null && id == activeId) { activeId = null; done() } } }
    fun speak(text: String): Boolean {
        if (!ready || closed) return false
        activeId = UUID.randomUUID().toString()
        if (tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, activeId) != TextToSpeech.SUCCESS) { activeId = null; return false }
        return true
    }
    fun stop() { activeId = null; tts?.stop() }
    fun close() { closed = true; stop(); tts?.shutdown(); tts = null; handler.removeCallbacksAndMessages(null) }
}
