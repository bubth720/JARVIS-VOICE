package com.jarvis.voice.bridgepilot
import android.app.Application
import com.jarvis.voice.fusion02.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/** Test-only adapter to frozen V1: not registered in Binder; real UID lookup remains in V1 service. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class Fusion02ProposalTests {
    private fun id()=UUID.randomUUID().toString()
    private val peer=Principal(12345,PilotWire.CLIENT,"a".repeat(64),10,20)
    private var now=100L
    private val auth=PilotAuthorization { now }
    private fun setup(): Map<String,String> {
        auth.visibility(true);auth.arm(peer)
        return auth.hello(peer,mapOf("version" to PilotWire.VERSION,"contract_sha256" to BuildConfig.CONTRACT_SHA,"session_id" to id()))
    }
    private fun proposal(h: Map<String,String>)=SpeakProposal(AudioKey(h.getValue("session_id"),id(),1),PublicUtterance.TRAIN,1000)
    private fun v1(h: Map<String,String>,p: SpeakProposal)=h.filterKeys { it in PilotWire.base }+mapOf(
        "correlation_id" to p.key.requestId,"cancellation_id" to id(),"grant_ref" to h.getValue("grant_ref"),
        "core_epoch" to id(),"lease_id" to id(),"generation" to p.key.generation.toString(),"text" to p.utterance.text,"remaining_ms" to p.remainingMs.toString())
    @Test fun proposalThroughActualV1AuthorityStillRefusesNativeAndReplay() {
        val h=setup();val p=proposal(h);val fields=v1(h,p)
        val response=auth.request(peer,fields)
        assertEquals("NATIVE_AUTHORITY_UNAVAILABLE",response["code"])
        assertEquals(p.key.requestId,response["correlation_id"])
        assertFalse(response.containsKey("text"))
        assertThrows(Refused::class.java){auth.request(peer,fields)}
        assertThrows(Refused::class.java){auth.status(peer,fields.filterKeys { it in PilotWire.key }+("generation" to "2"))}
        assertEquals(IntegrationStatus.BLOCKED_NATIVE_AUTHORITY,DisabledIntegration.speak(p))
    }
    @Test fun proposalCannotBypassPrincipalGrantOrLeaseExpiration() {
        val h=setup();val fields=v1(h,proposal(h))
        for(p in listOf(peer.copy(uid=0),peer.copy(certificate="b".repeat(64)),peer.copy(updated=21)))
            assertThrows(Refused::class.java){auth.request(p,fields)}
        assertThrows(Refused::class.java){auth.request(peer,fields+("grant_ref" to id()))}
        now=60100;assertThrows(Refused::class.java){auth.request(peer,fields)}
    }
    @Test fun localStopRevocationDoesNotAttestNativeUnloadOrFreeGlobalLease() {
        val h=setup();val p=proposal(h);auth.request(peer,v1(h,p));auth.visibility(false)
        assertThrows(Refused::class.java){auth.authenticate(peer)}
        val proxy=LocalProxy();val job=proxy.prepare(p.key,1,ObservationOrigin.JVM_SIMULATION)
        proxy.stop(p.key);proxy.nativeUnknown(p.key)
        assertEquals(false,job.timeline.snapshot().last().metadata()["nativeUnloadVerified"])
        assertThrows(AudioProtocolException::class.java){proxy.prepare(p.key.copy(requestId=id(),generation=2),1,ObservationOrigin.JVM_SIMULATION)}
    }
}
