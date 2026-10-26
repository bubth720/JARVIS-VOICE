package com.jarvis.voice.voicestudio
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jarvis.voice.fusion02.*
import com.jarvis.voice.pocket.CancellationToken
import org.junit.Test
import org.junit.Before
import org.junit.After
import android.content.Intent
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Synthetic samples through the existing real AudioTrack player; no voice/model/CORE authority. */
@RunWith(AndroidJUnit4::class)
class Fusion02AudioTests {
    private var activity: FinalActivity?=null
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    @Before fun foreground() {
        activity=instrumentation.startActivitySync(Intent(context,FinalActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as FinalActivity
        instrumentation.waitForIdleSync()
    }
    @After fun finish() { instrumentation.runOnMainSync { activity?.finish() };instrumentation.waitForIdleSync() }
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun key()=AudioKey(UUID.randomUUID().toString(),UUID.randomUUID().toString(),1)
    private fun signal(n: Int)=FloatArray(n) { (.05*kotlin.math.sin(it*2*kotlin.math.PI*440/24000)).toFloat() }
    @Test fun actualWriteAndEstimatedPresentationCompleteTwoShortSegments() {
        val proxy=LocalProxy();val j=proxy.prepare(key(),2,ObservationOrigin.ANDROID_SYNTHETIC_AUDIO_TRACK)
        proxy.execute(j,SegmentRenderer { _,_,cb -> cb(12000);RenderedSegment(signal(12000),true) },
            SegmentPlayback { pcm,stop,write,playing ->
                stop.check();PcmPlayer(context,CancellationToken(),onStarted={playing()}).use { player ->
                    player.write(pcm);write(player.submitted.toInt());player.drain()
                    PresentedSegment(player.submitted.toInt(),player.completed)
                }
            })
        val events=j.timeline.snapshot()
        assertEquals(AudioStage.Finished,j.timeline.stage)
        assertEquals(2,events.count { it.stage==AudioStage.AudioTrackWrite })
        assertEquals(2,events.count { it.stage==AudioStage.PlayingEstimated })
        assertTrue(events.all { it.metadata()["humanAudibilityVerified"]==false })
        assertTrue(events.zipWithNext().all { (a,b) -> b.elapsedNs>=a.elapsedNs })
    }
    @Test fun actualAudioStopRejectsLateSuccessAndPreservesCorrelation() {
        val k=key();val t=AudioTimeline(k,1,ObservationOrigin.ANDROID_SYNTHETIC_AUDIO_TRACK)
        t.queued();t.generating(k,0);t.pcm(k,0,120000)
        val began=CountDownLatch(1);val ended=CountDownLatch(1)
        PcmPlayer(context,CancellationToken(),onStarted={t.playing(k,0);began.countDown()}).use { player ->
            val worker=Thread { try { player.write(signal(120000));t.write(k,0,player.submitted.toInt());player.drain() }
                catch(_: java.util.concurrent.CancellationException) {} finally { ended.countDown() } }
            worker.start();assertTrue(began.await(8,TimeUnit.SECONDS));t.stop();player.stop()
            assertTrue(ended.await(3,TimeUnit.SECONDS));worker.join(1000);assertFalse(worker.isAlive);assertFalse(player.completed)
            assertThrows(AudioProtocolException::class.java){t.segmentFinished(k,0,120000,true)}
            assertEquals(AudioStage.Cancelled,t.stage)
        }
    }
    @Test fun nonfinitePcmRejectedByProxyAndRealPlayerWithoutFinished() {
        PcmPlayer(context,CancellationToken()).use { player ->
            assertThrows(IllegalArgumentException::class.java){player.write(floatArrayOf(Float.NaN))}
            assertEquals(0L,player.submitted);assertFalse(player.completed)
        }
        val proxy=LocalProxy();val j=proxy.prepare(key(),1,ObservationOrigin.ANDROID_SYNTHETIC_AUDIO_TRACK)
        proxy.execute(j,SegmentRenderer { _,_,cb -> cb(1);RenderedSegment(floatArrayOf(Float.NaN),true) },
            SegmentPlayback { pcm,_,write,_ -> PcmPlayer(context,CancellationToken()).use { it.write(pcm);write(it.submitted.toInt());PresentedSegment(1,it.completed) } })
        assertEquals(AudioStage.Failed,j.timeline.stage);assertFalse(j.timeline.snapshot().any { it.stage==AudioStage.AudioTrackWrite })
    }
}
