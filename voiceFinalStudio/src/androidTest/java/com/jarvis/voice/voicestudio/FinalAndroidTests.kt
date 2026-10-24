package com.jarvis.voice.voicestudio
import android.content.Intent
import android.content.pm.PackageManager
import android.text.SpannableString
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jarvis.voice.final01.*
import com.jarvis.voice.pocket.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.io.File
@RunWith(AndroidJUnit4::class)
class FinalAndroidTests {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private var activity: FinalActivity?=null
    @Before fun foreground() { activity=instrumentation.startActivitySync(Intent(context,FinalActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as FinalActivity;instrumentation.waitForIdleSync() }
    @After fun finish() { instrumentation.runOnMainSync { activity?.finish() };instrumentation.waitForIdleSync() }
    private fun signal(n: Int)=FloatArray(n) { (.05*kotlin.math.sin(it*2*kotlin.math.PI*440/24000)).toFloat() }
    @Test fun independentIdentityHasNoNetworkOrRecordingPermission() {
        assertEquals("com.jarvis.voice.studio.final01",context.packageName)
        assertEquals(PackageManager.PERMISSION_DENIED,context.checkSelfPermission("android.permission.INTERNET"))
        assertEquals(PackageManager.PERMISSION_DENIED,context.checkSelfPermission("android.permission.RECORD_AUDIO"))
    }
    @Test fun actualAudioTrackFinishesSmallSentenceAndCanReplayPrivateFloatPcm() {
        val cache=LocalAudioCache(File(context.noBackupFilesDir,"fixture-cache"));val token=CancellationToken();val pcm=signal(12000)
        cache.save("B","public-fixture",pcm,token)
        repeat(2) { val data=cache.load("B","public-fixture",token);PcmPlayer(context,token).use { it.write(data);it.drain();assertTrue(it.completed);assertEquals(12000L,it.submitted) };data.fill(0f) }
        cache.clear()
    }
    @Test fun floatAudioTrackCompletesWithoutPcm16Conversion() {
        PcmPlayer(context,CancellationToken(),floatOutput=true).use { it.write(signal(12000));it.drain();assertTrue(it.completed);assertEquals(12000L,it.submitted) }
    }
    @Test fun stopDuringRealAudioDoesNotReportCompletedOrDiscardCachedPcm() {
        val token=CancellationToken();val began=CountDownLatch(1);val stopped=CountDownLatch(1);val cache=LocalAudioCache(File(context.noBackupFilesDir,"fixture-stop"));cache.save("B","public-fixture",signal(120000),token)
        PcmPlayer(context,token,onStarted={began.countDown()}).use { player ->
            val thread=Thread { try { player.write(signal(120000));player.drain() } catch(_: java.util.concurrent.CancellationException) {} finally { stopped.countDown() } };thread.start()
            assertTrue(began.await(8,TimeUnit.SECONDS));player.stop();assertTrue(stopped.await(3,TimeUnit.SECONDS));thread.join(1000);assertFalse(thread.isAlive);assertFalse(player.completed)
        }
        assertEquals(120000,cache.load("B","public-fixture",CancellationToken()).size);cache.clear()
    }
    @Test fun studioClearAudioAlsoErasesFinalDerivedWavs() {
        val app=context.applicationContext as FinalApplication
        val cache=LocalAudioCache(File(context.noBackupFilesDir,"final01-audio"));cache.save("B","fixture",signal(12000),CancellationToken())
        val id=app.controller.vault.create("Fixture publique","test synthétique",true)
        instrumentation.runOnMainSync { app.controller.clearAudio(id) }
        val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5)
        while(app.controller.busy && System.nanoTime()<deadline) Thread.sleep(10)
        assertFalse(app.controller.busy);assertFalse(File(context.noBackupFilesDir,"final01-audio/B.wav").exists())
    }
    @Test fun oversizedPasteRefusesWholeReplacementAndDoesNotDeleteSelectedText() {
        var refused=false;val filter=FinalTextFilter { refused=true };val existing=SpannableString("Texte conservé.")
        assertEquals(existing.toString(),filter.filter("x".repeat(200001),0,200001,existing,0,existing.length).toString());assertTrue(refused)
    }
    @Test fun activityBackgroundCancelsExistingWorkerWithoutStartingAnEngine() {
        val app=context.applicationContext as FinalApplication;val started=CountDownLatch(1);val ended=CountDownLatch(1)
        instrumentation.runOnMainSync { assertTrue(app.controller.run("Fixture de cancellation") { c -> try { started.countDown();while(true) { c.check();Thread.sleep(10) };"unreachable" } finally { ended.countDown() } }) }
        assertTrue(started.await(3,TimeUnit.SECONDS));instrumentation.runOnMainSync { activity!!.finish() };assertTrue(ended.await(3,TimeUnit.SECONDS))
    }
}
