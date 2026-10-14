package com.jarvis.voice.pocket

import org.junit.Assert.*
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class PcmDeliveryTests {
    @Test fun deferredWaitsForCompleteSentenceAndPreservesEverySample() {
        val output = Collections.synchronizedList(mutableListOf<Float>())
        PcmDelivery({ false }, sink = { output.addAll(it.toList()) }).use { q ->
            q.offer(floatArrayOf(0.1f, 0.2f)); Thread.sleep(60)
            assertTrue(output.isEmpty())
            q.offer(floatArrayOf(0.3f)); q.finish(); q.await()
            assertEquals(listOf(0.1f, 0.2f, 0.3f), output.toList())
            assertEquals(3, q.peakSamples); assertEquals(0, q.rebufferCount)
        }
    }
    @Test fun adaptiveConsumerIsIndependentOfProducer() {
        val started = CountDownLatch(1); val release = CountDownLatch(1)
        PcmDelivery({ false }, true, { started.countDown(); release.await(2, TimeUnit.SECONDS) }).use { q ->
            q.offer(FloatArray(48000))
            assertTrue(started.await(2, TimeUnit.SECONDS))
            // A blocked playback sink must not block ONNX's offer().
            q.offer(FloatArray(48000)); q.finish(); release.countDown(); q.await()
        }
    }
    @Test fun slowProducerDefersPlaybackAndRaisesAdaptiveReserve() {
        val clock = java.util.concurrent.atomic.AtomicLong(1)
        val sound = CountDownLatch(1)
        PcmDelivery({ false }, true, { sound.countDown() }, nanoTime = clock::get).use { q ->
            clock.set(3_000_000_001L); q.offer(FloatArray(48000))
            assertFalse(sound.await(50, TimeUnit.MILLISECONDS)) // RTF 1.5; streaming must wait
            clock.set(6_000_000_001L); q.offer(FloatArray(24000))
            assertEquals(144000, q.requiredSamples) // twice the three-second producer stall
            assertFalse(sound.await(50, TimeUnit.MILLISECONDS))
            q.finish(); q.await(); assertEquals(0, sound.count)
        }
    }
    @Test fun stopDuringDeferredGenerationProducesNoSound() {
        val cancelled = AtomicBoolean(false); val played = AtomicBoolean(false)
        PcmDelivery(cancelled::get, sink = { played.set(true) }).use { q ->
            q.offer(FloatArray(48000)); cancelled.set(true)
            assertThrows(java.util.concurrent.CancellationException::class.java) { q.await() }
        }
        assertFalse(played.get())
    }
    @Test fun stopDuringPlaybackAndSinkFailureArePropagated() {
        val cancelled = AtomicBoolean(false)
        PcmDelivery(cancelled::get, sink = { cancelled.set(true) }).use { q ->
            q.offer(FloatArray(20)); q.offer(FloatArray(20)); q.finish()
            assertThrows(java.util.concurrent.CancellationException::class.java) { q.await() }
        }
        PcmDelivery({ false }, sink = { error("sink failure") }).use { q ->
            q.offer(FloatArray(20)); q.finish()
            assertThrows(IllegalStateException::class.java) { q.await() }
        }
    }
    @Test fun queueIsBoundedAndRejectsInvalidPcm() {
        PcmDelivery({ false }, sink = {}).use { q ->
            assertThrows(IllegalArgumentException::class.java) { q.offer(floatArrayOf(Float.NaN)) }
            q.offer(FloatArray(480000))
            assertThrows(IllegalArgumentException::class.java) { q.offer(FloatArray(1)) }
            q.finish(); q.await()
        }
    }
}
