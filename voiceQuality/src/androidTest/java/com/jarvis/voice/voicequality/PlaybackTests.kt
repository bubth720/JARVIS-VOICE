package com.jarvis.voice.voicequality

import android.content.Intent
import android.media.*
import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jarvis.voice.pocket.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CancellationException

@RunWith(AndroidJUnit4::class)
class PlaybackTests {
    private fun foreground(block: () -> Unit) {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val activity=instrumentation.startActivitySync(Intent(instrumentation.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try { block() } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
    @Test fun fullAndVeryShortStaticAudioPresentAllFramesWithoutStreamingThreshold() = foreground {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        for(frames in listOf(120,2400,24000,72000)) {
            val pcm=FloatArray(frames) { (kotlin.math.sin(it*2*kotlin.math.PI*440/24000)*.1).toFloat() }
            val copy=pcm.copyOf(); var started=0; val snapshots=mutableListOf<String>()
            PcmPlayer(context,CancellationToken(),{ snapshots.add(it) },{ started++ }).use { player ->
                player.write(pcm); player.drain()
                assertTrue(player.completed); assertEquals(frames.toLong(),player.submitted)
                assertEquals(1,started); assertTrue(player.lastHead==frames.toLong() || player.evidence=="AudioTimestamp présenté")
                assertTrue(snapshots.any { it.contains("route type") && it.contains("playState") })
                assertEquals(0,player.zeroWrites)
                println("STATIC_PLAYBACK_TEST $frames ${player.lastSnapshot}")
            }
            assertArrayEquals(copy,pcm,0f)
        }
    }
    @Test fun floatStaticOutputPresentsSameCompletePcmWithoutConversion() = foreground {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val pcm=FloatArray(24000) { kotlin.math.sin(it*.08).toFloat()*.1f }
        PcmPlayer(context,CancellationToken(),floatOutput=true).use { player ->
            player.write(pcm);player.drain();assertTrue(player.completed);assertEquals(24000L,player.submitted)
        }
    }
    @Test fun twoSecondStreamingStartThresholdReproducesNoProgressOnOneSecondPcm() = foreground {
        if(Build.VERSION.SDK_INT<31) return@foreground
        val track=AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(24000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).build())
            .setBufferSizeInBytes(192000).setTransferMode(AudioTrack.MODE_STREAM).build()
        try {
            println("STREAM_DEFAULT_THRESHOLD frames=${track.startThresholdInFrames} buffer=${track.bufferSizeInFrames}")
            assertEquals(track.bufferSizeInFrames,track.setStartThresholdInFrames(track.bufferSizeInFrames))
            assertEquals(24000,track.write(FloatArray(24000) { .01f },0,24000,AudioTrack.WRITE_NON_BLOCKING))
            track.play(); Thread.sleep(300)
            assertEquals(AudioTrack.PLAYSTATE_PLAYING,track.playState)
            assertEquals(0,track.playbackHeadPosition)
            println("STREAM_THRESHOLD_STALL_REPRODUCED submitted=24000 head=${track.playbackHeadPosition}")
        } finally { track.stop(); track.release() }
    }
    @Test fun stopInterruptsPlaybackAndCannotBeReportedAsCompleted() = foreground {
        val context=InstrumentationRegistry.getInstrumentation().targetContext; val token=CancellationToken()
        PcmPlayer(context,token).use { player ->
            player.write(FloatArray(240000) { .01f })
            val thread=Thread { Thread.sleep(150); player.stop() }.apply { start() }
            val began=SystemClock.elapsedRealtime()
            try { player.drain(); fail("Cancelled playback marked complete") } catch(_: CancellationException) { }
            thread.join(1000)
            assertFalse(player.completed); assertTrue(SystemClock.elapsedRealtime()-began<2000)
        }
    }
    @Test fun noPcmNonFiniteAndPreCancelledPlaybackAreRejected() = foreground {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        PcmPlayer(context,CancellationToken()).use { player ->
            try { player.drain(); fail("Missing PCM accepted") } catch(error: PlaybackFailure) { assertEquals("AUCUN_PCM_CHARGE",error.reason) }
            try { player.write(floatArrayOf(Float.NaN)); fail("Non-finite PCM accepted") } catch(_: IllegalArgumentException) { }
            assertFalse(player.completed)
        }
        PcmPlayer(context,CancellationToken().also { it.cancel() }).use { player ->
            try { player.write(FloatArray(120)); fail("Cancelled audio loaded") } catch(_: CancellationException) { }
        }
    }
}
