package com.jarvis.voice.fusion02
import org.junit.Test
import org.junit.Assert.*
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TimelineTests {
    private fun key(session: String=UUID.randomUUID().toString(),generation: Long=1)=AudioKey(session,UUID.randomUUID().toString(),generation)
    private fun t(n: Int=1)=AudioTimeline(key(),n,ObservationOrigin.JVM_SIMULATION)
    private fun pcm(t: AudioTimeline,index: Int=0) { t.generating(t.key,index);t.pcm(t.key,index,24) }
    private fun done(t: AudioTimeline,index: Int=0) { pcm(t,index);t.write(t.key,index,24);t.playing(t.key,index);t.segmentFinished(t.key,index,24,true) }
    private fun refuses(code: ProtocolError,block: ()->Unit) { assertEquals(code,assertThrows(AudioProtocolException::class.java,block).code) }
    @Test fun completeRequiresObservedWritePresentationEosAndAllSegments() {
        val t=t(2);t.queued();done(t);t.pause();done(t,1);t.finish()
        assertEquals(AudioStage.Finished,t.stage);assertEquals(2,t.completed)
        assertEquals((1L..t.snapshot().size.toLong()).toList(),t.snapshot().map { it.sequence })
        assertTrue(t.snapshot().all { it.metadata()["humanAudibilityVerified"]==false && it.metadata()["nativeUnloadVerified"]==false })
    }
    @Test fun truncatedAndNoEosNeverSucceed() {
        val t=t();t.queued();pcm(t);refuses(ProtocolError.INCOMPLETE_AUDIO){t.write(t.key,0,23)}
        t.write(t.key,0,24);t.playing(t.key,0)
        refuses(ProtocolError.INCOMPLETE_AUDIO){t.segmentFinished(t.key,0,23,true)}
        refuses(ProtocolError.INCOMPLETE_AUDIO){t.segmentFinished(t.key,0,24,false)}
        refuses(ProtocolError.INCOMPLETE_AUDIO){t.finish()}
    }
    @Test fun queuedAndFirstPcmAreUniqueAndCounterBounded() {
        val t=t();t.queued();refuses(ProtocolError.ORDER){t.queued()};pcm(t);t.pcm(t.key,0,24)
        assertEquals(1,t.snapshot().count { it.stage==AudioStage.FirstPCM })
        refuses(ProtocolError.FRAME_LIMIT){t.pcm(t.key,0,480000)}
        refuses(ProtocolError.FRAME_LIMIT){t.pcm(t.key,0,0)}
        t.write(t.key,0,48)
    }
    @Test fun stopIsStickyRejectsLateAndWrongCorrelation() {
        val t=t();t.queued();pcm(t);refuses(ProtocolError.CORRELATION){t.stop(key())};assertTrue(t.stop());assertFalse(t.stop())
        refuses(ProtocolError.TERMINAL){t.write(t.key,0,24)};refuses(ProtocolError.TERMINAL){t.finish()}
        t.nativeUnknown();assertEquals(AudioStage.NativeUnknown,t.stage)
    }
    @Test fun clocksAreMonotonicRelativeAndTestClockDeclared() {
        var now=900L;val t=AudioTimeline(key(),1,ObservationOrigin.JVM_SIMULATION,{now},ClockSource.INJECTED_TEST_CLOCK)
        t.queued();now+=10;pcm(t);assertEquals(10L,t.snapshot().last().elapsedNs)
        assertEquals("INJECTED_TEST_CLOCK",t.snapshot().last().metadata()["clock"])
        now=1;refuses(ProtocolError.CLOCK_REGRESSED){t.stop()}
    }
    @Test fun renderOnlyCannotClaimDevicePlayback() {
        val t=AudioTimeline(key(),1,ObservationOrigin.LINUX_NATIVE_RENDER_ONLY);t.queued();pcm(t)
        refuses(ProtocolError.ORDER){t.write(t.key,0,24)};t.segmentRenderedOnly(t.key,0,true);t.finish()
        assertEquals(AudioStage.RenderedOnly,t.stage)
        assertFalse(t.snapshot().any { it.stage in setOf(AudioStage.PlayingEstimated,AudioStage.Finished) })
        val device=t();device.queued();pcm(device);refuses(ProtocolError.ORDER){device.segmentRenderedOnly(device.key,0,true)}
    }
    @Test fun boundedTelemetryHasExplicitGapAndImmutableSnapshot() {
        val t=t(100);t.queued();repeat(100){done(t,it)};t.finish()
        assertEquals(256,t.snapshot().size);assertTrue(t.dropped>0)
        assertThrows(UnsupportedOperationException::class.java){(t.snapshot() as MutableList).clear()}
        refuses(ProtocolError.ORDER){AvatarObserver(t.key).accept(t.snapshot().first())}
    }
    @Test fun metadataContainsOnlyAllowlistedOperationalFields() {
        val t=t();t.queued()
        assertEquals(setOf("schema","sessionId","requestId","generation","sequence","elapsedNs","clock","stage","segment","frames","origin","humanAudibilityVerified","nativeUnloadVerified","phoneticAlignmentVerified"),t.snapshot().single().metadata().keys)
        assertFalse(t.key.toString().contains(t.key.sessionId));assertFalse(t.snapshot().single().toString().contains(t.key.requestId))
    }
    @Test fun canonicalKeysAndSegmentBounds() {
        assertThrows(IllegalArgumentException::class.java){AudioKey("1-1-1-1-1",UUID.randomUUID().toString(),1)}
        assertThrows(IllegalArgumentException::class.java){key(generation=0)}
        assertThrows(IllegalArgumentException::class.java){t(4001)}
        val t=t();t.queued();refuses(ProtocolError.ORDER){t.generating(t.key,1)}
    }
    private val render=SegmentRenderer { _,_,cb -> cb(24);RenderedSegment(FloatArray(24){.1f},true) }
    private val play=SegmentPlayback { pcm,_,w,p -> w(pcm.size);p();PresentedSegment(pcm.size,true) }
    @Test fun executableProxyWipesPrivateBufferAndCompletes() {
        val p=LocalProxy();val j=p.prepare(key(),2,ObservationOrigin.JVM_SIMULATION);val buffers=mutableListOf<FloatArray>()
        p.execute(j,SegmentRenderer { _,_,cb -> cb(24);RenderedSegment(FloatArray(24){.1f}.also { buffers+=it },true) },play)
        assertEquals(AudioStage.Finished,j.timeline.stage);assertTrue(buffers.all { a -> a.all { it==0f } })
    }
    @Test fun replayStaleGenerationAndCapacityFailClosed() {
        val p=LocalProxy(2);val first=key();val j=p.prepare(first,1,ObservationOrigin.JVM_SIMULATION);p.execute(j,render,play)
        refuses(ProtocolError.REPLAY){p.prepare(first.copy(generation=2),1,ObservationOrigin.JVM_SIMULATION)}
        refuses(ProtocolError.STALE_GENERATION){p.prepare(key(first.sessionId),1,ObservationOrigin.JVM_SIMULATION)}
        p.execute(p.prepare(key(first.sessionId,2),1,ObservationOrigin.JVM_SIMULATION),render,play)
        refuses(ProtocolError.CAPACITY){p.prepare(key(),1,ObservationOrigin.JVM_SIMULATION)}
    }
    @Test fun stopDoesNotReleaseAdmissionWhileWorkerBlockedAndLateAudioRefused() {
        val p=LocalProxy();val j=p.prepare(key(),1,ObservationOrigin.JVM_SIMULATION)
        val entered=CountDownLatch(1);val leave=CountDownLatch(1);val ended=CountDownLatch(1)
        val worker=Thread { try { p.execute(j,SegmentRenderer { _,_,cb -> entered.countDown();check(leave.await(3,TimeUnit.SECONDS));cb(24);RenderedSegment(FloatArray(24),true) },play) } finally { ended.countDown() } }
        worker.start();assertTrue(entered.await(3,TimeUnit.SECONDS));assertTrue(p.stop(j.timeline.key))
        refuses(ProtocolError.BUSY){p.prepare(key(),1,ObservationOrigin.JVM_SIMULATION)}
        leave.countDown();assertTrue(ended.await(3,TimeUnit.SECONDS));worker.join()
        assertEquals(AudioStage.Cancelled,j.timeline.stage);assertFalse(j.timeline.snapshot().any { it.stage==AudioStage.AudioTrackWrite })
    }
    @Test fun unknownQuarantinesAdmissionEvenAfterWorkerReturns() {
        val p=LocalProxy();val j=p.prepare(key(),1,ObservationOrigin.JVM_SIMULATION);p.nativeUnknown(j.timeline.key)
        p.execute(j,render,play);assertEquals(AudioStage.NativeUnknown,j.timeline.stage)
        refuses(ProtocolError.TERMINAL){p.prepare(key(),1,ObservationOrigin.JVM_SIMULATION)}
    }
    @Test fun rendererFailureAndNonfiniteAudioCannotSucceed() {
        val p=LocalProxy();val j=p.prepare(key(),1,ObservationOrigin.JVM_SIMULATION)
        p.execute(j,SegmentRenderer { _,_,cb -> cb(1);RenderedSegment(floatArrayOf(Float.NaN),true) },play)
        assertEquals(AudioStage.Failed,j.timeline.stage)
        assertFalse(j.timeline.snapshot().any { it.stage==AudioStage.AudioTrackWrite })
    }
    @Test fun avatarRejectsDuplicatesAndStopsAtTerminalWithoutPhonemes() {
        val t=t();t.queued();done(t);t.finish();val a=AvatarObserver(t.key)
        val motions=t.snapshot().map(a::accept);assertTrue(motions.contains(AvatarMotion.SPEAKING_ESTIMATED));assertEquals(AvatarMotion.STOPPED,motions.last())
        refuses(ProtocolError.TERMINAL){a.accept(t.snapshot().last())}
        val b=AvatarObserver(t.key);b.accept(t.snapshot().first());refuses(ProtocolError.ORDER){b.accept(t.snapshot().first())}
    }
    @Test fun coreSttAndUnqualifiedEmotionsRemainUnsupported() {
        val proposal=SpeakProposal(key(),PublicUtterance.TRAIN,1000)
        assertEquals(IntegrationStatus.BLOCKED_NATIVE_AUTHORITY,DisabledIntegration.speak(proposal))
        assertEquals(IntegrationStatus.UNSUPPORTED_CORE_WHISPER_NOT_CONNECTED,DisabledIntegration.transcribe())
        for(e in listOf("JOY","SURPRISE","SADNESS","ANGER","unknown")) assertEquals("UNSUPPORTED",EmotionSupport.status(e))
        assertEquals("BASELINE_ONLY",EmotionSupport.status("NEUTRAL"))
    }
}
