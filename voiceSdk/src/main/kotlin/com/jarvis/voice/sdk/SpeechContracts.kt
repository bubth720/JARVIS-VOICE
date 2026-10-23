package com.jarvis.voice.sdk

import com.jarvis.voice.expression.VoiceExpressionPlan
import com.jarvis.voice.pocket.CancellationToken
import java.util.UUID

enum class SpeechState { IDLE, PREPARING, PLAYING, STOPPING, RELEASED }
enum class SpeechRefusal { BUSY, RELEASED, DUPLICATE_REQUEST, CANCELLED, INVALID_REQUEST, MODEL_UNAVAILABLE, PROFILE_UNAVAILABLE,
    NON_VERIFIED, AUDIO_UNAVAILABLE, ENGINE_FAILED, LIMIT_EXCEEDED }
class SpeechRejected(val reason: SpeechRefusal) : IllegalStateException(reason.name)

/** Caller supplies complete, explicit utterances; no punctuation splitting or hidden rewrite. */
data class SpeechRequest(val requestId: String, val plan: VoiceExpressionPlan) {
    init {
        require(UUID.fromString(requestId).toString() == requestId)
        require(plan.request.identityId == plan.binding.identityId) { "Identité de demande et profil incompatibles" }
    }
    override fun toString() = "SpeechRequest(requestId=$requestId)"
}

class SpeechQueue(requests: List<SpeechRequest>) {
    val requests: List<SpeechRequest> = java.util.Collections.unmodifiableList(requests.toList())
    init {
        require(this.requests.size in 1..3)
        require(this.requests.map { it.requestId }.toSet().size == this.requests.size)
        val first = this.requests.first().plan.binding
        require(this.requests.all { it.plan.binding == first }) { "Une file conserve le même profil exact" }
    }
}

data class RenderedSpeech(val pcm: FloatArray, val eosReached: Boolean) {
    override fun toString() = "RenderedSpeech(samples=${pcm.size}, eosReached=$eosReached)"
}
fun interface LocalSpeechEnginePort {
    /** Synchronous: returns only after the existing backend call and its cleanup return. */
    fun render(request: SpeechRequest, cancel: CancellationToken): RenderedSpeech
}
data class AudioReceipt(val presentedSamples: Int, val underruns: Int = 0) {
    init { require(presentedSamples >= 0 && underruns >= 0) }
}
interface OutputAudioPort : AutoCloseable {
    val focusController: AudioFocusController
    /** Output owns audio focus. Complete delayed PCM only; never asserts human audibility. */
    fun play(pcm: FloatArray, cancel: CancellationToken): AudioReceipt
    fun requestStop()
}

/** Observation of the output's real focus callbacks; this is not a second focus owner. */
class AudioFocusController {
    enum class State { UNKNOWN, GRANTED, LOST }
    @Volatile var state = State.UNKNOWN; private set
    @Synchronized fun granted() { if (state != State.LOST) state = State.GRANTED }
    @Synchronized fun lost() { state = State.LOST }
    @Synchronized fun check() { if (state == State.LOST) throw SpeechRejected(SpeechRefusal.AUDIO_UNAVAILABLE) }
    @Synchronized fun begin() { check(); state = State.UNKNOWN }
}

data class PlaybackMetrics(val completedUtterances: Int, val generatedSamples: Int,
    val presentedSamples: Int, val peakQueuedSamples: Int, val underruns: Int, val elapsedMs: Long)
sealed interface SpeechOutcome {
    data class Completed(val metrics: PlaybackMetrics) : SpeechOutcome
    data class Refused(val reason: SpeechRefusal) : SpeechOutcome
}

/** No fallback is selected implicitly, and a private neural identity is never substituted. */
object AndroidTtsFallbackGate {
    fun check(explicitlySelected: Boolean, installedVoice: Boolean, networkRequired: Boolean?): SpeechRefusal? =
        if (explicitlySelected && installedVoice && networkRequired == false) null else SpeechRefusal.NON_VERIFIED
}
