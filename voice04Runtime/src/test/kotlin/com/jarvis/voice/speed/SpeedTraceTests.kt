package com.jarvis.voice.speed
import org.junit.Test
import org.junit.Assert.*
class SpeedTraceTests {
    @Test fun missingIsNullNotZeroAndExportContainsOnlyCounters() {
        var now=10L;val t=SpeedTrace { now };now+=1000000;t.first(SpeedPoint.ADMITTED)
        val j=t.json();assertTrue(j.getJSONObject("eventsMs").isNull("FIRST_PCM"))
        assertEquals(1.0,j.getJSONObject("eventsMs").getDouble("ADMITTED"),0.0)
        assertFalse(j.toString().contains("profile"));assertFalse(j.toString().contains("utterance"))
    }
    @Test fun firstEventIsNotOverwrittenAndDurationsAccumulate() {
        var n=0L;val t=SpeedTrace { n };t.timed(SpeedPoint.PACK_VERIFIED) { n+=1000000 }
        t.timed(SpeedPoint.PACK_VERIFIED) { n+=2000000 };t.chunk(24000);t.tokens(5);t.finish("COMPLETED")
        assertEquals(1.0,t.json().getJSONObject("eventsMs").getDouble("PACK_VERIFIED"),0.0)
        assertEquals(3.0,t.json().getJSONObject("durationsMs").getDouble("PACK_VERIFIED"),0.0)
        assertEquals(1.0,t.json().getDouble("pcmSeconds"),0.0)
    }
    @Test(expected=IllegalStateException::class) fun regressionRefused() { var n=100L;val t=SpeedTrace { n };n=0;t.first(SpeedPoint.ADMITTED) }
    @Test fun refusalAndCancellationNeverInventPlayback() { for(s in listOf("REFUSED","CANCELLED")) { val t=SpeedTrace();t.finish(s);assertFalse(t.has(SpeedPoint.PLAYBACK_COMPLETED));assertEquals(s,t.json().getString("outcome")) } }
}
