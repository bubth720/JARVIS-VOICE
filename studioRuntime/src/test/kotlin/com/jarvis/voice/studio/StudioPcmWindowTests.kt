package com.jarvis.voice.studio
import org.junit.Assert.*
import org.junit.Test

class StudioPcmWindowTests {
    @Test fun contiguousDecodedFramesSelectExactFiveSecondsAtStartAndAfterSeek() {
        for(rate in listOf(24000,44100,48000)) for(startMs in listOf(0L,1000L)) {
            val window=StudioPcmWindow(startMs,5000,rate)
            val selected=ArrayList<Int>();var cursor=0
            // A short first output buffer represents already-trimmed codec priming.
            for(size in listOf(47)+List(300) { 1152 }) {
                val samples=IntArray(size) { cursor+it }
                selected.addAll(window.next(size).map { samples[it] });cursor+=size
                if(window.complete) break
            }
            val first=(startMs*rate/1000).toInt()
            assertTrue(window.complete);assertEquals(5*rate,selected.size)
            assertEquals((first until first+5*rate).toList(),selected)
        }
    }
    @Test fun earlyEosAndEmptyBlocksCannotProduceACompleteOrPaddedRegion() {
        val window=StudioPcmWindow(0,5000,24000)
        assertTrue(window.next(0).isEmpty());assertEquals(100,window.next(100).count());assertFalse(window.complete)
        val later=StudioPcmWindow(1000,5000,24000)
        assertTrue(later.next(24000).isEmpty());assertFalse(later.complete)
        assertEquals(120000,later.next(120001).count());assertTrue(later.complete);assertTrue(later.next(100).isEmpty())
    }
}
