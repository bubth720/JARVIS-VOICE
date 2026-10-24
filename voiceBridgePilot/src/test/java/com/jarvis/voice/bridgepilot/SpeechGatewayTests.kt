package com.jarvis.voice.bridgepilot
import android.app.Application
import com.jarvis.voice.bridgeclient.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class SpeechGatewayTests {
    private fun id()=UUID.randomUUID().toString()
    private val peer=Principal(12345,PilotWire.CLIENT,"a".repeat(64),10,20)
    private var now=100L
    private val auth=PilotAuthorization { now }
    private var currentPeer=peer
    private val gateway=SpeechJobGateway(auth,{ currentPeer }) { now }
    private fun ready(session: String=id(),request: String=id(),generation: Long=1): Pair<PublicSpeechJobRequestV2,Map<String,String>> {
        auth.visibility(true);auth.arm(peer)
        val hello=auth.hello(peer,mapOf("version" to PilotWire.VERSION,"contract_sha256" to BuildConfig.CONTRACT_SHA,"session_id" to session))
        val r=PublicSpeechJobRequestV2(SpeechCorrelation(request,session,generation),PublicUtterance.HELLO)
        val fields=hello.filterKeys { it in PilotWire.base }+mapOf("correlation_id" to request,"cancellation_id" to id(),"grant_ref" to hello.getValue("grant_ref"),"core_epoch" to id(),"lease_id" to id(),"generation" to generation.toString(),"text" to r.fixture.text,"remaining_ms" to "1000")
        return r to fields
    }
    @Test fun authorizedPublicCallReusesV1AndRefusesNative() {
        val (r,f)=ready();val out=gateway.submit(r,f);assertEquals(SpeechBlockCode.NATIVE_AUTHORITY_UNAVAILABLE,out.code);assertEquals(NativeSpeechObservation.UNKNOWN,out.nativeObservation)
        assertEquals(out,gateway.status(r.key));assertEquals(LogicalSpeechPhase.CANCELLED_LOGICAL_ONLY,gateway.cancel(r.key).phase)
        assertEquals(NativeSpeechObservation.UNKNOWN,gateway.cancel(r.key).nativeObservation)
    }
    @Test fun identityConsentExpiryAndRevocationRemainMandatory() {
        val (r,f)=ready()
        currentPeer=peer.copy(uid=77);assertThrows(Refused::class.java) { gateway.submit(r,f) };currentPeer=peer;now=60101
        assertThrows(Refused::class.java) { gateway.submit(r,f) };now=100;auth.revoke()
        assertThrows(Refused::class.java) { gateway.submit(r,f) }
    }
    @Test fun privateTextAndV2FieldsCannotEnterV1Wire() {
        val (r,f)=ready()
        assertThrows(Refused::class.java) { gateway.submit(r,f+("text" to "Texte non autorisé par la fixture.")) }
        assertThrows(Refused::class.java) { PilotWire.decode(PilotWire.encode(f+("style" to "JOY")),PilotWire.request) }
    }
    @Test fun replayAndStaleGenerationAreBlockedAcrossRearming() {
        val (r,f)=ready();gateway.submit(r,f)
        val (_,again)=ready(r.key.sessionId,r.key.requestId,2)
        assertThrows(Refused::class.java) { gateway.submit(r.copy(key=r.key.copy(generation=2)),again) }
        val (r2,f2)=ready(r.key.sessionId,id(),1)
        assertThrows(Refused::class.java) { gateway.submit(r2,f2) }
    }
    @Test fun twoCompetingCallsConsumeOnlyOneGrant() {
        val (r,f)=ready();val outcomes=java.util.Collections.synchronizedList(mutableListOf<Boolean>())
        val threads=List(2) { Thread { try { gateway.submit(r,f);outcomes.add(true) } catch(_: Refused) { outcomes.add(false) } } }
        threads.forEach { it.start() };threads.forEach { it.join(2000);assertFalse(it.isAlive) };assertEquals(1,outcomes.count { it })
    }
    @Test fun staleResultCannotBecomeAvatarSpeakingOrReleaseProof() {
        val (r,f)=ready();gateway.submit(r,f)
        assertThrows(Refused::class.java) { gateway.status(r.key.copy(generation=2)) }
        val t=NativeSpeechLeaseTracker();assertEquals(NativeSpeechObservation.UNKNOWN,t.logicalJobEnded());assertEquals(NativeSpeechObservation.UNKNOWN,t.cancellationRequested())
    }
}
