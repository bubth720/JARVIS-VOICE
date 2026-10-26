package com.jarvis.voice.fusion02

import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

/** This is a local, model-free laboratory port. It grants no CORE/native/Binder authority. */
class StopSignal {
    private val stopped=AtomicBoolean()
    fun stop() { stopped.set(true) }
    fun check() { if(stopped.get()) throw CancellationException("LOCAL_STOP") }
}
data class RenderedSegment(val pcm: FloatArray,val eos: Boolean)
fun interface SegmentRenderer { fun render(index: Int,stop: StopSignal,onPcm: (Int)->Unit): RenderedSegment }
fun interface SegmentPlayback {
    /** Callbacks report actual completed write and estimated presentation, not scheduled intent. */
    fun play(pcm: FloatArray,stop: StopSignal,onWrite: (Int)->Unit,onPlaying: ()->Unit): PresentedSegment
}
data class PresentedSegment(val frames: Int,val completed: Boolean)

/** Bounded tombstones are never evicted: capacity exhaustion fails closed until a NEW lab instance. */
class LocalProxy(private val capacity: Int=128) {
    init { require(capacity in 1..1024) }
    private val seen=mutableSetOf<Pair<String,String>>()
    private val generations=mutableMapOf<String,Long>()
    private var active: Job?=null
    private var quarantined=false
    class Job internal constructor(val timeline: AudioTimeline,internal val stop: StopSignal) {
        internal val running=AtomicBoolean()
    }
    @Synchronized fun prepare(key: AudioKey,segments: Int,origin: ObservationOrigin): Job {
        demand(!quarantined,ProtocolError.TERMINAL)
        demand(active==null,ProtocolError.BUSY)
        demand((key.sessionId to key.requestId) !in seen,ProtocolError.REPLAY)
        demand(key.generation>(generations[key.sessionId] ?: 0),ProtocolError.STALE_GENERATION)
        demand(seen.size<capacity,ProtocolError.CAPACITY)
        val job=Job(AudioTimeline(key,segments,origin),StopSignal())
        job.timeline.queued();seen.add(key.sessionId to key.requestId);generations[key.sessionId]=key.generation;active=job
        return job
    }
    @Synchronized fun stop(key: AudioKey): Boolean {
        val job=active ?: return false
        demand(key==job.timeline.key,ProtocolError.CORRELATION)
        job.stop.stop();return job.timeline.stop(key)
        // Stop never releases admission. The worker must return; UNKNOWN quarantines this instance.
    }
    @Synchronized fun nativeUnknown(key: AudioKey) {
        val job=active ?: throw AudioProtocolException(ProtocolError.CORRELATION)
        demand(key==job.timeline.key,ProtocolError.CORRELATION)
        job.stop.stop();job.timeline.nativeUnknown(key);quarantined=true
    }
    fun execute(job: Job,renderer: SegmentRenderer,playback: SegmentPlayback?=null) {
        synchronized(this) { demand(active===job,ProtocolError.CORRELATION);demand(job.running.compareAndSet(false,true),ProtocolError.REPLAY) }
        val t=job.timeline;val key=t.key
        try {
            demand((t.origin==ObservationOrigin.LINUX_NATIVE_RENDER_ONLY)==(playback==null),ProtocolError.ORDER)
            repeat(t.segments) { index ->
                job.stop.check();t.generating(key,index)
                var produced=0
                val rendered=renderer.render(index,job.stop) { count -> job.stop.check();t.pcm(key,index,count);produced+=count }
                try {
                    job.stop.check()
                    demand(rendered.pcm.size==produced && produced>0 && rendered.pcm.all { it.isFinite() },ProtocolError.INCOMPLETE_AUDIO)
                    if(playback==null) t.segmentRenderedOnly(key,index,rendered.eos)
                    else {
                        val receipt=playback.play(rendered.pcm,job.stop,{ n -> job.stop.check();t.write(key,index,n) },{ job.stop.check();t.playing(key,index) })
                        job.stop.check();t.segmentFinished(key,index,receipt.frames,rendered.eos && receipt.completed)
                    }
                } finally { rendered.pcm.fill(0f) }
            }
            job.stop.check();t.finish(key)
        } catch(_: CancellationException) { t.stop(key) }
        catch(_: Exception) { if(t.stage !in setOf(AudioStage.Cancelled,AudioStage.NativeUnknown)) t.fail(key) }
        finally { synchronized(this) { if(active===job) active=null } }
    }
}

/** A proposal cannot dispatch; the actual V1 peer is resolved/authenticated ONLY by its frozen Binder. */
const val SPEAK_SCHEMA="voice.fusion02.speak/1-draft"
enum class PublicUtterance(val text: String) { HELLO("Bonjour, ceci est un test public."), TRAIN("Demain, je prendrai le train pour Paris.") }
data class SpeakProposal(val key: AudioKey,val utterance: PublicUtterance,val remainingMs: Long) {
    init { require(remainingMs in 1..60000) }
    override fun toString()="SpeakProposal(schema=$SPEAK_SCHEMA,generation=${key.generation})"
}
enum class IntegrationStatus { BLOCKED_NATIVE_AUTHORITY, UNSUPPORTED_CORE_WHISPER_NOT_CONNECTED }
/** Capability port for the future CORE-owned Whisper; intentionally no recorder/audio payload API. */
interface CoreWhisperCapability { fun status(): IntegrationStatus }
object UnavailableCoreWhisper: CoreWhisperCapability {
    override fun status()=IntegrationStatus.UNSUPPORTED_CORE_WHISPER_NOT_CONNECTED
}
object DisabledIntegration {
    fun speak(@Suppress("UNUSED_PARAMETER") proposal: SpeakProposal)=IntegrationStatus.BLOCKED_NATIVE_AUTHORITY
    fun transcribe()=UnavailableCoreWhisper.status()
}
/** NEUTRAL means existing engine baseline, not a newly verified listening result. */
object EmotionSupport {
    fun status(emotion: String)=if(emotion=="NEUTRAL") "BASELINE_ONLY" else "UNSUPPORTED"
}
enum class AvatarMotion { IDLE, SPEAKING_ESTIMATED, PAUSED, STOPPED, UNKNOWN }
/** One consumer per operation. No phonemes or claim of acoustic listening. */
class AvatarObserver(private val key: AudioKey) {
    private var sequence=0L
    private var terminal=false
    @Synchronized fun accept(event: AudioEvent): AvatarMotion {
        demand(event.key==key,ProtocolError.CORRELATION);demand(!terminal || event.stage==AudioStage.NativeUnknown,ProtocolError.TERMINAL)
        demand(event.sequence==sequence+1,ProtocolError.ORDER);sequence=event.sequence
        return when(event.stage) {
            AudioStage.PlayingEstimated -> AvatarMotion.SPEAKING_ESTIMATED
            AudioStage.Paused -> AvatarMotion.PAUSED
            AudioStage.NativeUnknown -> { terminal=true;AvatarMotion.UNKNOWN }
            AudioStage.Finished,AudioStage.RenderedOnly,AudioStage.Cancelled,AudioStage.Failed -> { terminal=true;AvatarMotion.STOPPED }
            else -> AvatarMotion.IDLE
        }
    }
}
