package com.jarvis.voice.pocketprobe

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import com.jarvis.voice.pocket.*
import java.io.File
import kotlin.math.sin

/** Synthetic codec and AudioTrack checks; perceptive Xiaomi validation remains separate. */
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
    @Test fun testTwelveSecondFixturesAndOffsetExcerpts() {
        val root = File(instrumentation.targetContext.cacheDir, "matrix-test").apply { mkdirs() }
        try {
            for (name in listOf("synthetic", "mono", "stereo48")) {
                val file = File(root, "$name.mp3")
                instrumentation.context.assets.open("$name.mp3").use { input -> file.outputStream().use { input.copyTo(it) } }
                val repo = ReferenceRepository(File(root, name))
                file.inputStream().use { input -> repo.import(input, { false }) { candidate ->
                    LocalMp3.decode(candidate, 0, 5000, CancellationToken())
                    LocalMp3.inspect(candidate)
                } }
                assertNotNull(ReferenceRepository(File(root, name)).current())
                for ((start, duration) in listOf(0L to 5000L, 0L to 10000L, 2000L to 5000L)) {
                    val pcm = LocalMp3.decode(repo.current()!!.first, start, duration, CancellationToken())
                    assertTrue(pcm.all { it.isFinite() })
                    assertTrue(kotlin.math.abs(pcm.size - duration * 24) <= 2400)
                    val wav = File(root, "test.wav")
                    ReferenceAudio.writeWav(wav, pcm)
                    val header = java.nio.ByteBuffer.wrap(wav.readBytes()).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    assertEquals(24000, header.getInt(24)); assertEquals(1, header.getShort(22).toInt())
                    assertEquals(16, header.getShort(34).toInt()); assertEquals(44L + pcm.size * 2, wav.length())
                }
                fails<IllegalArgumentException> { LocalMp3.decode(file, 9000, 5000, CancellationToken()) }
                repo.delete(); assertNull(repo.current())
            }
        } finally { root.deleteRecursively() }
    }
    @Test fun testThreeSecondsOfContinuousDeferredAudioHasNoUnderruns() {
        val token = CancellationToken()
        PcmPlayer(token).use { player ->
            PcmDelivery(token::isCancelled, sink = player::write).use { queue ->
                repeat(10) { chunk -> queue.offer(FloatArray(7200) { i -> (sin((chunk*7200+i)*2*Math.PI*440/24000)*.03).toFloat() }) }
                queue.finish(); queue.await(); player.drain()
                assertEquals(0,queue.rebufferCount); assertEquals("AudioTrack ${player.underrunObservations}", 0,player.underruns)
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
