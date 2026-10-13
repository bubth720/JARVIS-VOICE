package com.jarvis.voice.speech

/** Explicit policy, without Android or networking dependencies.
 * Cloud implementations must define authentication, authorized voice IDs,
 * cancellation and data retention before being connected here.
 */
enum class SpeechMode { LOCAL, CLOUD, HYBRID }

class SpeechRouter(
    private val local: SpeechEngine,
    private val cloud: SpeechEngine? = null,
    private val online: () -> Boolean = { false },
) {
    fun speak(request: SpeechRequest, mode: SpeechMode = SpeechMode.LOCAL): SpeechResult {
        if (request.text.isBlank()) return SpeechResult.InvalidText
        return when (mode) {
            SpeechMode.LOCAL -> local.speak(request)
            SpeechMode.CLOUD -> if (online()) cloud?.speak(request) ?: SpeechResult.Unavailable
                else SpeechResult.Unavailable
            SpeechMode.HYBRID -> {
                val result = if (online()) cloud?.speak(request) else null
                when (result) {
                    SpeechResult.Accepted, SpeechResult.InvalidText -> result
                    else -> local.speak(request)
                }
            }
        }
    }

    fun stop() { local.stop(); cloud?.stop() }
    fun close() { local.close(); cloud?.close() }
}
