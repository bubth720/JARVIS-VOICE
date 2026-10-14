package com.jarvis.voice.pocketprobe

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import com.jarvis.voice.pocket.*
import java.io.File
import kotlin.math.sin

/** Run on a physical ARM64 phone. CI compiles these tests, it cannot validate device audio. */
@RunWith(AndroidJUnit4::class)
class DeviceAudioTests {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private inline fun <reified T : Throwable> fails(block: () -> Unit) {
        try { block() } catch (t: Throwable) { assertTrue("Unexpected exception", t is T); return }
        fail("Expected ${T::class.java.simpleName}")
    }
    @Test fun testValidSyntheticMp3DecodesAndCorruptionIsRejected() {
        val root = File(instrumentation.targetContext.cacheDir, "synthetic-test").apply { mkdirs() }
        try {
            val valid = File(root,"valid.mp3")
            instrumentation.context.assets.open("synthetic.mp3").use { input -> valid.outputStream().use { input.copyTo(it) } }
            assertTrue(LocalMp3.inspect(valid).durationMs >= 5000)
            val pcm = LocalMp3.decode(valid,0,5000,CancellationToken())
            assertTrue(pcm.size in 117600..120000); assertTrue(pcm.all { it.isFinite() })
            assertTrue(ReferenceAudio.quality(pcm).rmsDb in -35.0..-10.0)
            val corrupt = File(root,"corrupt.mp3").apply { writeBytes(ByteArray(2048)) }
            fails<IllegalArgumentException> { LocalMp3.inspect(corrupt) }
            val token = CancellationToken().apply { cancel() }
            fails<java.util.concurrent.CancellationException> { LocalMp3.decode(valid,0,5000,token) }
        } finally { root.deleteRecursively() }
    }
    @Test fun testThreeSecondsOfContinuousDeferredAudioHasNoUnderruns() {
        val token = CancellationToken()
        PcmPlayer(token).use { player ->
            PcmDelivery(token::isCancelled, sink = player::write).use { queue ->
                repeat(10) { chunk -> queue.offer(FloatArray(7200) { i -> (sin((chunk*7200+i)*2*Math.PI*440/24000)*.03).toFloat() }) }
                queue.finish(); queue.await(); player.drain()
                assertEquals(0,queue.rebufferCount); assertEquals(0,player.underruns)
                assertTrue(player.firstFrameNs > 0)
            }
        }
    }
    @Test fun testStopCutsPlaybackBeforeFullDuration() {
        val token = CancellationToken()
        PcmPlayer(token).use { player ->
            val stopper = Thread { Thread.sleep(500); token.cancel(); player.stop() }.apply { start() }
            try { player.write(FloatArray(72000) { .02f }); player.drain(); fail("Cancellation expected") }
            catch (_: java.util.concurrent.CancellationException) { }
            finally { stopper.join(2000) }
        }
    }
    @Test fun testPackageHasNoInternetOrRecordingPermissionAndCloningStaysDisabled() {
        val context = instrumentation.targetContext
        val permissions = context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertFalse("android.permission.INTERNET" in permissions)
        assertFalse("android.permission.RECORD_AUDIO" in permissions)
        assertFalse(CloningCapability.available)
    }
}
