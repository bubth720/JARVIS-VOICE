package com.jarvis.voice.speed
import com.jarvis.voice.pocket.*
import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
class NarrativeDeliveryTests {
    private fun plan()=LongFormPlanner.prepareWith(LongFormSpeechRequestV1(List(6) { "Le train arrive." }.joinToString(" "))) { PreparedText(it,longArrayOf(1,2,3),3) }
    @Test fun sixJobsPauseAndExplicitResumeKeepOrder() {
        val q=NarrativeQueue(plan());val seen=mutableListOf<Int>()
        q.run(CancellationToken()) { i,_->seen.add(i);if(i==1) assertTrue(q.requestPause()) }
        assertEquals(NarrativeState.PAUSED,q.state);assertEquals(2,q.completedJobs)
        try { q.run(CancellationToken()) { _,_->fail() };fail() } catch(_: IllegalStateException) {}
        q.run(CancellationToken(),true) { i,_->seen.add(i) };assertEquals((0..5).toList(),seen);assertEquals(NarrativeState.COMPLETED,q.state)
    }
    @Test fun concurrencyNeverAdmitsAnotherHeavyJob() {
        val q=NarrativeQueue(plan());val entered=CountDownLatch(1);val leave=CountDownLatch(1)
        val t=Thread { q.run(CancellationToken()) { i,_->if(i==0) { entered.countDown();check(leave.await(2,TimeUnit.SECONDS)) } } };t.start()
        assertTrue(entered.await(2,TimeUnit.SECONDS));try { q.run(CancellationToken(),true) { _,_->fail() };fail() } catch(_: IllegalStateException) {} finally { leave.countDown();t.join(2000) };assertFalse(t.isAlive)
    }
    @Test fun nativeUnknownCannotBeResetOrResumed() {
        val q=NarrativeQueue(plan());try { q.run(CancellationToken()) { _,_->throw UnknownNarrativeNative() };fail() } catch(_: UnknownNarrativeNative) {}
        assertEquals(NarrativeState.UNKNOWN_NATIVE,q.state);try { q.run(CancellationToken(),true) { _,_->fail() };fail() } catch(_: IllegalStateException) {}
    }
    @Test fun cancelledCurrentJobIsNotConsumed() {
        val q=NarrativeQueue(plan());val c=CancellationToken()
        try { q.run(c) { _,_->c.cancel() };fail() } catch(_: java.util.concurrent.CancellationException) {}
        assertEquals(0,q.completedJobs);assertEquals(NarrativeState.INTERRUPTED,q.state)
    }
    @Test fun poolReusesAndErases101JobsWithFixedBudget() {
        val p=PcmBufferPool()
        repeat(101) { p.borrow(480000).use { lease -> assertTrue(lease.buffer.all { it==0f });lease.buffer[0]=.7f } }
        assertEquals(1,p.arrayCreations);assertEquals(101,p.borrows);p.close()
        try { p.borrow(480000);fail() } catch(_: IllegalStateException) {}
    }
    @Test fun poolOwnershipAndUnknownShutdownAreFailClosed() {
        val p=PcmBufferPool();val lease=p.borrow(480000)
        try { p.borrow(480000);fail() } catch(_: IllegalStateException) {}
        p.close();lease.close();lease.close()
        try { lease.buffer;fail() } catch(_: IllegalStateException) {}
    }
    @Test fun pooledCapturePresentsOnlyValidPrefixAndNeverMutatesSource() {
        PcmBufferPool().use { pool -> PooledPcmCapture(pool).use { capture ->
            val a=floatArrayOf(.1f,.2f,.3f);capture.append(a)
            capture.present(true) { data,n-> assertEquals(3,n);assertArrayEquals(a,data.copyOf(n),0f) }
            assertEquals(.1f,a[0],0f)
        } }
    }
    @Test fun captureBudgetAndEosCannotBeBypassed() {
        PcmBufferPool().use { p -> PooledPcmCapture(p).use { c ->
            c.append(floatArrayOf(.2f));try { c.present(false) { _,_->fail() };fail() } catch(_: IllegalArgumentException) {}
            try { c.append(FloatArray(480000));fail() } catch(_: IllegalArgumentException) {}
        } }
    }
    @Test fun everyModeExcludesOldApiAndAutoRemembersSlowProducer() {
        for(api in 26..30) for(mode in SafePlaybackMode.entries) assertFalse(SafePlaybackPolicy.decide(mode,api,null).mayProbeStream)
        val scheduler=FirstAudioScheduler();assertTrue(scheduler.decision(SafePlaybackMode.AUTO_SAFE,35).mayProbeStream)
        scheduler.produced(2000.0,1.0);assertFalse(scheduler.decision(SafePlaybackMode.AUTO_SAFE,35).mayProbeStream)
        assertTrue(scheduler.decision(SafePlaybackMode.EXPERIMENTAL_STREAM,35).mayProbeStream)
    }
    @Test fun pooledProgressiveKeepsExactSamplesAndReleasesBothSlots() {
        PcmBufferPool().use { p ->
            repeat(6) {
                val source=FloatArray(72000) { (it%23)/100f }
                val output=object: ProgressiveOutput {
                    var n=0
                    override fun start(preload: FloatArray) { n+=preload.size }
                    override fun write(pcm: FloatArray) { n+=pcm.size }
                    override fun health() {}
                    override fun finish(totalSamples: Int) { check(n==totalSamples) }
                    override fun stop() {}
                    override fun close() {}
                }
                ProgressiveJob(CancellationToken(),{output},pool=p) { 0L }.use { job ->
                    job.offer(source);job.finishInto(true) { a,n,mode -> assertEquals(ProgressiveMode.STREAM,mode);assertArrayEquals(source,a.copyOf(n),0f) }
                }
            };assertEquals(2,p.arrayCreations)
        }
    }
}
