package com.jarvis.voice.final01

import com.jarvis.voice.pocket.CancellationToken
import kotlin.math.*

/** Descriptive acoustic QC only, not a speaker verifier or proof that recordings share a voice. */
data class ReferenceFeatures(val seconds: Double,val rms: Double,val peak: Double,val silencePercent: Double,
    val clippingPercent: Double,val medianPitchHz: Double?,val periodicFramePercent: Double) {
    companion object {
        fun analyze(pcm: FloatArray,cancel: CancellationToken=CancellationToken()): ReferenceFeatures {
            require(pcm.size in 2400..720000 && pcm.all { it.isFinite() }) { "REFERENCE_BOUND_OR_NONFINITE" }
            var squares=0.0;var peak=0.0;var silent=0;var clipped=0
            pcm.forEachIndexed { i,v -> if(i%4096==0) cancel.check();val a=abs(v.toDouble());squares+=a*a;peak=maxOf(peak,a);if(a<.0001) silent++;if(a>=1) clipped++ }
            val pitches=mutableListOf<Double>();var frames=0
            for(start in 0..pcm.size-960 step 960) {
                cancel.check();frames++;var best=0.0;var lagBest=0
                var energy=0.0;for(i in 0 until 960) energy+=pcm[start+i].toDouble().pow(2)
                if(energy/960<.000025) continue
                for(lag in 60..400) {
                    var dot=0.0;var aa=0.0;var bb=0.0
                    for(i in 0 until 960-lag) { val a=pcm[start+i].toDouble();val b=pcm[start+i+lag].toDouble();dot+=a*b;aa+=a*a;bb+=b*b }
                    val correlation=dot/sqrt(aa*bb).coerceAtLeast(1e-20)
                    // Prefer first strong local period, reducing harmonic multiples for clean signals.
                    if(correlation>best+.001) { best=correlation;lagBest=lag }
                }
                if(best>=.65) pitches.add(24000.0/lagBest)
            }
            pitches.sort()
            return ReferenceFeatures(pcm.size/24000.0,sqrt(squares/pcm.size),peak,silent*100.0/pcm.size,clipped*100.0/pcm.size,
                pitches.takeIf { it.isNotEmpty() }?.let { it[it.size/2] },if(frames==0) 0.0 else pitches.size*100.0/frames)
        }
    }
}
