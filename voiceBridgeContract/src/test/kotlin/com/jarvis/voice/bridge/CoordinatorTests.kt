package com.jarvis.voice.bridge
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
class CoordinatorTests {
    private val guard=AdmissionFixture(3L*1024*1048576,false,0,true)
    private fun finish(c: SpeechJobCoordinator,t: Reservation) { c.cancel(t);c.nativeDone(t);c.audioStopped(t);c.confirmUnloaded(t,false) }
    @Test fun oneHeavyWorkAndTwoGibFloorAreNeverRelaxed() {
        val c=SpeechJobCoordinator();val t=c.reserve(Fixtures.job(),guard,100)
        Fixtures.reject(BridgeError.BUSY) { c.reserve(Fixtures.job(10),guard,100) };finish(c,t)
        Fixtures.reject(BridgeError.INSUFFICIENT_RESOURCES) { c.reserve(Fixtures.job(10),guard.copy(availableBytes=2147483647),100) }
        c.reserve(Fixtures.job(10),guard.copy(availableBytes=2147483648),100)
    }
    @Test fun unknownResidentsSensorsLowMemoryAbsenceAndApiFail() {
        for(bad in listOf(guard.copy(otherModelsKnownUnloaded=false),guard.copy(thermalStatus=-1),guard.copy(thermalStatus=3),guard.copy(lowMemory=true)))
            Fixtures.reject(BridgeError.INSUFFICIENT_RESOURCES) { SpeechJobCoordinator().reserve(Fixtures.job(),bad,100) }
        Fixtures.reject(BridgeError.MODULE_ABSENT) { SpeechJobCoordinator().reserve(Fixtures.job(),guard.copy(moduleAvailable=false),100) }
        Fixtures.reject(BridgeError.INCOMPATIBLE) { SpeechJobCoordinator().reserve(Fixtures.job(),guard.copy(androidApi=25),100) }
    }
    @Test fun cancelIsIdempotentAndExpirationNeverMeansUnloaded() {
        val c=SpeechJobCoordinator();val t=c.reserve(Fixtures.job(),guard,100);c.generating(t,100)
        assertTrue(c.cancel(t));assertFalse(c.cancel(t));assertTrue(c.hasBlockedReservation())
        Fixtures.reject(BridgeError.CLEANUP_FAILED) { c.confirmUnloaded(t,false) }
        Fixtures.reject(BridgeError.BUSY) { c.reserve(Fixtures.job(10),guard,90000) }
        c.nativeDone(t);c.confirmUnloaded(t,false);assertFalse(c.hasBlockedReservation())
        val t2=c.reserve(Fixtures.job(10),guard,100);assertTrue(c.expire(t2,80100));assertFalse(c.acceptsResponse(t2,Fixtures.id(3),80100));assertTrue(c.hasBlockedReservation())
    }
    @Test fun completePlaybackStillRequiresNativeEndAndResidentRelease() {
        val c=SpeechJobCoordinator();val t=c.reserve(Fixtures.job(),guard,100);c.generating(t,100);c.generated(t,200)
        Fixtures.reject { c.playing(t,201) };c.nativeDone(t);c.playing(t,201)
        Fixtures.reject(BridgeError.CLEANUP_FAILED) { c.confirmUnloaded(t,false) };c.audioStopped(t)
        Fixtures.reject(BridgeError.CLEANUP_FAILED) { c.confirmUnloaded(t,true) };c.confirmUnloaded(t,false);assertEquals(SpeechState.UNLOADED,c.state)
    }
    @Test fun processDeathQuarantinesAndOldGenerationCannotReleaseNewJob() {
        val c=SpeechJobCoordinator();val old=c.reserve(Fixtures.job(),guard,100);c.generating(old,100);c.lostOrCleanupFailed(old)
        assertEquals(SpeechState.UNKNOWN,c.state);Fixtures.reject(BridgeError.BUSY) { c.reserve(Fixtures.job(10),guard,100) }
        c.nativeDone(old);Fixtures.reject(BridgeError.CLEANUP_FAILED) { c.confirmUnloaded(old,false) };c.audioStopped(old);c.confirmUnloaded(old,false)
        val newer=c.reserve(Fixtures.job(10),guard,100)
        Fixtures.reject(BridgeError.INTERRUPTED) { c.nativeDone(old) };assertTrue(c.hasBlockedReservation());finish(c,newer)
    }
    @Test fun completedOperationCannotBeReplayedWithinDeadlineAndOldSessionCannotConsumeResponse() {
        val c=SpeechJobCoordinator();val t=c.reserve(Fixtures.job(),guard,100)
        assertFalse(c.acceptsResponse(t,Fixtures.id(99),100));assertTrue(c.acceptsResponse(t,Fixtures.id(3),100));finish(c,t)
        Fixtures.reject(BridgeError.INTERRUPTED) { c.reserve(Fixtures.job(),guard,100) }
    }
    @Test fun concurrentReservationsHaveOneWinnerWithoutGlobalArbiterClaim() {
        val c=SpeechJobCoordinator();val start=CountDownLatch(1);val won=AtomicInteger();val lost=AtomicInteger()
        val jobs=(20..27).map(Fixtures::job)
        val threads=jobs.map { r->Thread { start.await();try { c.reserve(r,guard,100);won.incrementAndGet() } catch(e: BridgeFailure) { assertEquals(BridgeError.BUSY,e.code);lost.incrementAndGet() } }.apply { start() } }
        start.countDown();threads.forEach { it.join(5000);assertFalse(it.isAlive) };assertEquals(1,won.get());assertEquals(7,lost.get())
    }
}
