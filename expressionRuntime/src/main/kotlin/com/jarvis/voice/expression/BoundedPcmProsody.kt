package com.jarvis.voice.expression
import com.jarvis.voice.pocket.CancellationToken
import kotlin.math.*

data class EmotionPcmResult(val pcm: FloatArray,val changedSamples: Int,val maxAbsDifference: Double,
    val inputRms: Double,val outputRms: Double,val cues: EmotionAvatarCues) {
    override fun toString()="EmotionPcmResult(samples=${pcm.size},changed=$changedSamples,humanValidated=false)"
}
/** Gain envelopes only. No resampling, pitch synthesis, lexical rewrite, clipper or recorded laughter. */
object BoundedPcmProsody {
    private fun smooth(t: Double): Double { val x=t.coerceIn(0.0,1.0);return x*x*(3-2*x) }
    fun render(source: FloatArray,plan: EmotionRenderPlan,cancel: CancellationToken): EmotionPcmResult {
        cancel.check();require(source.size in 1..480000 && source.all { it.isFinite() })
        val neutral=plan.effective==VoiceEmotionIntent.NEUTRAL
        val peak=source.maxOf { abs(it.toDouble()) };val safeGain=if(peak>0) minOf(1.0,.98/peak) else 1.0
        val output=source.copyOf();var changed=0;var difference=0.0;var inSquares=0.0;var outSquares=0.0
        for(i in source.indices) {
            if(i%4096==0) cancel.check()
            if(!neutral) {
                val t=i/24000.0
                val envelope=when(plan.effective) {
                    VoiceEmotionIntent.JOY->if(t<.15) .65+.30*smooth(t/.15) else .95-.10*smooth((t-.15)/.5)
                    VoiceEmotionIntent.SURPRISE->if(t<.07) .45+.50*smooth(t/.07) else .95-.30*smooth((t-.07)/.4)
                    VoiceEmotionIntent.CALM->.60+.10*smooth(t/.3)
                    else->error("STYLE_UNSUPPORTED")
                }
                val gain=minOf(safeGain,1+(envelope-1)*plan.intensity/2.0)
                output[i]=(source[i]*gain).toFloat()
            }
            if(output[i].toRawBits()!=source[i].toRawBits()) changed++
            difference=maxOf(difference,abs(output[i].toDouble()-source[i]));inSquares+=source[i].toDouble()*source[i];outSquares+=output[i].toDouble()*output[i]
        }
        cancel.check()
        val cues=buildList {
            add(EmotionAvatarCue(EmotionCueKind.SPEECH,0,source.size))
            if(!neutral) add(EmotionAvatarCue(EmotionCueKind.SOFTWARE_ENERGY_ENVELOPE,0,minOf(source.size,12000)))
        }
        return EmotionPcmResult(output,changed,difference,sqrt(inSquares/source.size),sqrt(outSquares/source.size),EmotionAvatarCues(24000,source.size,cues))
    }
}
