package com.jarvis.voice.bridge

enum class SpeechState { UNKNOWN,UNAVAILABLE,READY,GENERATING,GENERATED,PLAYING,CANCEL_REQUESTED,NATIVE_DONE,UNLOADED,REFUSED }
data class Reservation(val generation: Long,val correlationId: String,val sessionId: String) {
    override fun toString()="Reservation[generation=$generation]"
}
data class AdmissionFixture(val availableBytes: Long,val lowMemory: Boolean,val thermalStatus: Int,
    val otherModelsKnownUnloaded: Boolean,val moduleAvailable: Boolean=true,val androidApi: Int=26)
/** Contract simulation only; not an Android arbiter, RAM reservation, or attestation. */
class SpeechJobCoordinator {
    var state=SpeechState.UNKNOWN;private set
    private var serial=0L
    private var active: Reservation?=null
    private var request: SpeechJobRequest?=null
    private var ended=false
    private var rendered=false
    private var audioStopped=true
    private var cancelled=false
    private val recent=linkedMapOf<Pair<String,String>,Long>()
    @Synchronized fun reserve(r: SpeechJobRequest,guard: AdmissionFixture,now: Long): Reservation {
        checkBridge(active==null,BridgeError.BUSY)
        fun refuse(code: BridgeError): Nothing { state=if(code==BridgeError.MODULE_ABSENT) SpeechState.UNAVAILABLE else SpeechState.REFUSED;throw BridgeFailure(code) }
        if(!guard.moduleAvailable) refuse(BridgeError.MODULE_ABSENT)
        if(guard.androidApi<26) refuse(BridgeError.INCOMPATIBLE)
        if(now<0 || now>=r.deadlineLocalMs || r.deadlineLocalMs-now>90000) refuse(BridgeError.EXPIRED)
        recent.entries.removeAll { it.value<=now }
        if(recent.containsKey(r.sessionId to r.correlationId)) refuse(BridgeError.INTERRUPTED)
        if(recent.size>=128) refuse(BridgeError.BUSY)
        if(!guard.otherModelsKnownUnloaded || guard.lowMemory || guard.availableBytes<2048L*1048576 || guard.thermalStatus !in 0..2) refuse(BridgeError.INSUFFICIENT_RESOURCES)
        val ticket=Reservation(++serial,r.correlationId,r.sessionId)
        recent[r.sessionId to r.correlationId]=r.deadlineLocalMs
        active=ticket;request=r;ended=false;rendered=false;audioStopped=true;cancelled=false;state=SpeechState.READY
        return ticket
    }
    private fun current(t: Reservation) { checkBridge(active==t,BridgeError.INTERRUPTED) }
    private fun live(t: Reservation,now: Long) { current(t);checkBridge(now>=0);if(now>=request!!.deadlineLocalMs) { cancel(t);throw BridgeFailure(BridgeError.EXPIRED) };checkBridge(!cancelled,BridgeError.CANCELLED) }
    @Synchronized fun generating(t: Reservation,now: Long) { live(t,now);checkBridge(state==SpeechState.READY);state=SpeechState.GENERATING }
    @Synchronized fun generated(t: Reservation,now: Long) { live(t,now);checkBridge(state==SpeechState.GENERATING);rendered=true;state=SpeechState.GENERATED }
    @Synchronized fun cancel(t: Reservation): Boolean { current(t);if(cancelled) return false;cancelled=true;state=SpeechState.CANCEL_REQUESTED;return true }
    @Synchronized fun expire(t: Reservation,now: Long): Boolean { current(t);return if(now>=request!!.deadlineLocalMs) cancel(t) else false }
    @Synchronized fun nativeDone(t: Reservation) { current(t);checkBridge(state in setOf(SpeechState.GENERATED,SpeechState.CANCEL_REQUESTED,SpeechState.GENERATING,SpeechState.UNKNOWN,SpeechState.NATIVE_DONE));ended=true;state=SpeechState.NATIVE_DONE }
    @Synchronized fun playing(t: Reservation,now: Long) { live(t,now);checkBridge(ended && rendered && state==SpeechState.NATIVE_DONE);audioStopped=false;state=SpeechState.PLAYING }
    @Synchronized fun audioStopped(t: Reservation) { current(t);audioStopped=true;if(ended && !cancelled) state=SpeechState.NATIVE_DONE }
    @Synchronized fun lostOrCleanupFailed(t: Reservation) { current(t);ended=false;audioStopped=false;state=SpeechState.UNKNOWN;cancelled=true }
    @Synchronized fun confirmUnloaded(t: Reservation,modelResident: Boolean) {
        current(t);checkBridge(ended && audioStopped && !modelResident,BridgeError.CLEANUP_FAILED)
        state=SpeechState.UNLOADED;active=null;request=null
    }
    @Synchronized fun acceptsResponse(t: Reservation,session: String,now: Long)=now>=0 && active==t && t.sessionId==session && !cancelled && now<request!!.deadlineLocalMs
    @Synchronized fun hasBlockedReservation()=active!=null
}
/** Future production implementations must keep PCM private; there is no Android endpoint here. */
interface VoiceRendererPort {
    fun render(request: SpeechJobRequest): SpeechJobResult
    fun cancel(cancellationId: String)
}

enum class FallbackDecision { NOT_REQUESTED,WAIT_FOR_STOP,LOCAL_TTS_EXPLICIT,FAILED_NO_AUTORETRY }
/** A timeout alone must not start a second voice over a possibly active renderer. */
object FallbackPolicy {
    fun decide(userRequested: Boolean,voiceDefinitelyStopped: Boolean,localOfflineTtsReady: Boolean)=when {
        !userRequested->FallbackDecision.NOT_REQUESTED
        !voiceDefinitelyStopped->FallbackDecision.WAIT_FOR_STOP
        !localOfflineTtsReady->FallbackDecision.FAILED_NO_AUTORETRY
        else->FallbackDecision.LOCAL_TTS_EXPLICIT
    }
}
