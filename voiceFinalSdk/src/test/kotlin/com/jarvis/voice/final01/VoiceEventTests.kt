package com.jarvis.voice.final01
import org.junit.Test
import org.junit.Assert.*
class VoiceEventTests {
    @Test fun stableMetadataContainsNoIdentityTextAudioOrAlignmentClaim() {
        val event=VoiceEvent(VoiceEventType.FIRST_AUDIO,0,2,0)
        assertEquals(setOf("schema","type","completed","total","segment","presentedSamples","skipped"),event.metadata().keys)
        assertEquals(VOICE_EVENT_SCHEMA,event.metadata()["schema"]);assertNull(event.presentedSamples)
    }
    @Test fun completeMustAccountForEverySegmentIncludingExplicitOmissions() {
        assertThrows(IllegalArgumentException::class.java) { VoiceEvent(VoiceEventType.COMPLETE,1,2,null) }
        val done=VoiceEvent(VoiceEventType.COMPLETE,1,2,null,skipped=1);assertEquals(1,done.skipped)
    }
    @Test fun invalidCountersCannotBecomeProgress() {
        assertThrows(IllegalArgumentException::class.java) { VoiceEvent(VoiceEventType.PROGRESS,3,2,0) }
        assertThrows(IllegalArgumentException::class.java) { VoiceEvent(VoiceEventType.FIRST_AUDIO,0,2,0,0) }
    }
}
