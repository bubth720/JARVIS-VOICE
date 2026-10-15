package com.jarvis.voice.pocket

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.CancellationException

class PlaybackEvidenceTests {
    private fun failure(reason: String, action: () -> Unit) {
        try { action(); fail("Truncated or failed playback accepted") }
        catch(error: PlaybackFailure) { assertEquals(reason,error.reason) }
    }
    @Test fun silenceNonFinitePeakAndRmsAreMeasuredBeforeConversion() {
        val stats=PcmStatistics.measure(floatArrayOf(0f,0f,1f,-1f,Float.NaN,Float.POSITIVE_INFINITY))
        assertEquals(6,stats.samples); assertEquals(2,stats.nonFinite)
        assertEquals(1.0,stats.peak,0.0); assertEquals(kotlin.math.sqrt(.5),stats.rms,1e-12)
        assertEquals(100.0/3,stats.silencePercent,1e-12)
        assertEquals(100.0,PcmStatistics.measure(FloatArray(100)).silencePercent,0.0)
    }
    @Test fun shortAndLongUtterancesRequireAllFramesAndRealProgress() {
        val short=PlaybackEvidence(120,0)
        assertEquals(PlaybackStage.WAITING,short.observe(0,true,true,0))
        assertEquals(PlaybackStage.STARTED,short.observe(4,true,true,119))
        assertEquals(PlaybackStage.COMPLETED,short.observe(5,true,true,120))
        val full=PlaybackEvidence(480000,0)
        for(i in 0..19) full.observe(i*1000L,true,true,i*24000L)
        assertEquals(PlaybackStage.COMPLETED,full.observe(20000,true,true,480000))
    }
    @Test fun stalledStartPausedUninitializedAndTerminalCounterNeverPassOnElapsedTime() {
        failure("DEMARRAGE_BLOQUE") { PlaybackEvidence(24000,0).observe(2000,true,true,0) }
        failure("PLAY_STATE_INATTENDU") { PlaybackEvidence(24000,0).observe(0,true,false,0) }
        failure("NON_INITIALISE") { PlaybackEvidence(24000,0).observe(0,false,true,0) }
        val stuck=PlaybackEvidence(24000,0); stuck.observe(1000,true,true,23999)
        failure("COMPTEUR_STAGNANT") { stuck.observe(3000,true,true,23999) }
        failure("COMPTEUR_INCOHERENT") { PlaybackEvidence(120,0).observe(1,true,true,121) }
        val reset=PlaybackEvidence(120,0); reset.observe(1,true,true,10)
        failure("COMPTEUR_REINITIALISE") { reset.observe(2,true,true,0) }
    }
    @Test fun onlyAlreadyPresentedTimestampMayCorroborateFinalFrame() {
        // Timestamp validity in clock/launch epoch is checked by Android adapter before passing it here.
        val done=PlaybackEvidence(24000,0)
        assertEquals(PlaybackStage.COMPLETED,done.observe(1000,true,true,23999,24000))
        assertEquals("AudioTimestamp présenté",done.evidence)
        val stuck=PlaybackEvidence(24000,0); stuck.observe(1000,true,true,23999,23999)
        failure("COMPTEUR_STAGNANT") { stuck.observe(3000,true,true,23999,23999) }
    }
    @Test fun privateReplayPreservesExactPcmAndOldAudioAfterCancellationOrCorruption() {
        val root=Files.createTempDirectory("local-pcm-replay").toFile()
        try {
            val store=PrivatePcmStore(root); val token=CancellationToken()
            val pcm=floatArrayOf(-0f,0.000003f,-.3f,1.1f)
            store.save(pcm,token); assertTrue(store.exists())
            assertArrayEquals(pcm,PrivatePcmStore(root).load(token),0f)
            try { store.save(FloatArray(100),CancellationToken().also { it.cancel() }); fail("Cancelled save accepted") }
            catch(_: CancellationException) { }
            assertArrayEquals(pcm,store.load(token),0f)
            assertEquals(1,root.listFiles()!!.count { it.isDirectory })
            try { store.save(floatArrayOf(Float.NaN),token); fail("Non-finite audio cached") } catch(_: IllegalArgumentException) { }
            try { store.save(FloatArray(480001),token); fail("Unbounded audio cached") } catch(_: IllegalArgumentException) { }
            val data=root.listFiles()!!.first { it.isDirectory }.resolve("audio.pcm")
            val bytes=data.readBytes(); bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte(); data.writeBytes(bytes)
            try { store.load(token); fail("Corrupt audio replayed") } catch(_: IllegalArgumentException) { }
            store.delete(); assertFalse(store.exists()); assertEquals(0,root.listFiles()!!.size)
        } finally { root.deleteRecursively() }
    }
}
