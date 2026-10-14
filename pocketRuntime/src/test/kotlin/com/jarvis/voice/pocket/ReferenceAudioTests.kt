package com.jarvis.voice.pocket

import org.junit.Test
import org.junit.Assert.*
import java.nio.file.Files
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

class ReferenceAudioTests {
    @Test fun longExcerptsStayFiniteBeyondIntMultiplicationLimit() {
        for (rate in listOf(44100, 48000)) for (seconds in listOf(5, 10, 15)) {
            val input = FloatArray(rate * seconds) { (.1 * sin(2 * PI * 1000 * it / rate)).toFloat() }
            val pcm = ReferenceAudio.resample24k(input, rate)
            assertEquals(seconds * 24000, pcm.size)
            assertTrue(pcm.all { it.isFinite() })
            assertEquals(-23.01, ReferenceAudio.quality(pcm).rmsDb, .05)
            val root = Files.createTempDirectory("long-wav").toFile()
            try {
                val wav = java.io.File(root, "test.wav")
                ReferenceAudio.writeWav(wav, pcm)
                assertEquals(44L + seconds * 48000, wav.length())
            } finally { root.deleteRecursively() }
        }
    }
    @Test fun convertsStereoToMonoWithoutChangingGain() {
        assertArrayEquals(floatArrayOf(0f, .5f), ReferenceAudio.mono(floatArrayOf(1f,-1f,.25f,.75f),2), 0f)
        assertThrows(IllegalArgumentException::class.java) { ReferenceAudio.mono(floatArrayOf(Float.NaN),1) }
    }
    @Test fun resamplesWithAntiAliasingAndPreservesSpeechBand() {
        fun wave(hz: Int) = FloatArray(48000) { sin(2 * PI * hz * it / 48000).toFloat() * .5f }
        val low = ReferenceAudio.resample24k(wave(1000),48000)
        val high = ReferenceAudio.resample24k(wave(18000),48000)
        fun rms(a: FloatArray) = sqrt(a.drop(200).dropLast(200).sumOf { it.toDouble()*it } / (a.size-400))
        assertEquals(24000,low.size)
        assertEquals(.35355,rms(low),.005)
        assertTrue("Anti-alias suppression required",rms(high) < .005)
        val cancelled = assertThrows(java.util.concurrent.CancellationException::class.java) {
            ReferenceAudio.resample24k(wave(1000),48000) { true }
        }
        assertNotNull(cancelled)
    }
    @Test fun qualityDetectsSilenceAndClippingAndWavIsMono24k() {
        assertTrue(ReferenceAudio.quality(FloatArray(24000)).rmsDb < -100)
        assertEquals(1.0,ReferenceAudio.quality(FloatArray(24000) { 1f }).clippedFraction,0.0)
        val root = Files.createTempDirectory("wav-test").toFile()
        try {
            val wav = java.io.File(root,"test.wav"); ReferenceAudio.writeWav(wav,FloatArray(24000) { .25f })
            val header = ByteBuffer.wrap(wav.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(48044,wav.length().toInt()); assertEquals(1,header.getShort(22).toInt())
            assertEquals(24000,header.getInt(24)); assertEquals(16,header.getShort(34).toInt())
        } finally { root.deleteRecursively() }
    }
    @Test fun privateImportSurvivesRestartAndInvalidReplacementPreservesPrevious() {
        val root = Files.createTempDirectory("private-reference").toFile()
        try {
            val repo = ReferenceRepository(root)
            repo.import(ByteArrayInputStream(byteArrayOf(1,2,3)),{false}) { ReferenceInfo(60000,44100,2) }
            val original = repo.current()!!.first
            assertEquals(60000,ReferenceRepository(root).current()!!.second.durationMs)
            assertThrows(IllegalArgumentException::class.java) {
                repo.import(ByteArrayInputStream(byteArrayOf(5)),{false}) { throw IllegalArgumentException("corrupt") }
            }
            assertEquals(original,repo.current()!!.first)
            assertThrows(java.util.concurrent.CancellationException::class.java) {
                repo.import(ByteArrayInputStream(byteArrayOf(5)),{true}) { ReferenceInfo(60000,24000,1) }
            }
            assertEquals(original,repo.current()!!.first)
            repo.delete(); assertNull(ReferenceRepository(root).current())
        } finally { root.deleteRecursively() }
    }
    @Test fun failedOrCancelledExcerptPreservesPreviousAndLeavesNoTemporaryFile() {
        val root = Files.createTempDirectory("atomic-excerpt").toFile()
        try {
            val repo = ReferenceRepository(root)
            repo.saveExcerpt(FloatArray(120000) { .1f }, { false })
            val previous = repo.excerpt.readBytes()
            assertThrows(IllegalArgumentException::class.java) { repo.saveExcerpt(floatArrayOf(Float.NaN), { false }) }
            assertArrayEquals(previous, repo.excerpt.readBytes())
            var calls = 0
            assertThrows(java.util.concurrent.CancellationException::class.java) {
                repo.saveExcerpt(FloatArray(240000) { .2f }, { ++calls > 10 })
            }
            assertArrayEquals(previous, repo.excerpt.readBytes())
            assertFalse(java.io.File(root, "excerpt.pending").exists())
            assertThrows(java.util.concurrent.CancellationException::class.java) {
                repo.import(ByteArrayInputStream(byteArrayOf(1)), { calls > 10 }) { ReferenceInfo(12000,44100,2) }
            }
            repo.delete(); assertFalse(repo.excerpt.exists())
        } finally { root.deleteRecursively() }
    }
    @Test fun rejectsPathTraversalInvalidFormatsAndPretendCloning() {
        val root = Files.createTempDirectory("private-reference").toFile()
        try {
            java.io.File(root,"reference.json").writeText("""{"schema":1,"id":"../../escape"}""")
            assertThrows(IllegalArgumentException::class.java) { ReferenceRepository(root).current() }
            assertThrows(IllegalArgumentException::class.java) { ReferenceInfo(1000,24000,1).validate() }
            assertThrows(IllegalArgumentException::class.java) { ReferenceInfo(10000,44100,3).validate() }
            assertFalse(CloningCapability.available)
        } finally { root.deleteRecursively() }
    }
}
