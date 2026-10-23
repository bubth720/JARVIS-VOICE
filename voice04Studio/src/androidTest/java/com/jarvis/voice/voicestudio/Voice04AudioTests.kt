package com.jarvis.voice.voicestudio
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jarvis.voice.pocket.*
import com.jarvis.voice.speed.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Actual AudioTrack on an explicitly selected emulator. Synthetic public signals, not human audibility. */
@RunWith(AndroidJUnit4::class)
class Voice04AudioTests {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun signal(n: Int)=FloatArray(n) { (kotlin.math.sin(it*2*kotlin.math.PI*440/24000)*.05).toFloat() }
    @Test fun completeDeferredSmallPhraseHasMeasuredPresentation() {
        val c=CancellationToken();val trace=SpeedTrace()
        PcmPlayer(context,c,speed=trace).use { p -> p.write(signal(12000));p.drain();assertTrue(p.completed);assertEquals(12000L,p.submitted) }
        assertTrue(trace.has(SpeedPoint.FIRST_WRITE));assertTrue(trace.has(SpeedPoint.PRESENTATION_ESTIMATED));assertTrue(trace.has(SpeedPoint.PLAYBACK_COMPLETED))
    }
    @Test fun realStreamPresentsAllFramesWithoutTransformingSource() {
        val c=CancellationToken();val trace=SpeedTrace();val source=signal(72000);val copy=source.copyOf()
        ProgressiveJob(c,{Voice04StreamOutput(context,c,trace)},48000) { 0L }.use { job ->
            job.offer(source.copyOfRange(0,48000));job.offer(source.copyOfRange(48000,72000))
            val complete=job.finish(true)
            assertEquals(ProgressiveMode.STREAM,job.mode);assertTrue(job.completed);assertArrayEquals(copy,complete,0f)
        }
        assertArrayEquals(copy,source,0f);assertTrue(trace.has(SpeedPoint.PLAYBACK_COMPLETED))
    }
    @Test fun stopInterruptsRealStreamAndNeverClaimsCompletion() {
        val c=CancellationToken();val trace=SpeedTrace()
        ProgressiveJob(c,{Voice04StreamOutput(context,c,trace)},24000) { 0L }.use { job ->
            job.offer(signal(24000))
            val until=System.nanoTime()+3000000000L
            while(!trace.has(SpeedPoint.FIRST_WRITE) && System.nanoTime()<until) Thread.sleep(10)
            assertTrue(trace.has(SpeedPoint.FIRST_WRITE));job.stop();assertTrue(c.isCancelled());assertFalse(job.completed)
        }
        assertFalse(trace.has(SpeedPoint.PLAYBACK_COMPLETED))
    }
    @Test fun invalidPcmNeverCreatesAudioTrack() {
        val trace=SpeedTrace();val c=CancellationToken()
        ProgressiveJob(c,{Voice04StreamOutput(context,c,trace)}) { 0L }.use { job ->
            try { job.offer(floatArrayOf(Float.NaN));fail() } catch(_: IllegalArgumentException) {}
        }
        assertFalse(trace.has(SpeedPoint.FIRST_WRITE))
    }
    @Test fun cancellationBeforeStartDoesNotWrite() {
        val trace=SpeedTrace();val c=CancellationToken();c.cancel()
        Voice04StreamOutput(context,c,trace).use { p ->
            try { p.start(signal(24000));fail() } catch(_: java.util.concurrent.CancellationException) {}
        };assertFalse(trace.has(SpeedPoint.FIRST_WRITE))
    }
    @Test fun slowProducerSelectsDeferredWithoutCreatingTrack() {
        var now=0L;val trace=SpeedTrace();val c=CancellationToken()
        ProgressiveJob(c,{Voice04StreamOutput(context,c,trace)},24000) { now }.use { job ->
            now=5000000000L;job.offer(signal(24000));job.finish(true);assertEquals(ProgressiveMode.DEFERRED,job.mode)
        };assertFalse(trace.has(SpeedPoint.FIRST_WRITE))
    }
}
