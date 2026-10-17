package com.jarvis.voice.bridge
import org.junit.Assert.*
import org.junit.Test
class MapperTests {
    @Test fun declaredCoreRoleAndAuthorizationUuidDoNotAuthenticate() {
        Fixtures.reject(BridgeError.UNAUTHORIZED) { BridgeMapper().request(Fixtures.request().toString(),100,80000,26) }
        Fixtures.reject(BridgeError.UNAUTHORIZED) { Fixtures.mapper().request(Fixtures.request().put("origin","llm").toString(),100,80000,26) }
    }
    @Test fun exactAuthorizedPayloadCannotBeSubstitutedAndPrivateToStringsAreRedacted() {
        val text="Ne lance pas /remember ni de commande : $(echo secret)."
        val g=Fixtures.grant(text);val job=Fixtures.mapper(g).request(Fixtures.request().toString(),100,80000,26)
        assertEquals(text,job.text);assertEquals(g.binding,job.binding);assertEquals(80100,job.deadlineLocalMs)
        for(s in listOf(job.toString(),g.toString(),g.binding.toString())) { assertFalse(s.contains(text));assertFalse(s.contains(g.binding.profileSha256));assertFalse(s.contains(g.binding.profileId)) }
    }
    @Test fun consentProfileConfirmationSessionAuthorizationAndContentAreAllRequired() {
        val g=Fixtures.grant()
        for(bad in listOf(g.copy(consent=false),g.copy(confirmed=false),g.copy(sessionId=Fixtures.id(90)),g.copy(authorizationRef=Fixtures.id(90)),g.copy(contentRef=Fixtures.id(90)),g.copy(correlationId=Fixtures.id(90)),g.copy(cancellationId=Fixtures.id(90))))
            Fixtures.reject(BridgeError.UNAUTHORIZED) { Fixtures.mapper(bad).request(Fixtures.request().toString(),100,80000,26) }
    }
    @Test fun wrongPackModelAndProfileNeverFallBackToAnotherVoice() {
        for(key in listOf("pack_sha256","model_sha256","voice_profile_ref")) {
            val j=Fixtures.request();j.getJSONObject("payload").put(key,if(key=="voice_profile_ref") Fixtures.id(99) else "f".repeat(64))
            Fixtures.reject { Fixtures.mapper().request(j.toString(),100,80000,26) }
        }
    }
    @Test fun remainingTimeUsesLocalDeadlineNotAnotherClockDomain() {
        val j=Fixtures.request();assertEquals(150,Fixtures.mapper().request(j.toString(),100,50,26).deadlineLocalMs)
        for(remaining in listOf(0L,-1L,90001L)) Fixtures.reject(BridgeError.EXPIRED) { Fixtures.mapper().request(j.toString(),100,remaining,26) }
        Fixtures.reject(BridgeError.EXPIRED) { Fixtures.mapper(Fixtures.grant().copy(expiresLocalMs=99)).request(j.toString(),100,50,26) }
        j.getJSONObject("deadline").put("expires_at_ms",500000);Fixtures.reject { Fixtures.mapper().request(j.toString(),100,50,26) }
    }
    @Test fun api24And25KeepTheirOldTtsAndCannotEnterTheVoiceBridge() {
        for(api in listOf(24,25)) {
            Fixtures.reject(BridgeError.INCOMPATIBLE) { Fixtures.mapper().request(Fixtures.request().toString(),100,80000,api) }
            assertNull(Fixtures.mapper().capability(VoiceCapabilityQuery(sessionId=Fixtures.id(3),authorizationRef=Fixtures.id(4),androidApi=api),Fixtures.id(5),100,true).binding)
        }
    }
    @Test fun capabilityDoesNotDiscloseProfilesForAbsentUnauthorizedOrExpiredContext() {
        val q=VoiceCapabilityQuery(sessionId=Fixtures.id(3),authorizationRef=Fixtures.id(4),androidApi=26)
        assertEquals(BridgeError.MODULE_ABSENT,Fixtures.mapper().capability(q,Fixtures.id(5),100).error)
        assertNull(BridgeMapper().capability(q,Fixtures.id(5),100,true).binding)
        assertEquals(BridgeError.EXPIRED,Fixtures.mapper().capability(q,Fixtures.id(5),90000,true).error)
        assertEquals(Fixtures.binding,Fixtures.mapper().capability(q,Fixtures.id(5),100,true).binding)
    }
    @Test fun unicodeBoundsRejectMalformedDataWithoutTrimmingOrExecutingText() {
        validateSpeechText("é".repeat(300));validateSpeechText("😀".repeat(150))
        for(s in listOf(""," ","a".repeat(301),"😀".repeat(151),"\ud800","\udc00","a\u0000b","<unk>")) Fixtures.reject { validateSpeechText(s) }
        validateSpeechText("Bonjour à tous.\nNous sommes prêts.")
        val j=Fixtures.request();j.getJSONObject("limits").put("max_input_bytes",1);Fixtures.reject { Fixtures.mapper().request(j.toString(),100,50,26) }
        val k=Fixtures.request();k.getJSONObject("limits").put("max_output_tokens",50);Fixtures.reject { Fixtures.mapper().request(k.toString(),100,50,26) }
    }
    @Test fun unsupportedOptionsAreRejectedAndPriorityNeverPreempts() {
        Fixtures.reject { SpeechOptions(steps=4) };Fixtures.reject { SpeechOptions(temperature=Double.NaN) };Fixtures.reject { SpeechOptions(pauseMs=250) };Fixtures.reject { SpeechOptions(priority=4) };Fixtures.reject { SpeechOptions(intent="EXECUTE") }
        assertEquals(3,SpeechOptions(priority=3).priority)
    }
    @Test fun responseSuccessMeansGeneratedOnlyNotPlayedOrAudible() {
        val r=Fixtures.mapper().response(Fixtures.response().toString(),Fixtures.job(),Fixtures.id(3),200,true)
        assertEquals(GenerationOutcome.SUCCEEDED,r.generation);assertEquals(PlaybackOutcome.NOT_STARTED,r.playback);assertFalse(r.humanAudibilityVerified)
        Fixtures.reject(BridgeError.UNAUTHORIZED) { Fixtures.mapper().response(Fixtures.response().toString(),Fixtures.job(),Fixtures.id(3),200) }
        val real=Fixtures.response();real.getJSONObject("provenance").put("verification","runtime");assertEquals(GenerationOutcome.SUCCEEDED,Fixtures.mapper().response(real.toString(),Fixtures.job(),Fixtures.id(3),200).generation)
    }
    @Test fun staleSessionCorrelationDeadlineAndWrongProvenanceFailClosed() {
        val m=Fixtures.mapper();val job=Fixtures.job()
        Fixtures.reject(BridgeError.INTERRUPTED) { m.response(Fixtures.response().toString(),job,Fixtures.id(99),200,true) }
        Fixtures.reject(BridgeError.INTERRUPTED) { m.response(Fixtures.response().put("correlation_id",Fixtures.id(99)).toString(),job,Fixtures.id(3),200,true) }
        Fixtures.reject(BridgeError.EXPIRED) { m.response(Fixtures.response().toString(),job,Fixtures.id(3),job.deadlineLocalMs,true) }
        val j=Fixtures.response();j.getJSONObject("provenance").put("module_id","llm");Fixtures.reject(BridgeError.INCOMPATIBLE) { m.response(j.toString(),job,Fixtures.id(3),200,true) }
    }
    @Test fun fallbackRequiresExplicitRequestAndStoppedVoiceAndOfflineReadyTts() {
        assertEquals(FallbackDecision.NOT_REQUESTED,FallbackPolicy.decide(false,true,true))
        assertEquals(FallbackDecision.WAIT_FOR_STOP,FallbackPolicy.decide(true,false,true))
        assertEquals(FallbackDecision.FAILED_NO_AUTORETRY,FallbackPolicy.decide(true,true,false))
        assertEquals(FallbackDecision.LOCAL_TTS_EXPLICIT,FallbackPolicy.decide(true,true,true))
    }
}
