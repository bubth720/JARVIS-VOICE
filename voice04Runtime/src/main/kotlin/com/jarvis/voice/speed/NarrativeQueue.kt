package com.jarvis.voice.speed
import com.jarvis.voice.pocket.CancellationToken
import com.jarvis.voice.pocket.FrenchFrontend
import java.util.concurrent.atomic.AtomicBoolean

enum class NarrativeState { READY, RUNNING, PAUSED, INTERRUPTED, COMPLETED, UNKNOWN_NATIVE }
class UnknownNarrativeNative: IllegalStateException("UNKNOWN_NATIVE")
object SentencePreflight {
    fun prepare(request: LongFormSpeechRequestV1,frontend: FrenchFrontend)=LongFormPlanner.prepare(request,frontend)
}
/** No queue-wide monitor held during ONNX/audio; pause requests can arrive from the UI. */
class NarrativeQueue(val plan: LongFormPlan) {
    private val lock=Any();private val pause=AtomicBoolean()
    @Volatile var state=NarrativeState.READY;private set
    @Volatile var completedJobs=0;private set
    fun requestPause(): Boolean { if(state!=NarrativeState.RUNNING) return false;pause.set(true);return true }
    fun run(cancel: CancellationToken,resume: Boolean=false,job: (Int,LongFormSegment)->Unit) {
        synchronized(lock) {
            check(state==NarrativeState.READY || resume && state in setOf(NarrativeState.PAUSED,NarrativeState.INTERRUPTED)) { "NARRATIVE_BUSY_OR_RESUME_REQUIRED" }
            state=NarrativeState.RUNNING;pause.set(false)
        }
        try {
            while(completedJobs<plan.segments.size) {
                cancel.check()
                if(pause.get()) { state=NarrativeState.PAUSED;return }
                job(completedJobs,plan.segments[completedJobs]);cancel.check();completedJobs++
            }
            state=NarrativeState.COMPLETED
        } catch(t: Throwable) { state=if(t is UnknownNarrativeNative) NarrativeState.UNKNOWN_NATIVE else NarrativeState.INTERRUPTED;throw t }
    }
}
