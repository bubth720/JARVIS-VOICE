package com.jarvis.voice.fusion02

import java.util.UUID
import java.util.Collections

const val AUDIO_SCHEMA="voice.fusion02.audio/1-draft"
data class AudioKey(val sessionId: String,val requestId: String,val generation: Long) {
    init { require(listOf(sessionId,requestId).all { runCatching { UUID.fromString(it).toString()==it }.getOrDefault(false) });require(generation in 1..9007199254740991L) }
    override fun toString()="AudioKey(generation=$generation)"
}
enum class AudioStage { SpeechQueued, Generating, FirstPCM, AudioTrackWrite, PlayingEstimated, SegmentFinished, SegmentGeneratedOnly, Paused, Finished, RenderedOnly, Cancelled, Failed, NativeUnknown }
enum class ObservationOrigin { JVM_SIMULATION, LINUX_NATIVE_RENDER_ONLY, ANDROID_SYNTHETIC_AUDIO_TRACK }
enum class ProtocolError { CORRELATION, TERMINAL, ORDER, FRAME_LIMIT, INCOMPLETE_AUDIO, CLOCK_REGRESSED, BUSY, REPLAY, CAPACITY, STALE_GENERATION }
class AudioProtocolException(val code: ProtocolError): IllegalStateException(code.name)
internal fun demand(ok: Boolean,code: ProtocolError) { if(!ok) throw AudioProtocolException(code) }
/** IDs are operation correlation, never profile identifiers. No text/PCM/path in the event. */
data class AudioEvent internal constructor(val key: AudioKey,val sequence: Long,val elapsedNs: Long,val stage: AudioStage,
    val segment: Int?,val frames: Int?,val origin: ObservationOrigin,val clockSource: String) {
    fun metadata(): Map<String,Any?> = mapOf("schema" to AUDIO_SCHEMA,"sessionId" to key.sessionId,"requestId" to key.requestId,
        "generation" to key.generation,"sequence" to sequence,"elapsedNs" to elapsedNs,"clock" to clockSource,
        "stage" to stage.name,"segment" to segment,"frames" to frames,"origin" to origin.name,
        "humanAudibilityVerified" to false,"nativeUnloadVerified" to false,"phoneticAlignmentVerified" to false)
    override fun toString()="AudioEvent(sequence=$sequence,stage=$stage,segment=$segment,frames=$frames,origin=$origin)"
}
/** Trusted producer observations, not an IPC decoder or native attestation. No lock spans rendering/audio. */
class AudioTimeline(val key: AudioKey,val segments: Int,val origin: ObservationOrigin,
    private val clock: ()->Long=System::nanoTime,private val clockSource: String="System.nanoTime/process-relative") {
    private val began=clock();private var last=began;private var sequence=0L
    private val events=java.util.ArrayDeque<AudioEvent>()
    @Volatile var dropped=0;private set
    @Volatile var stage: AudioStage?=null;private set
    @Volatile var completed=0;private set
    private var current: Int?=null;private var produced=0;private var submitted=0;private var playing=false
    private val renderedOnly=origin==ObservationOrigin.LINUX_NATIVE_RENDER_ONLY;private var terminal=false
    init { require(segments in 1..4000) }
    private fun correlated(k: AudioKey) { demand(k==key,ProtocolError.CORRELATION) }
    private fun live(k: AudioKey) { correlated(k);demand(!terminal,ProtocolError.TERMINAL) }
    private fun record(s: AudioStage,frames: Int?=null) {
        val now=clock();demand(now>=last && now>=began,ProtocolError.CLOCK_REGRESSED);last=now
        val event=AudioEvent(key,++sequence,now-began,s,current,frames,origin,clockSource);stage=s
        if(events.size==256) { events.removeFirst();dropped++ };events.addLast(event)
        // Pull-only bounded telemetry: no arbitrary callbacks under the state lock.
    }
    @Synchronized fun queued(k: AudioKey=key) { live(k);demand(stage==null,ProtocolError.ORDER);record(AudioStage.SpeechQueued) }
    @Synchronized fun generating(k: AudioKey,index: Int) {
        live(k);demand(current==null && index==completed && index<segments && stage in setOf(AudioStage.SpeechQueued,AudioStage.SegmentFinished,AudioStage.SegmentGeneratedOnly,AudioStage.Paused),ProtocolError.ORDER)
        current=index;produced=0;submitted=0;playing=false;record(AudioStage.Generating)
    }
    @Synchronized fun pcm(k: AudioKey,index: Int,count: Int) {
        live(k);demand(current==index && stage in setOf(AudioStage.Generating,AudioStage.FirstPCM),ProtocolError.ORDER)
        demand(count>0 && produced.toLong()+count<=480000,ProtocolError.FRAME_LIMIT)
        produced+=count;if(stage==AudioStage.Generating) record(AudioStage.FirstPCM,produced)
    }
    @Synchronized fun write(k: AudioKey,index: Int,frames: Int) {
        live(k);demand(!renderedOnly && current==index && stage==AudioStage.FirstPCM,ProtocolError.ORDER)
        demand(frames==produced && frames>0,ProtocolError.INCOMPLETE_AUDIO);submitted=frames;record(AudioStage.AudioTrackWrite,frames)
    }
    @Synchronized fun playing(k: AudioKey,index: Int) {
        live(k);demand(current==index && stage==AudioStage.AudioTrackWrite,ProtocolError.ORDER)
        playing=true;record(AudioStage.PlayingEstimated) // Device presentation estimate, never a human-hearing timestamp.
    }
    @Synchronized fun segmentFinished(k: AudioKey,index: Int,presentedFrames: Int,eos: Boolean) {
        live(k);demand(current==index && playing && !renderedOnly,ProtocolError.ORDER)
        demand(eos && produced==submitted && presentedFrames==submitted && submitted>0,ProtocolError.INCOMPLETE_AUDIO)
        record(AudioStage.SegmentFinished,presentedFrames);completed++;current=null
    }
    @Synchronized fun segmentRenderedOnly(k: AudioKey,index: Int,eos: Boolean) {
        live(k);demand(renderedOnly && current==index && stage==AudioStage.FirstPCM && submitted==0,ProtocolError.ORDER)
        demand(eos && produced>0,ProtocolError.INCOMPLETE_AUDIO)
        record(AudioStage.SegmentGeneratedOnly,produced);completed++;current=null
    }
    @Synchronized fun pause(k: AudioKey=key) { live(k);demand(current==null && completed<segments && stage in setOf(AudioStage.SegmentFinished,AudioStage.SegmentGeneratedOnly),ProtocolError.ORDER);record(AudioStage.Paused) }
    @Synchronized fun finish(k: AudioKey=key) {
        live(k);demand(current==null && completed==segments,ProtocolError.INCOMPLETE_AUDIO)
        record(if(renderedOnly) AudioStage.RenderedOnly else AudioStage.Finished);terminal=true
    }
    @Synchronized fun stop(k: AudioKey=key): Boolean {
        correlated(k);if(terminal) return false;record(AudioStage.Cancelled);terminal=true;return true
    }
    @Synchronized fun fail(k: AudioKey=key) { correlated(k);if(!terminal) { record(AudioStage.Failed);terminal=true } }
    @Synchronized fun nativeUnknown(k: AudioKey=key) {
        correlated(k);demand(stage!=AudioStage.Finished && stage!=AudioStage.RenderedOnly && stage!=AudioStage.NativeUnknown,ProtocolError.TERMINAL)
        record(AudioStage.NativeUnknown);terminal=true
    }
    @Synchronized fun snapshot(): List<AudioEvent> = Collections.unmodifiableList(events.toList())
}
