package com.jarvis.voice.bridgeclient
import java.util.UUID

/** In-memory proposal only. No serializer, Binder transaction, permission or native authority. */
const val PUBLIC_LAB_V2="voice.speech.public-lab/2-draft"
enum class PublicUtterance(val text: String) { HELLO("Bonjour, ceci est un test public."), TRAIN("Demain, le train arrivera à Paris.") }
enum class LabSpeechStyle { NEUTRAL, SOFTWARE_EXPERIMENT_UNAPPROVED }
data class SpeechCorrelation(val requestId: String,val sessionId: String,val generation: Long) {
    init { require(listOf(requestId,sessionId).all { runCatching { UUID.fromString(it).toString()==it }.getOrDefault(false) });require(generation in 1..9007199254740991L) }
    override fun toString()="SpeechCorrelation(generation=$generation)"
}
data class PublicSpeechJobRequestV2(val key: SpeechCorrelation,val fixture: PublicUtterance,
    val style: LabSpeechStyle=LabSpeechStyle.NEUTRAL,val version: String=PUBLIC_LAB_V2) {
    init { require(version==PUBLIC_LAB_V2 && fixture.text.length<=300 && fixture.text.toByteArray(Charsets.UTF_8).size<=1200) }
    override fun toString()="PublicSpeechJobRequestV2(fixture=$fixture,generation=${key.generation})"
}
enum class SpeechConnectionMode { TEXT_ONLY, TTS_LOCAL_AVAILABLE, SPEECH_BLOCKED, UNKNOWN_NATIVE }
enum class LogicalSpeechPhase { REFUSED, CANCELLED_LOGICAL_ONLY }
enum class NativeSpeechObservation { UNKNOWN }
enum class SpeechBlockCode { NATIVE_AUTHORITY_UNAVAILABLE, STYLE_UNSUPPORTED }
data class SpeechProgressEnvelope(val key: SpeechCorrelation,val phase: LogicalSpeechPhase,val code: SpeechBlockCode,
    val mode: SpeechConnectionMode=SpeechConnectionMode.SPEECH_BLOCKED,val logicalJobReleased: Boolean=true,
    val nativeObservation: NativeSpeechObservation=NativeSpeechObservation.UNKNOWN,val relativePcmCues: List<Int> = emptyList()) {
    init { require(mode==SpeechConnectionMode.SPEECH_BLOCKED && logicalJobReleased && relativePcmCues.isEmpty()) }
    override fun toString()="SpeechProgressEnvelope(phase=$phase,code=$code,native=$nativeObservation)"
}
fun interface PublicSpeechTransport { fun submit(request: PublicSpeechJobRequestV2): SpeechProgressEnvelope }
