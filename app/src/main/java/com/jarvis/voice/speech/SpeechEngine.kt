package com.jarvis.voice.speech

/** Engine boundary for Android TTS, a future local model, or a cloud provider.
 * Implementations must report events on the main thread and release resources in close().
 */
interface SpeechEngine {
    val maxTextLength: Int
    fun speak(request: SpeechRequest): SpeechResult
    fun stop()
    fun close()
}

data class SpeechRequest(val text: String)
sealed interface SpeechResult {
    data object Accepted : SpeechResult
    data object NotReady : SpeechResult
    data object InvalidText : SpeechResult
    data object Unavailable : SpeechResult
}

enum class SpeechState { INITIALIZING, READY, SPEAKING, ERROR, CLOSED }
