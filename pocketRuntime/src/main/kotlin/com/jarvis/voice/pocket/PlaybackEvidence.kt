package com.jarvis.voice.pocket

import kotlin.math.abs
import kotlin.math.sqrt
import java.util.Locale

/** Diagnostics are computed on original PCM, before clipping/PCM16 conversion. */
data class PcmStatistics(val samples: Int, val finite: Int, val nonFinite: Int, val peak: Double,
    val rms: Double, val silencePercent: Double, val clippedPercent: Double) {
    val seconds get() = samples / 24000.0
    fun summary(): String = String.format(Locale.FRENCH,
        "PCM : %d échantillons, 24000 Hz, %.3f s ; pic %.6f ; RMS %.6f ; silence |x| < 0,0001 : %.2f %% ; non finis %d ; écrêtage PCM16 %.2f %%.",
        samples, seconds, peak, rms, silencePercent, nonFinite, clippedPercent)
    companion object {
        fun measure(pcm: FloatArray): PcmStatistics {
            var peak=0.0; var squares=0.0; var finite=0; var silent=0; var clipped=0
            for(sample in pcm) if(sample.isFinite()) {
                val value=abs(sample.toDouble()); finite++; peak=maxOf(peak,value); squares+=value*value
                if(value<0.0001) silent++
                if(value>=1.0) clipped++
            }
            return PcmStatistics(pcm.size,finite,pcm.size-finite,peak,sqrt(squares/maxOf(finite,1)),
                silent*100.0/maxOf(pcm.size,1),clipped*100.0/maxOf(pcm.size,1))
        }
    }
}

class PlaybackFailure(val reason: String) : IllegalStateException("Lecture [$reason] — consulter les compteurs AudioTrack")
enum class PlaybackStage { WAITING, STARTED, COMPLETED }

/** Elapsed time is only a failure bound. Success requires measured presentation of all frames. */
class PlaybackEvidence(private val frames: Long, private val startMs: Long, private val sampleRate: Int=24000) {
    init { require(frames in 1..480000 && sampleRate==24000) }
    private var largest=0L
    private var progressedAt=startMs
    var evidence="aucune"; private set
    var progressedFrames=0L; private set
    private val endDeadline=startMs+(frames*1000+sampleRate-1)/sampleRate+4000
    fun observe(nowMs: Long, initialized: Boolean, playing: Boolean, head: Long, presentedTimestamp: Long?=null): PlaybackStage {
        if(!initialized) throw PlaybackFailure("NON_INITIALISE")
        if(!playing) throw PlaybackFailure("PLAY_STATE_INATTENDU")
        if(head !in 0..frames || presentedTimestamp?.let { it !in 0..frames }==true) throw PlaybackFailure("COMPTEUR_INCOHERENT")
        val presented=maxOf(head,presentedTimestamp ?: 0)
        if(presented<largest) throw PlaybackFailure("COMPTEUR_REINITIALISE")
        if(presented>largest) { largest=presented; progressedAt=nowMs }
        progressedFrames=largest
        if(presented==frames) {
            evidence=if(head==frames) "playbackHeadPosition" else "AudioTimestamp présenté"
            return PlaybackStage.COMPLETED
        }
        if(largest==0L && nowMs-startMs>=2000) throw PlaybackFailure("DEMARRAGE_BLOQUE")
        if(largest>0L && nowMs-progressedAt>=2000) throw PlaybackFailure("COMPTEUR_STAGNANT")
        if(nowMs>=endDeadline) throw PlaybackFailure("DELAI_GLOBAL_DEPASSE")
        return if(largest>0) PlaybackStage.STARTED else PlaybackStage.WAITING
    }
}
