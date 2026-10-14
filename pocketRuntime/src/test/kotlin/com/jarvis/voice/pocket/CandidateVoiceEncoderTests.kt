package com.jarvis.voice.pocket

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CancellationException
import kotlin.math.abs

class CandidateVoiceEncoderTests {
    @Test fun preCancelledCandidateNeverLoadsModels() {
        val token = CancellationToken().also { it.cancel() }
        try {
            CandidateVoiceEncoder(File("unavailable-models"), token).use { error("Cancelled candidate loaded") }
            fail("Cancellation expected")
        } catch (_: CancellationException) { }
    }

    @Test fun realEncoderAndAll18StatesMatchIndependentOfficialGolden() {
        val root = System.getenv("POCKET_CLONING_MODEL_DIR").orEmpty()
        val fixture = System.getenv("POCKET_CLONING_FIXTURE_DIR").orEmpty()
        // Gated weights are tested in the private cloud only, never downloaded by public CI.
        assumeTrue("Private validated candidate and synthetic golden fixture required", root.isNotBlank() && fixture.isNotBlank())
        val bytes = File(fixture, "synthetic-pcm.f32").readBytes()
        val floats = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        val pcm = FloatArray(floats.remaining()).also { floats.get(it) }
        val expected = VoiceState.read(File(fixture, "synthetic-state.bin"), "synthetic")
        val cancel = CancellationToken()
        CandidateVoiceEncoder(File(root), cancel).use { encoder ->
            val actual = encoder.encode(pcm, cancel)
            assertEquals(18, actual.size)
            assertEquals(expected.keys, actual.keys)
            for ((key, reference) in expected) {
                val value = requireNotNull(actual[key])
                assertEquals(reference.dtype, value.dtype)
                assertArrayEquals(reference.shape, value.shape)
                if (reference.dtype == 1) assertArrayEquals(reference.bytes, value.bytes)
                else {
                    val py = ByteBuffer.wrap(reference.bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
                    val jvm = ByteBuffer.wrap(value.bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
                    while (py.hasRemaining()) {
                        val target = py.get(); val output = jvm.get()
                        assertTrue("FP32 cache parity failed: $key", output.isFinite() && abs(output-target) <= 2e-4f + 2e-4f * abs(target))
                    }
                }
            }
            cancel.cancel()
            try { encoder.encode(pcm, cancel); fail("Cancellation expected") }
            catch (_: CancellationException) { }
        }
    }
}
