package com.jarvis.voice.final01

import com.jarvis.voice.pocket.CancellationToken
import java.util.concurrent.atomic.AtomicBoolean

enum class DocumentState { READY, RUNNING, PAUSED, INTERRUPTED, COMPLETE, UNKNOWN }
class NativeResourceUnknown: IllegalStateException("UNKNOWN_NATIVE")
enum class Recovery { RETRY_INTERRUPTED_SEGMENT, SKIP_INTERRUPTED_SEGMENT }
/** In-memory only: process death never silently restarts a document or replays completed segments. */
class DocumentSession(val plan: DocumentPlan,private val emit: (VoiceEvent)->Unit={}) {
    private val lock=Any();private val pause=AtomicBoolean()
    @Volatile var state=DocumentState.READY;private set
    @Volatile private var cursor=0
    val completed get()=cursor-skipped
    @Volatile var skipped=0;private set
    private var presented=false
    private var ambiguous=false
    private fun event(t: VoiceEventType,samples: Long?=null) { runCatching { emit(VoiceEvent(t,completed,plan.segments.size,cursor.takeIf { it<plan.segments.size },samples,skipped)) } }
    fun requestPause() { if(state==DocumentState.RUNNING) pause.set(true) }
    /** FIRST_AUDIO means observed device presentation, never human audibility. */
    fun presentation(samples: Long?=null) { synchronized(lock) { check(state==DocumentState.RUNNING);if(!presented) { presented=true;event(VoiceEventType.FIRST_AUDIO,samples) } } }
    fun run(cancel: CancellationToken,resume: Boolean=false,recovery: Recovery?=null,job: (Int,DocumentSegment)->Unit) {
        synchronized(lock) {
            check(state==DocumentState.READY || resume && state in setOf(DocumentState.PAUSED,DocumentState.INTERRUPTED)) { "EXPLICIT_RESUME_REQUIRED_OR_BUSY" }
            if(state==DocumentState.INTERRUPTED && ambiguous) {
                require(recovery!=null) { "EXPLICIT_INTERRUPTED_SEGMENT_DECISION_REQUIRED" }
                if(recovery==Recovery.SKIP_INTERRUPTED_SEGMENT) { cursor++;skipped++ }
            }
            state=DocumentState.RUNNING;pause.set(false);event(VoiceEventType.START)
        }
        try {
            while(cursor<plan.segments.size) {
                cancel.check()
                if(pause.get()) { state=DocumentState.PAUSED;event(VoiceEventType.PAUSE);return }
                ambiguous=true;job(cursor,plan.segments[cursor]);ambiguous=false
                // Successful presentation is committed even if Stop arrives just after the final frame.
                cursor++;event(VoiceEventType.PROGRESS)
            }
            state=DocumentState.COMPLETE;event(VoiceEventType.COMPLETE)
        } catch(t: Throwable) {
            state=if(t is NativeResourceUnknown) DocumentState.UNKNOWN else DocumentState.INTERRUPTED
            event(if(state==DocumentState.UNKNOWN) VoiceEventType.UNKNOWN else if(cancel.isCancelled()) VoiceEventType.CANCELLED else VoiceEventType.ERROR)
            throw t
        }
    }
}
enum class Emotion { NEUTRAL, PROFESSIONAL, JOY, ENTHUSIASM, SURPRISE, SADNESS, SOFTNESS, EMPATHY, CONCERN, DOUBT, HESITATION, CONTROLLED_ANGER, FIRMNESS, URGENCY, CURIOSITY, AMUSEMENT, IRONY }
enum class EmotionSupport { NATIVE_NEUTRAL, UNSUPPORTED }
object PocketEmotionCapabilities {
    fun support(emotion: Emotion)=if(emotion==Emotion.NEUTRAL) EmotionSupport.NATIVE_NEUTRAL else EmotionSupport.UNSUPPORTED
    fun requireSupported(emotion: Emotion) { require(support(emotion)!=EmotionSupport.UNSUPPORTED) { "UNSUPPORTED" } }
}
