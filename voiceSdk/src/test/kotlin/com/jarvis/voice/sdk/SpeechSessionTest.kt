package com.jarvis.voice.sdk

import com.jarvis.voice.expression.*
import com.jarvis.voice.identity.*
import com.jarvis.voice.pocket.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CancellationException
import kotlin.concurrent.thread

internal fun request(text: String = "Bonjour à vous."): SpeechRequest {
    val identity = VoiceIdentityId("11111111-1111-4111-8111-111111111111")
    val binding = ExpressionVoiceBinding(identity,"22222222-2222-4222-8222-222222222222",
        "a".repeat(64),"b".repeat(64),PocketIdentity.ENGINE,PocketIdentity.packId)
    return SpeechRequest(UUID.randomUUID().toString(),ExpressionPolicy.plan(CoreVoiceRequestV1(text,identity),
        binding,QualitySampling(),42))
}
internal class RecordingOutput : OutputAudioPort {
    override val focusController = AudioFocusController()
    var played = 0; var stops = 0; var closed = 0
    var partial = false; var grant = true; var lose = false
    var samples = mutableListOf<Float>()
    override fun play(pcm: FloatArray,cancel: CancellationToken): AudioReceipt {
        cancel.check(); played++; samples.addAll(pcm.toList())
        if(grant) focusController.granted()
        if(lose) focusController.lost()
        return AudioReceipt(pcm.size - if(partial) 1 else 0)
    }
    override fun requestStop() { stops++ }
    override fun close() { closed++ }
}
class SpeechSessionTest {
    @Test fun sequentialBatchUsesOnePortAndOrderedPcm() {
        var renders=0
        val engine=LocalSpeechEnginePort { _,_ -> RenderedSpeech(floatArrayOf((++renders).toFloat()),true) }
        val output=RecordingOutput(); val states=mutableListOf<SpeechState>()
        val session=SpeechSessionRepository(engine).open(output,states::add)
        val result=session.speak(SpeechQueue(listOf(request(),request()))) as SpeechOutcome.Completed
        assertEquals(listOf(1f,2f),output.samples);assertEquals(2,renders)
        assertEquals(2,result.metrics.presentedSamples); assertEquals(1,result.metrics.peakQueuedSamples)
        assertEquals(listOf(SpeechState.PREPARING,SpeechState.PLAYING,SpeechState.PREPARING,SpeechState.PLAYING,SpeechState.IDLE),states)
        session.close(); session.close(); assertEquals(1,output.closed);assertEquals(SpeechState.RELEASED,session.state)
    }
    @Test fun closeDuringRenderWaitsForActualReturnAndRejectsLatePcm() {
        val started=CountDownLatch(1);val release=CountDownLatch(1)
        val repository=SpeechSessionRepository(LocalSpeechEnginePort { _,_ ->
            started.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));RenderedSpeech(floatArrayOf(.1f),true)
        })
        val output=RecordingOutput();val session=repository.open(output);val result=AtomicReference<SpeechOutcome>()
        val worker=thread { result.set(session.speak(SpeechQueue(listOf(request())))) }
        assertTrue(started.await(5,TimeUnit.SECONDS));session.close()
        assertEquals(SpeechState.STOPPING,session.state);assertEquals(0,output.closed)
        assertEquals(SpeechOutcome.Refused(SpeechRefusal.BUSY),repository.open(RecordingOutput()).speak(SpeechQueue(listOf(request()))))
        release.countDown();worker.join(5000);assertFalse(worker.isAlive)
        assertEquals(SpeechOutcome.Refused(SpeechRefusal.CANCELLED),result.get())
        assertEquals(0,output.played);assertEquals(1,output.closed);assertEquals(SpeechState.RELEASED,session.state)
        assertEquals(SpeechOutcome.Refused(SpeechRefusal.RELEASED),session.speak(SpeechQueue(listOf(request()))))
    }
    @Test fun requestStopAllowsExplicitNewRequestAfterDrain() {
        val started=CountDownLatch(1);val release=CountDownLatch(1);var calls=0
        val session=SpeechSessionRepository(LocalSpeechEnginePort { _,_ ->
            if(++calls==1) { started.countDown();assertTrue(release.await(5,TimeUnit.SECONDS)) }
            RenderedSpeech(floatArrayOf(.1f),true)
        }).open(RecordingOutput())
        val worker=thread { session.speak(SpeechQueue(listOf(request()))) }
        assertTrue(started.await(5,TimeUnit.SECONDS));session.requestStop();release.countDown();worker.join(5000)
        assertTrue(session.speak(SpeechQueue(listOf(request()))) is SpeechOutcome.Completed)
    }
    @Test fun interruptedOrCompletedRequestCannotBeReplayed() {
        val output=RecordingOutput();val session=SpeechSessionRepository(LocalSpeechEnginePort { _,_ -> RenderedSpeech(floatArrayOf(.2f),true) }).open(output)
        val queue=SpeechQueue(listOf(request()));assertTrue(session.speak(queue) is SpeechOutcome.Completed)
        assertEquals(SpeechOutcome.Refused(SpeechRefusal.DUPLICATE_REQUEST),session.speak(queue));assertEquals(1,output.played)
    }
    @Test fun preCancelledRequestDoesNotCallEngine() {
        var calls=0;val output=RecordingOutput()
        val session=SpeechSessionRepository(LocalSpeechEnginePort { _,_ -> calls++;error("not called") }).open(output)
        val cancel=CancellationToken().also { it.cancel() }
        assertEquals(SpeechOutcome.Refused(SpeechRefusal.CANCELLED),session.speak(SpeechQueue(listOf(request())),cancel))
        assertEquals(0,calls);assertEquals(0,output.played)
    }
    @Test fun uncertainEngineFailureBlocksOtherSessionsWithoutFallback() {
        var calls=0;val repo=SpeechSessionRepository(LocalSpeechEnginePort { _,_ -> calls++;error("private diagnostic not exported") })
        assertEquals(SpeechOutcome.Refused(SpeechRefusal.ENGINE_FAILED),repo.open(RecordingOutput()).speak(SpeechQueue(listOf(request()))))
        assertEquals(SpeechOutcome.Refused(SpeechRefusal.ENGINE_FAILED),repo.open(RecordingOutput()).speak(SpeechQueue(listOf(request()))))
        assertEquals(1,calls)
        assertTrue(repo.isQuarantined)
    }
    @Test fun missingPackReturnsRealAdapterRefusalWithoutNativeLoad() {
        val root=Files.createTempDirectory("voice-sdk-missing-").toFile()
        try {
            val vault=VoiceIdentityVault(File(root,"vault"))
            val engine=VaultQualitySpeechEngine(vault,File(root,"absent-pack"))
            val output=RecordingOutput();val session=SpeechSessionRepository(engine).open(output)
            assertEquals(SpeechOutcome.Refused(SpeechRefusal.MODEL_UNAVAILABLE),session.speak(SpeechQueue(listOf(request()))))
            assertEquals(0,output.played);assertTrue(vault.list().isEmpty())
        } finally { root.deleteRecursively() }
    }
    @Test fun completeEosRequiredBeforePlayback() {
        val output=RecordingOutput();val session=SpeechSessionRepository(LocalSpeechEnginePort { _,_ -> RenderedSpeech(floatArrayOf(.2f),false) }).open(output)
        assertTrue(session.speak(SpeechQueue(listOf(request()))) is SpeechOutcome.Refused);assertEquals(0,output.played)
    }
    @Test fun absentProfileRefusesBeforeModelOrVaultMutation() {
        val root=Files.createTempDirectory("voice-sdk-profile-").toFile()
        try {
            val pack=File(root,"pack").apply { mkdir() }
            File(pack,"bundle.json").writeText("{}")
            File(pack,"tokenizer.json").writeText("{}")
            val vault=VoiceIdentityVault(File(root,"vault"))
            val output=RecordingOutput();val session=SpeechSessionRepository(VaultQualitySpeechEngine(vault,pack)).open(output)
            assertEquals(SpeechOutcome.Refused(SpeechRefusal.PROFILE_UNAVAILABLE),session.speak(SpeechQueue(listOf(request()))))
            assertEquals(0,output.played);assertTrue(vault.list().isEmpty())
        } finally { root.deleteRecursively() }
    }
    @Test fun audioCloseFailureBlocksSubsequentDispatch() {
        val repo=SpeechSessionRepository(LocalSpeechEnginePort { _,_ -> RenderedSpeech(floatArrayOf(.1f),true) })
        val output=object: OutputAudioPort {
            override val focusController=AudioFocusController()
            override fun play(pcm: FloatArray,cancel: CancellationToken)=AudioReceipt(pcm.size)
            override fun requestStop() {}
            override fun close() { error("release failed") }
        }
        repo.open(output).close()
        assertEquals(SpeechOutcome.Refused(SpeechRefusal.ENGINE_FAILED),repo.open(RecordingOutput()).speak(SpeechQueue(listOf(request()))))
    }
    @Test fun nonFinitePcmNeverPlays() {
        val output=RecordingOutput();val session=SpeechSessionRepository(LocalSpeechEnginePort { _,_ -> RenderedSpeech(floatArrayOf(Float.NaN),true) }).open(output)
        assertTrue(session.speak(SpeechQueue(listOf(request()))) is SpeechOutcome.Refused);assertEquals(0,output.played)
    }
    @Test fun totalBatchPreservesTwentySecondLimit() {
        val output=RecordingOutput();val session=SpeechSessionRepository(LocalSpeechEnginePort { _,_ -> RenderedSpeech(FloatArray(240001),true) }).open(output)
        assertEquals(SpeechOutcome.Refused(SpeechRefusal.LIMIT_EXCEEDED),session.speak(SpeechQueue(listOf(request(),request()))))
        assertEquals(1,output.played)
    }
    @Test fun partialPlaybackIsNotSuccess() {
        val output=RecordingOutput().also { it.partial=true }
        val session=SpeechSessionRepository(LocalSpeechEnginePort { _,_ -> RenderedSpeech(floatArrayOf(.2f),true) }).open(output)
        assertEquals(SpeechOutcome.Refused(SpeechRefusal.AUDIO_UNAVAILABLE),session.speak(SpeechQueue(listOf(request()))))
    }
    @Test fun focusUnknownIsNotOfflinePlaybackProof() {
        val output=RecordingOutput().also { it.grant=false }
        val session=SpeechSessionRepository(LocalSpeechEnginePort { _,_ -> RenderedSpeech(floatArrayOf(.2f),true) }).open(output)
        assertEquals(SpeechOutcome.Refused(SpeechRefusal.NON_VERIFIED),session.speak(SpeechQueue(listOf(request()))))
    }
    @Test fun focusLossRefusesContinuation() {
        val output=RecordingOutput().also { it.lose=true }
        val session=SpeechSessionRepository(LocalSpeechEnginePort { _,_ -> RenderedSpeech(floatArrayOf(.2f),true) }).open(output)
        assertEquals(SpeechOutcome.Refused(SpeechRefusal.AUDIO_UNAVAILABLE),session.speak(SpeechQueue(listOf(request(),request()))))
        assertEquals(1,output.played)
    }
    @Test fun fallbackRequiresExplicitVerifiedOfflineVoice() {
        assertEquals(SpeechRefusal.NON_VERIFIED,AndroidTtsFallbackGate.check(false,true,false))
        assertEquals(SpeechRefusal.NON_VERIFIED,AndroidTtsFallbackGate.check(true,true,null))
        assertEquals(SpeechRefusal.NON_VERIFIED,AndroidTtsFallbackGate.check(true,true,true))
        assertEquals(SpeechRefusal.NON_VERIFIED,AndroidTtsFallbackGate.check(true,false,false))
        assertNull(AndroidTtsFallbackGate.check(true,true,false))
    }
    @Test fun frameQueueCopiesAndPreservesOrderAndBound() {
        val source=floatArrayOf(1f,2f);val queue=PcmFrameQueue(3)
        queue.offer(source);source[0]=3f;queue.offer(floatArrayOf(4f))
        assertEquals(3,queue.peakSamples);assertArrayEquals(floatArrayOf(1f,2f,4f),queue.takeComplete(),0f)
        assertEquals(0,queue.samples);queue.close()
        assertThrows(IllegalStateException::class.java) { queue.offer(floatArrayOf(1f)) }
    }
    @Test fun frameQueueRejectsOverflowWithoutLosingFirstFrame() {
        val queue=PcmFrameQueue(2);queue.offer(floatArrayOf(1f))
        assertThrows(SpeechRejected::class.java) { queue.offer(floatArrayOf(2f,3f)) }
        assertArrayEquals(floatArrayOf(1f),queue.takeComplete(),0f)
    }
    @Test fun noSilentBatchTruncationOrDuplicateRequest() {
        assertThrows(IllegalArgumentException::class.java) { SpeechQueue(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { SpeechQueue(List(4) { request() }) }
        val request=request();assertThrows(IllegalArgumentException::class.java) { SpeechQueue(listOf(request,request)) }
    }
    @Test fun requestCannotSelectAnotherIdentityThanItsBoundProfile() {
        val valid=request()
        val mismatched=valid.plan.copy(request=CoreVoiceRequestV1("Bonjour à vous.",VoiceIdentityId("33333333-3333-4333-8333-333333333333")))
        assertThrows(IllegalArgumentException::class.java) { SpeechRequest(UUID.randomUUID().toString(),mismatched) }
    }
    @Test fun diagnosticObjectsDoNotEchoTextOrPrivatePcm() {
        assertFalse(request("Contenu confidentiel.").toString().contains("Contenu confidentiel"))
        assertEquals("RenderedSpeech(samples=1, eosReached=true)",RenderedSpeech(floatArrayOf(.123456f),true).toString())
    }
    @Test fun focusObservationFromPreviousUtteranceCannotBeReused() {
        var calls=0
        val output=object: OutputAudioPort {
            override val focusController=AudioFocusController()
            override fun play(pcm: FloatArray,cancel: CancellationToken): AudioReceipt {
                if(++calls==1) focusController.granted()
                return AudioReceipt(pcm.size)
            }
            override fun requestStop() {}
            override fun close() {}
        }
        val session=SpeechSessionRepository(LocalSpeechEnginePort { _,_ -> RenderedSpeech(floatArrayOf(.1f),true) }).open(output)
        assertEquals(SpeechOutcome.Refused(SpeechRefusal.NON_VERIFIED),session.speak(SpeechQueue(listOf(request(),request()))))
    }
    @Test fun knownPreNativeValidationRefusalAllowsExplicitCorrectedRequest() {
        var calls=0
        val session=SpeechSessionRepository(LocalSpeechEnginePort { _,_ ->
            if(++calls==1) throw SpeechRejected(SpeechRefusal.INVALID_REQUEST)
            RenderedSpeech(floatArrayOf(.1f),true)
        }).open(RecordingOutput())
        assertEquals(SpeechOutcome.Refused(SpeechRefusal.INVALID_REQUEST),session.speak(SpeechQueue(listOf(request()))))
        assertTrue(session.speak(SpeechQueue(listOf(request()))) is SpeechOutcome.Completed)
    }
    @Test fun unknownOutputFailureCannotTriggerAnotherEngineCall() {
        var renders=0
        val repo=SpeechSessionRepository(LocalSpeechEnginePort { _,_ -> renders++;RenderedSpeech(floatArrayOf(.1f),true) })
        val output=object: OutputAudioPort {
            override val focusController=AudioFocusController()
            override fun play(pcm: FloatArray,cancel: CancellationToken): AudioReceipt = error("cleanup unknown")
            override fun requestStop() {}
            override fun close() {}
        }
        assertEquals(SpeechOutcome.Refused(SpeechRefusal.AUDIO_UNAVAILABLE),repo.open(output).speak(SpeechQueue(listOf(request()))))
        assertTrue(repo.isQuarantined)
        assertEquals(SpeechOutcome.Refused(SpeechRefusal.ENGINE_FAILED),repo.open(RecordingOutput()).speak(SpeechQueue(listOf(request()))))
        assertEquals(1,renders)
    }
    @Test fun cleanupFailureSuppressedByUseDuringCancellationOrRefusalPreventsSecondEngineCall() {
        for (primary in listOf(CancellationException("synthetic cancellation"),SpeechRejected(SpeechRefusal.INVALID_REQUEST))) {
            var calls=0
            val repo=SpeechSessionRepository(LocalSpeechEnginePort { _,_ ->
                calls++
                AutoCloseable { throw IllegalStateException("synthetic cleanup failure") }.use { throw primary }
            })
            val output=RecordingOutput()
            val expected=if(primary is CancellationException) SpeechRefusal.CANCELLED else SpeechRefusal.INVALID_REQUEST
            assertEquals(SpeechOutcome.Refused(expected),repo.open(output).speak(SpeechQueue(listOf(request()))))
            assertEquals(1,primary.suppressed.size);assertTrue(repo.isQuarantined);assertEquals(0,output.played)
            assertEquals(SpeechOutcome.Refused(SpeechRefusal.ENGINE_FAILED),repo.open(RecordingOutput()).speak(SpeechQueue(listOf(request()))))
            assertEquals(1,calls)
        }
    }
}
