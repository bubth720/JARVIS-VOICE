package com.jarvis.voice.bridge.harness
import com.jarvis.voice.bridge.*
import com.jarvis.voice.expression.*
import com.jarvis.voice.identity.*
import org.junit.Assert.*
import org.junit.Test
class AdapterTests {
    private fun id(n: Int)="00000000-0000-4000-8000-"+n.toString().padStart(12,'0')
    private fun job()=SpeechJobRequest(id(1),id(2),id(3),"Bonjour à tous.\nNous sommes prêts.",VoiceBinding(id(4),id(5),"a".repeat(64),"b".repeat(64),PocketIdentity.ENGINE,"dd5fe98f5577ecfd56db9af8057cd05fe20cbc91560042aec8a56cfb01c34995",PocketIdentity.packId),SpeechOptions(pauseMs=250,allowSentencePauses=true),90000)
    @Test fun dtoMatchesUnchanged005iExactlyWithoutChangingTextOrSampling() {
        val j=job();val actual=CoreRequestAdapter.request(j);val old=CoreVoiceRequestV1(j.text,VoiceIdentityId(j.binding.identityId),pauseMs=250,allowSentencePauses=true)
        assertEquals(old,actual);assertEquals(j.text,CoreRequestAdapter.plan(j).request.utteranceText)
    }
    @Test fun defaultPortNeverLoadsOrConnectsAndRepeatedCancelHasNoSideEffect() {
        val p=DisabledVoiceAdapter();val result=p.render(job());assertEquals(BridgeError.MODULE_ABSENT,result.error);assertEquals(PlaybackOutcome.NOT_STARTED,result.playback);assertFalse(result.humanAudibilityVerified)
        repeat(2) { p.cancel(id(3)) }
    }
    @Test fun unsupportedEmotionIsHonestMetadataAndWrongEngineCannotFallback() {
        val j=job();val plan=CoreRequestAdapter.plan(j.copy(options=SpeechOptions(intent="HUMOROUS")))
        assertTrue(plan.effects.any { it.field=="intent" && it.status=="unsupported" });assertEquals(j.binding.profileId,plan.binding.profileId)
        try { CoreRequestAdapter.plan(j.copy(binding=j.binding.copy(engineId="other")));fail() } catch(_: IllegalArgumentException) {}
    }
}
