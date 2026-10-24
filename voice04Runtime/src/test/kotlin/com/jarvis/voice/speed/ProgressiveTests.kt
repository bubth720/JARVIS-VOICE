package com.jarvis.voice.speed
import com.jarvis.voice.pocket.CancellationToken
import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.atomic.AtomicBoolean
class ProgressiveTests {
    private class Sink(val fail: Boolean=false): ProgressiveOutput {
        val samples=mutableListOf<Float>();var closed=false;var stopped=false
        override fun start(preload: FloatArray) { samples.addAll(preload.toList()) }
        override fun write(pcm: FloatArray) { if(fail) error("DEVICE_FAILURE");samples.addAll(pcm.toList()) }
        override fun health() {}
        override fun finish(totalSamples: Int) { check(totalSamples==samples.size) }
        override fun stop() { stopped=true }
        override fun close() { closed=true }
    }
    @Test fun rapidProducerKeepsEverySampleAcross101Cycles() {
        repeat(101) {
            val sink=Sink();val c=CancellationToken()
            ProgressiveJob(c,{sink},24000) { 0L }.use { job ->
                val input=FloatArray(72000) { (it%137)/200f }
                for(chunk in input.toList().chunked(12000)) job.offer(chunk.toFloatArray())
                val out=job.finish(true)
                assertArrayEquals(input,out,0f);assertArrayEquals(input,sink.samples.toFloatArray(),0f)
                assertEquals(ProgressiveMode.STREAM,job.mode);assertTrue(job.completed);assertTrue(job.peakQueuedSamples<=96000)
            };assertTrue(sink.closed)
        }
    }
    @Test fun slowProducerFallsBackBeforeOpeningOutput() {
        var n=0L;var opened=false
        ProgressiveJob(CancellationToken(),{ opened=true;Sink() },24000) { n }.use { job ->
            n=5000000000L;job.offer(FloatArray(24000));assertEquals(ProgressiveMode.DEFERRED,job.mode)
            assertEquals(24000,job.finish(true).size);assertFalse(opened);assertFalse(job.started)
        }
    }
    @Test fun smallPhraseUsesCompleteDeferredPath() {
        ProgressiveJob(CancellationToken(),{ error("must not open") }).use { job -> job.offer(FloatArray(100));assertEquals(100,job.finish(true).size);assertEquals(ProgressiveMode.DEFERRED,job.mode) }
    }
    @Test fun failureMidStreamCannotBecomeDeferredSuccess() {
        val c=CancellationToken();val sink=Sink(true);val job=ProgressiveJob(c,{sink},24000) { 0L }
        try {
            job.offer(FloatArray(24000));job.offer(FloatArray(24000))
            try { job.finish(true);fail() } catch(_: Exception) {}
            assertEquals(ProgressiveMode.STREAM,job.mode);assertFalse(job.completed)
        } finally { job.close() };assertTrue(sink.closed)
    }
    @Test fun cancelledBlockedProducerWakesAndDoesNotLeakThread() {
        val ring=BoundedPcmPipe(24000);val c=CancellationToken();val stopped=AtomicBoolean()
        val worker=Thread { try { ring.offer(FloatArray(48000),c) } catch(_: Exception) { stopped.set(true) } }
        worker.start();Thread.sleep(30);c.cancel();ring.close();worker.join(2000);assertFalse(worker.isAlive);assertTrue(stopped.get())
    }
    @Test(expected=IllegalArgumentException::class) fun invalidSampleRefusedBeforeQueue() { BoundedPcmPipe().use { it.offer(floatArrayOf(Float.NaN),CancellationToken()) } }
    @Test(expected=IllegalArgumentException::class) fun perJobBudgetCannotBeChainedAway() { ProgressiveJob(CancellationToken(),{Sink()}).use { it.offer(FloatArray(480001)) } }
    @Test fun missingEosIsNotSuccess() { ProgressiveJob(CancellationToken(),{Sink()}).use { job -> job.offer(FloatArray(50));try { job.finish(false);fail() } catch(_: IllegalArgumentException) {};assertFalse(job.completed) } }
    @Test fun unknownAudioCloseIsQuarantinedRatherThanReportedComplete() {
        val c=CancellationToken()
        val output=object: ProgressiveOutput {
            override fun start(preload: FloatArray) {}
            override fun write(pcm: FloatArray) {}
            override fun health() {}
            override fun finish(totalSamples: Int) {}
            override fun stop() {}
            override fun close() { error("NATIVE_CLOSE_UNKNOWN") }
        }
        val job=ProgressiveJob(c,{output},24000) { 0L }
        try { job.offer(FloatArray(24000));try { job.finish(true);fail() } catch(_: Exception) {};assertTrue(job.closeUnknown);assertFalse(job.completed) }
        finally { job.close() }
    }

    @Test fun unsupportedDeviceAlwaysFallsBackBeforeAnyWriteEvenWhenFast() {
        ProgressiveJob(CancellationToken(),{ error("unsupported must not open") },24000,devicePermitted=false) { 0L }.use { job ->
            job.offer(FloatArray(24000));assertEquals(ProgressiveMode.DEFERRED,job.mode);assertEquals(24000,job.finish(true).size);assertFalse(job.started)
        }
    }

}
