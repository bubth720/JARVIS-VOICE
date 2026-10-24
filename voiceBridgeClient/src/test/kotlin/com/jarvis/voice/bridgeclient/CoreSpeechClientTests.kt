package com.jarvis.voice.bridgeclient
import org.junit.Test
import org.junit.Assert.*
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
class CoreSpeechClientTests {
    private fun request()=PublicSpeechJobRequestV2(SpeechCorrelation(UUID.randomUUID().toString(),UUID.randomUUID().toString(),1),PublicUtterance.HELLO)
    private fun refused(r: PublicSpeechJobRequestV2)=SpeechProgressEnvelope(r.key,LogicalSpeechPhase.REFUSED,SpeechBlockCode.NATIVE_AUTHORITY_UNAVAILABLE)
    @Test fun publicRefusalHasNoFakePcmCuesOrNativeRelease() {
        val c=CoreSpeechClientPort(PublicSpeechTransport(::refused));assertEquals(SpeechConnectionMode.TEXT_ONLY,c.mode)
        val r=c.submit(request());assertTrue(r.logicalJobReleased);assertEquals(NativeSpeechObservation.UNKNOWN,r.nativeObservation);assertTrue(r.relativePcmCues.isEmpty())
    }
    @Test fun replayAndWrongCorrelationAreRefused() {
        val r=request();val c=CoreSpeechClientPort(PublicSpeechTransport(::refused));c.submit(r)
        assertThrows(IllegalStateException::class.java) { c.submit(r) }
        val bad=CoreSpeechClientPort(PublicSpeechTransport { refused(request()) })
        assertThrows(IllegalStateException::class.java) { bad.submit(request()) }
    }
    @Test fun disconnectRejectsLateReplyAndConcurrentRequestWithoutDeadlock() {
        val entered=CountDownLatch(1);val release=CountDownLatch(1);var stale=false
        val c=CoreSpeechClientPort(PublicSpeechTransport { r->entered.countDown();check(release.await(2,TimeUnit.SECONDS));refused(r) })
        val thread=Thread { try { c.submit(request()) } catch(_: IllegalStateException) { stale=true } };thread.start();assertTrue(entered.await(2,TimeUnit.SECONDS))
        assertThrows(IllegalStateException::class.java) { c.submit(request()) };c.disconnect();release.countDown();thread.join(2000)
        assertFalse(thread.isAlive);assertTrue(stale);assertEquals(SpeechConnectionMode.TEXT_ONLY,c.mode)
    }
    @Test fun invalidVersionsAndFakeCuesCannotBeConstructed() {
        assertThrows(IllegalArgumentException::class.java) { request().copy(version="voice.bridge.fixture/1") }
        assertThrows(IllegalArgumentException::class.java) { refused(request()).copy(relativePcmCues=listOf(1)) }
    }
}
