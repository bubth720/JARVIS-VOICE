package com.jarvis.voice.speech

import org.junit.Assert.*
import org.junit.Test

class SpeechRouterTest {
    private class Engine(var result: SpeechResult = SpeechResult.Accepted) : SpeechEngine {
        override val maxTextLength = 4000
        var calls = 0
        var stopped = false
        var closed = false
        override fun speak(request: SpeechRequest): SpeechResult { calls++; return result }
        override fun stop() { stopped = true }
        override fun close() { closed = true }
    }
    @Test fun localModeDoesNotUseCloud() {
        val local = Engine(); val cloud = Engine()
        assertEquals(SpeechResult.Accepted, SpeechRouter(local, cloud) { true }.speak(SpeechRequest("Bonjour")))
        assertEquals(1, local.calls); assertEquals(0, cloud.calls)
    }
    @Test fun offlineHybridUsesLocal() {
        val local = Engine(); val cloud = Engine()
        SpeechRouter(local, cloud).speak(SpeechRequest("Bonjour"), SpeechMode.HYBRID)
        assertEquals(1, local.calls); assertEquals(0, cloud.calls)
    }
    @Test fun onlineHybridUsesCloud() {
        val local = Engine(); val cloud = Engine()
        SpeechRouter(local, cloud) { true }.speak(SpeechRequest("Bonjour"), SpeechMode.HYBRID)
        assertEquals(0, local.calls); assertEquals(1, cloud.calls)
    }
    @Test fun cloudFailureBeforeAcceptanceFallsBack() {
        val local = Engine(); val cloud = Engine(SpeechResult.Unavailable)
        assertEquals(SpeechResult.Accepted, SpeechRouter(local, cloud) { true }.speak(SpeechRequest("Bonjour"), SpeechMode.HYBRID))
        assertEquals(1, local.calls)
    }
    @Test fun invalidCloudRequestDoesNotFallBack() {
        val local = Engine(); val cloud = Engine(SpeechResult.InvalidText)
        assertEquals(SpeechResult.InvalidText, SpeechRouter(local, cloud) { true }.speak(SpeechRequest("Bonjour"), SpeechMode.HYBRID))
        assertEquals(0, local.calls)
    }
    @Test fun explicitCloudDoesNotSilentlyUseLocal() {
        val local = Engine()
        assertEquals(SpeechResult.Unavailable, SpeechRouter(local).speak(SpeechRequest("Bonjour"), SpeechMode.CLOUD))
        assertEquals(0, local.calls)
    }
    @Test fun blankTextDoesNotReachAnyEngine() {
        val local = Engine()
        assertEquals(SpeechResult.InvalidText, SpeechRouter(local).speak(SpeechRequest("  ")))
        assertEquals(0, local.calls)
    }
    @Test fun lifecycleReleasesBothEngines() {
        val local = Engine(); val cloud = Engine(); val router = SpeechRouter(local, cloud)
        router.stop(); router.close()
        assertTrue(local.stopped && cloud.stopped && local.closed && cloud.closed)
    }
}
