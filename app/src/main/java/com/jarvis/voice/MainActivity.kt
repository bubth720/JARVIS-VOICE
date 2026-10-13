package com.jarvis.voice

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.jarvis.voice.speech.*

class MainActivity : Activity() {
    private lateinit var engine: AndroidSpeechEngine
    private lateinit var router: SpeechRouter
    private lateinit var text: EditText
    private lateinit var status: TextView
    private lateinit var speak: Button
    private lateinit var stop: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
            // Respect system bars when targeting Android 15 edge-to-edge.
            setOnApplyWindowInsetsListener { view, insets ->
                view.setPadding(padding + insets.systemWindowInsetLeft,
                    padding + insets.systemWindowInsetTop,
                    padding + insets.systemWindowInsetRight,
                    padding + insets.systemWindowInsetBottom)
                insets
            }
        }
        layout.addView(TextView(this).apply { setText(R.string.app_name); textSize = 26f })
        text = EditText(this).apply {
            id = R.id.speech_text
            setHint(R.string.text_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            gravity = android.view.Gravity.TOP
            minLines = 4
        }
        layout.addView(text, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        status = TextView(this).apply { setText(R.string.initializing); accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE }
        layout.addView(status)
        speak = Button(this).apply { setText(R.string.speak); isEnabled = false }
        stop = Button(this).apply { setText(R.string.stop); isEnabled = false }
        layout.addView(speak)
        layout.addView(stop)
        layout.addView(TextView(this).apply { setText(R.string.voice_help) })
        layout.addView(Button(this).apply {
            setText(R.string.install_voice)
            setOnClickListener {
                try { startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)) }
                catch (_: ActivityNotFoundException) { status.setText(R.string.no_installer) }
            }
        })
        setContentView(layout)
        engine = AndroidSpeechEngine(this, ::renderState)
        router = SpeechRouter(engine)
        speak.setOnClickListener {
            when (router.speak(SpeechRequest(text.text.toString()))) {
                SpeechResult.InvalidText -> text.error = getString(R.string.text_error, engine.maxTextLength)
                SpeechResult.NotReady -> status.setText(R.string.initializing)
                SpeechResult.Unavailable -> status.setText(R.string.tts_error)
                SpeechResult.Accepted -> text.error = null
            }
        }
        stop.setOnClickListener { router.stop() }
    }
    private fun renderState(state: SpeechState) {
        speak.isEnabled = state == SpeechState.READY || state == SpeechState.SPEAKING
        stop.isEnabled = state == SpeechState.SPEAKING
        status.setText(when (state) {
            SpeechState.INITIALIZING -> R.string.initializing
            SpeechState.READY -> R.string.ready
            SpeechState.SPEAKING -> R.string.speaking
            else -> R.string.tts_error
        })
    }
    override fun onStop() { if (::router.isInitialized) router.stop(); super.onStop() }
    override fun onDestroy() { if (::router.isInitialized) router.close(); super.onDestroy() }
}
