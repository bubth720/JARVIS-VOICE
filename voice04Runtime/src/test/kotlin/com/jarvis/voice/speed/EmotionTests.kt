package com.jarvis.voice.speed
import org.junit.Test
import org.junit.Assert.*
class EmotionTests {
    @Test fun noLabelCanEnableAnUnheardEmotion() {
        for(intent in EmotionalIntentV1.entries) {
            val cap=Pocket6LEmotionCapabilities.availability(intent)
            assertEquals(intent==EmotionalIntentV1.NEUTRAL,cap.selectable)
            if(intent!=EmotionalIntentV1.NEUTRAL) assertEquals(EffectCategory.UNSUPPORTED,cap.category)
        }
    }
    @Test fun legacyMappingNeverGrantsAcousticCapability() { assertEquals("SURPRISED",Pocket6LEmotionCapabilities.legacyMetadata(EmotionalIntentV1.SURPRISE));assertFalse(Pocket6LEmotionCapabilities.availability(EmotionalIntentV1.SURPRISE).selectable) }
    @Test(expected=IllegalArgumentException::class) fun surpriseRefusedBeforeRendering() { Pocket6LEmotionCapabilities.requireSupported(EmotionalIntentV1.SURPRISE) }
    @Test(expected=IllegalArgumentException::class) fun fakeIntensityRefusedEvenInNeutral() { Pocket6LEmotionCapabilities.requireSupported(EmotionalIntentV1.NEUTRAL,1) }
    @Test fun neutralReferenceRemainsAvailable() { Pocket6LEmotionCapabilities.requireSupported(EmotionalIntentV1.NEUTRAL) }
}
