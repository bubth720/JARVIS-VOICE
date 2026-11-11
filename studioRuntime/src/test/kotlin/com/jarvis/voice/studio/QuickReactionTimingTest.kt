package com.jarvis.voice.studio

import org.junit.Assert.*
import org.junit.Test

class QuickReactionTimingTest {
    private fun allowed(elapsed:Long=1200,last:Long?=null,visible:Boolean=true,
        opted:Boolean=true,profile:Boolean=true,thermal:Boolean=true,
        clip:Boolean=true,ready:Boolean=false,playing:Boolean=false)=QuickReactionTiming.shouldPlay(
            elapsed,last,visible,opted,profile,thermal,clip,ready,playing)
    @Test fun permittedAtBoundary() { assertTrue(allowed());assertTrue(allowed(1300)) }
    @Test fun refusesBeforeThreshold() { assertFalse(allowed(1199));assertFalse(allowed(0)) }
    @Test fun refusesNegativeElapsed() = assertFalse(allowed(-1))
    @Test fun refusesUnseenGeneration() = assertFalse(allowed(visible=false))
    @Test fun refusesOptOut() = assertFalse(allowed(opted=false))
    @Test fun refusesStaleProfile() = assertFalse(allowed(profile=false))
    @Test fun refusesThermalPressure() = assertFalse(allowed(thermal=false))
    @Test fun refusesUnpreparedClip() = assertFalse(allowed(clip=false))
    @Test fun refusesFinalReady() = assertFalse(allowed(ready=true))
    @Test fun refusesConcurrentAudio() = assertFalse(allowed(playing=true))
    @Test fun enforcesCooldown() { assertFalse(allowed(last=19999));assertTrue(allowed(last=20000)) }
    @Test fun previousLongAgoAllowed() = assertTrue(allowed(last=45000))
    @Test fun onlyFixedHonestPhrases() { assertEquals(3,QuickReactionPhrase.entries.size);
        assertTrue(QuickReactionPhrase.entries.all { it.spoken.endsWith(".") && !it.spoken.contains("second") }) }
    @Test fun futureTimeDoesNotBypassOtherGates() = assertFalse(allowed(elapsed=60000,profile=false))
}
