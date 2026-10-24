package com.jarvis.voice.voicestudio
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jarvis.voice.pocket.*
import com.jarvis.voice.speed.*
import org.junit.Test
import org.junit.Before
import org.junit.After
import android.content.Intent
import android.os.Build
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Actual AudioTrack on an explicitly selected emulator. Synthetic public signals, not human audibility. */
@RunWith(AndroidJUnit4::class)
class Voice04AudioTests {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private var activity: StudioActivity?=null
    @Before fun foreground() {
        val i=InstrumentationRegistry.getInstrumentation()
        activity=i.startActivitySync(Intent(context,StudioActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as StudioActivity
        i.waitForIdleSync()
    }
    @After fun finishActivity() { InstrumentationRegistry.getInstrumentation().runOnMainSync { activity?.finish() } }
    private fun signal(n: Int)=FloatArray(n) { (kotlin.math.sin(it*2*kotlin.math.PI*440/24000)*.05).toFloat() }
    @Test fun completeDeferredSmallPhraseHasMeasuredPresentation() {
        val c=CancellationToken();val trace=SpeedTrace()
        PcmPlayer(context,c,speed=trace).use { p -> p.write(signal(12000));p.drain();assertTrue(p.completed);assertEquals(12000L,p.submitted) }
        assertTrue(trace.has(SpeedPoint.FIRST_WRITE));assertTrue(trace.has(SpeedPoint.PRESENTATION_ESTIMATED));assertTrue(trace.has(SpeedPoint.PLAYBACK_COMPLETED))
    }
    @Test fun devicePolicyStreamsOrSafelyDefersAllFrames() {
        val c=CancellationToken();val trace=SpeedTrace();val source=signal(72000);val copy=source.copyOf()
        ProgressiveJob(c,{Voice04StreamOutput(context,c,trace)},48000,devicePermitted=Build.VERSION.SDK_INT>=31) { 0L }.use { job ->
            job.offer(source.copyOfRange(0,48000));job.offer(source.copyOfRange(48000,72000))
            val complete=job.finish(true)
            assertArrayEquals(copy,complete,0f)
            if(Build.VERSION.SDK_INT>=31) { assertEquals(ProgressiveMode.STREAM,job.mode);assertTrue(job.completed) }
            else { assertEquals(ProgressiveMode.DEFERRED,job.mode);PcmPlayer(context,c,speed=trace).use { it.write(complete);it.drain();assertTrue(it.completed) } }
        }
        assertArrayEquals(copy,source,0f);assertTrue(trace.has(SpeedPoint.PLAYBACK_COMPLETED))
    }
    @Test fun stopInterruptsRealStreamAndNeverClaimsCompletion() {
        val c=CancellationToken();val trace=SpeedTrace()
        ProgressiveJob(c,{Voice04StreamOutput(context,c,trace)},24000,devicePermitted=Build.VERSION.SDK_INT>=31) { 0L }.use { job ->
            job.offer(signal(24000))
            if(Build.VERSION.SDK_INT<31) { assertEquals(ProgressiveMode.DEFERRED,job.mode);assertFalse(trace.has(SpeedPoint.FIRST_WRITE));return@use }
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
    @Test fun stopInterruptsActualDeferredTrackWithoutFalseCompletion() {
        val c=CancellationToken();val trace=SpeedTrace();val error=java.util.concurrent.atomic.AtomicReference<Throwable?>()
        PcmPlayer(context,c,speed=trace).use { player ->
            player.write(signal(72000))
            val thread=Thread { try { player.drain() } catch(t: Throwable) { error.set(t) } };thread.start()
            val until=System.nanoTime()+3000000000L
            while(!trace.has(SpeedPoint.PRESENTATION_ESTIMATED) && System.nanoTime()<until) Thread.sleep(10)
            assertTrue(trace.has(SpeedPoint.PRESENTATION_ESTIMATED));player.stop();thread.join(3000)
            assertFalse(thread.isAlive);assertNotNull(error.get());assertFalse(player.completed)
        }
        assertFalse(trace.has(SpeedPoint.PLAYBACK_COMPLETED))
    }

    private fun waitControllerIdle(c: StudioController) {
        val until=System.nanoTime()+5000000000L
        while(c.busy && System.nanoTime()<until) Thread.sleep(10)
        assertFalse(c.busy)
    }
    @Test fun activityDestructionStopsItsRealDeferredSignal() {
        val i=InstrumentationRegistry.getInstrumentation();val c=requireNotNull(activity).controller
        i.runOnMainSync { c.testSignal() }
        assertTrue(c.busy)
        i.runOnMainSync { activity!!.finish() }
        waitControllerIdle(c)
        assertFalse(c.message.startsWith("Signal de test terminé"))
    }
    @Test fun rotationKeepsTheSameControllerAndRemainsBounded() {
        val i=InstrumentationRegistry.getInstrumentation();val c=requireNotNull(activity).controller
        i.runOnMainSync { c.testSignal();activity!!.recreate() }
        waitControllerIdle(c)
        assertTrue(c.message.startsWith("Signal de test terminé"))
    }

    @Test fun actualAudioFocusLossInterruptsDeferredOutput() {
        val c=CancellationToken();val trace=SpeedTrace();val error=java.util.concurrent.atomic.AtomicReference<Throwable?>()
        val manager=context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        val attributes=android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build()
        val competing=android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener({},android.os.Handler(android.os.Looper.getMainLooper())).build()
        try {
            PcmPlayer(context,c,speed=trace).use { player ->
                player.write(signal(72000))
                val thread=Thread { try { player.drain() } catch(t: Throwable) { error.set(t) } };thread.start()
                val until=System.nanoTime()+3000000000L
                while(!trace.has(SpeedPoint.PRESENTATION_ESTIMATED) && System.nanoTime()<until) Thread.sleep(10)
                assertTrue(trace.has(SpeedPoint.PRESENTATION_ESTIMATED))
                assertEquals(android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED,manager.requestAudioFocus(competing))
                thread.join(2000)
                if(thread.isAlive) { player.stop();thread.join(2000);fail("Focus loss did not interrupt output") }
                assertTrue(error.get() is PlaybackFailure);assertEquals("FOCUS_PERDU",(error.get() as PlaybackFailure).reason);assertFalse(player.completed)
            }
        } finally { manager.abandonAudioFocusRequest(competing) }
    }
    @Test fun apkIdentityIsIndependentAndHasNoNetworkOrMicrophonePermission() {
        assertTrue(context.packageName in setOf("com.jarvis.voice.studio.voice04","com.jarvis.voice.studio.voice05","com.jarvis.voice.studio.voice06","com.jarvis.voice.studio.voice07"))
        val info=context.packageManager.getPackageInfo(context.packageName,android.content.pm.PackageManager.GET_PERMISSIONS)
        assertTrue(info.requestedPermissions.orEmpty().none { it in setOf("android.permission.INTERNET","android.permission.RECORD_AUDIO","android.permission.READ_EXTERNAL_STORAGE","android.permission.MANAGE_EXTERNAL_STORAGE") })
    }

    @Test fun oversizedPasteIsRejectedWholeAndPreservesReplacedSelection() {
        val i=InstrumentationRegistry.getInstrumentation();val rejected=java.util.concurrent.atomic.AtomicInteger()
        i.runOnMainSync {
            val input=android.widget.EditText(requireNotNull(activity));input.setText("Texte déjà validé.")
            input.filters=arrayOf(Voice04LongTextFilter { rejected.incrementAndGet() })
            input.text.replace(0,input.length(),"X".repeat(1801))
            assertEquals("Texte déjà validé.",input.text.toString());assertEquals(1,rejected.get())
            input.text.append(" Suite.");assertEquals("Texte déjà validé. Suite.",input.text.toString())
        }
    }


}
