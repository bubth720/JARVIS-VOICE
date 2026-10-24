package com.jarvis.voice.expression
import com.jarvis.voice.pocket.CancellationToken
import org.junit.Test
import org.junit.Assert.*
class EmotionPcmTests {
    private val controller=VoiceEmotionController()
    private fun plan(style: VoiceEmotionIntent,intensity: Int=2,consent: Boolean=true,fallback: Boolean=false)=(controller.plan(style,intensity,consent,fallback) as EmotionPlanning.Ready).plan
    @Test fun neutralAndExplicitFallbackAreBitExactIncludingSignedZero() {
        val a=floatArrayOf(-0f,0f,.2f,-.8f,1.2f,Float.MIN_VALUE)
        for(p in listOf(plan(VoiceEmotionIntent.NEUTRAL),plan(VoiceEmotionIntent.CONCERN,consent=false,fallback=true))) {
            val b=BoundedPcmProsody.render(a,p,CancellationToken());assertEquals(0,b.changedSamples)
            a.indices.forEach { assertEquals(a[it].toRawBits(),b.pcm[it].toRawBits()) }
        }
    }
    @Test fun threeSoftwareStylesDifferAndKeepTimingAndSource() {
        val a=FloatArray(48000) { sinTone(it) };val original=a.copyOf();val results=mutableListOf<FloatArray>()
        for(style in listOf(VoiceEmotionIntent.JOY,VoiceEmotionIntent.SURPRISE,VoiceEmotionIntent.CALM)) {
            val r=BoundedPcmProsody.render(a,plan(style),CancellationToken())
            assertEquals(a.size,r.pcm.size);assertTrue(r.changedSamples>40000);assertTrue(r.maxAbsDifference>.05)
            assertTrue(r.pcm.all { it.isFinite() && kotlin.math.abs(it)<=.98f });assertTrue(r.outputRms<r.inputRms)
            assertFalse(r.cues.humanEmotionVerified);assertTrue(r.cues.cues.all { it.endSample<=a.size });results.add(r.pcm)
        }
        assertArrayEquals(original,a,0f);assertFalse(results[0].contentEquals(results[1]));assertFalse(results[1].contentEquals(results[2]))
    }
    @Test fun determinismAndIntensityZeroNeverInsertAudioOrLaughter() {
        val a=FloatArray(24000) { sinTone(it) };val p=plan(VoiceEmotionIntent.SURPRISE)
        assertArrayEquals(BoundedPcmProsody.render(a,p,CancellationToken()).pcm,BoundedPcmProsody.render(a,p,CancellationToken()).pcm,0f)
        assertArrayEquals(a,BoundedPcmProsody.render(a,plan(VoiceEmotionIntent.JOY,0),CancellationToken()).pcm,0f)
        assertTrue(controller.plan(VoiceEmotionIntent.LAUGH_OPTIONAL,2,true) is EmotionPlanning.Unsupported)
    }
    @Test fun lackOfConsentOrBackendCapabilityRefusesBeforeDsp() {
        assertTrue(controller.plan(VoiceEmotionIntent.JOY,2,false) is EmotionPlanning.Unsupported)
        for(style in VoiceEmotionIntent.entries) assertFalse(EmotionCapabilityMatrix.forIntent(style).nativeEmotion || EmotionCapabilityMatrix.forIntent(style).humanValidated)
        assertFalse(ExpressiveBackendSelection.enabled(ExpressiveBackendSelection.Candidate.QWEN3_TTS))
    }
    @Test fun clippingInputDoesNotCreateNewClippingInSoftwareModes() {
        val r=BoundedPcmProsody.render(floatArrayOf(-3f,2f,0f),plan(VoiceEmotionIntent.CALM),CancellationToken())
        assertTrue(r.pcm.all { kotlin.math.abs(it)<=.98f })
    }
    @Test fun invalidAndCancelledPcmCannotRender() {
        for(a in listOf(floatArrayOf(Float.NaN),FloatArray(480001),FloatArray(0))) { try { BoundedPcmProsody.render(a,plan(VoiceEmotionIntent.JOY),CancellationToken());fail() } catch(_: IllegalArgumentException) {} }
        val c=CancellationToken();c.cancel();try { BoundedPcmProsody.render(floatArrayOf(.2f),plan(VoiceEmotionIntent.JOY),c);fail() } catch(_: java.util.concurrent.CancellationException) {}
    }
    private fun sinTone(i: Int)=(kotlin.math.sin(i*2*kotlin.math.PI*440/24000)*.5).toFloat()
}
