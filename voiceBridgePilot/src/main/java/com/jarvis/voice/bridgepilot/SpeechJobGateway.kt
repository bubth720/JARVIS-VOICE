package com.jarvis.voice.bridgepilot
import android.os.SystemClock
import com.jarvis.voice.bridgeclient.*

/** Unwired local helper. Existing onTransact/AIDL/service/permission are deliberately untouched. */
internal class SpeechJobGateway(private val auth: PilotAuthorization,private val principal: ()->Principal,private val clock: ()->Long=SystemClock::elapsedRealtime) {
    private val tracker=NativeSpeechLeaseTracker()
    private val dispatcher=SpeechJobDispatcher()
    private val recent=linkedMapOf<String,Long>()
    private val generations=linkedMapOf<String,Pair<Long,Long>>()
    private var ticket: Map<String,String>?=null
    @Synchronized fun submit(r: PublicSpeechJobRequestV2,v1Fields: Map<String,String>): SpeechProgressEnvelope {
        val peer=principal();auth.authenticate(peer) // Resolved again per call, never from the request payload.
        need(v1Fields.keys==PilotWire.request && v1Fields.values.all { it.length<=4096 },"FIELDS")
        val fields=PilotWire.decode(PilotWire.encode(v1Fields.toMap()),PilotWire.request)
        need(fields["session_id"]==r.key.sessionId && fields["correlation_id"]==r.key.requestId && fields["generation"]==r.key.generation.toString(),"CORRELATION")
        need(fields["text"]==r.fixture.text,"PUBLIC_FIXTURE_ONLY")
        val now=clock();recent.entries.removeAll { it.value<=now };generations.entries.removeAll { it.value.second<=now }
        need(r.key.requestId !in recent,"REUSED_ID");need(recent.size<128 && generations.size<128,"QUEUE_LIMIT")
        need(r.key.generation>(generations[r.key.sessionId]?.first ?: 0),"STALE_GENERATION")
        val response=auth.request(peer,fields) // Reuses real grant/session/deadline/replay validation and native refusal.
        need(response["phase"]=="REFUSED" && response["code"]=="NATIVE_AUTHORITY_UNAVAILABLE","V1_REFUSAL_REQUIRED")
        ticket=fields.filterKeys { it in PilotWire.key };recent[r.key.requestId]=now+60000;generations[r.key.sessionId]=r.key.generation to (now+60000)
        val code=if(r.style==LabSpeechStyle.NEUTRAL) SpeechBlockCode.NATIVE_AUTHORITY_UNAVAILABLE else SpeechBlockCode.STYLE_UNSUPPORTED
        return dispatcher.refused(r.key,code,tracker.logicalJobEnded())
    }
    @Synchronized fun status(key: SpeechCorrelation): SpeechProgressEnvelope {
        val peer=principal();auth.authenticate(peer);val fields=ticket ?: throw Refused("NO_JOB");auth.status(peer,fields)
        return dispatcher.status(key)
    }
    @Synchronized fun cancel(key: SpeechCorrelation): SpeechProgressEnvelope {
        status(key);tracker.cancellationRequested();return dispatcher.cancel(key)
    }
}
internal class SpeechJobDispatcher {
    private var last: SpeechProgressEnvelope?=null
    fun refused(key: SpeechCorrelation,code: SpeechBlockCode,native: NativeSpeechObservation)=
        SpeechProgressEnvelope(key,LogicalSpeechPhase.REFUSED,code,nativeObservation=native).also { last=it }
    fun status(key: SpeechCorrelation): SpeechProgressEnvelope { need(last?.key==key,"STALE_RESULT");return requireNotNull(last) }
    fun cancel(key: SpeechCorrelation)=status(key).copy(phase=LogicalSpeechPhase.CANCELLED_LOGICAL_ONLY).also { last=it }
}

/** Preparation only. No existing endpoint invokes this factory. Identity rules are reused unchanged. */
internal fun createUnwiredSpeechGateway(context: android.content.Context)=SpeechJobGateway(PilotRuntime.authorization,
    { PilotIdentity(context).candidate(android.os.Binder.getCallingUid()) })
