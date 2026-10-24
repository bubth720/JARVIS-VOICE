package com.jarvis.voice.final01

const val VOICE_EVENT_SCHEMA="voice.final01.events/1"
interface LocalVoiceControl { fun pauseAfterSegment();fun stop() }
enum class VoiceEventType { START, FIRST_AUDIO, PROGRESS, PAUSE, COMPLETE, CANCELLED, ERROR, UNKNOWN }
/** No text, voice identifiers, PCM, wall clock or phoneme alignment in the SDK event. */
data class VoiceEvent(val type: VoiceEventType,val completed: Int,val total: Int,val segment: Int?,val presentedSamples: Long?=null,val skipped: Int=0) {
    init { require(total in 1..4000 && completed in 0..total && skipped in 0..total && completed+skipped<=total && (segment==null || segment in 0 until total));require(presentedSamples==null || presentedSamples>0);require(type!=VoiceEventType.COMPLETE || completed+skipped==total && segment==null) }
}

fun VoiceEvent.metadata(): Map<String,Any?> = mapOf("schema" to VOICE_EVENT_SCHEMA,"type" to type.name,
    "completed" to completed,"total" to total,"segment" to segment,"presentedSamples" to presentedSamples,"skipped" to skipped)
