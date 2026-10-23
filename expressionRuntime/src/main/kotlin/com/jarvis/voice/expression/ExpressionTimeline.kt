package com.jarvis.voice.expression

import java.util.Collections
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToLong

enum class ExpressionCueKind { SPEECH, PAUSE }
data class ExpressionCue(val index: Int, val kind: ExpressionCueKind, val startSample: Int, val endSample: Int) {
    init { require(index in 0..4 && startSample >= 0 && endSample > startSample && endSample <= 480000) }
    val startMs: Double get() = startSample / 24.0
    val endMs: Double get() = endSample / 24.0
}
/** No PCM, text, identity/profile, inferred emotion or wall-clock/native unloading assertion. */
class ExpressionTimeline private constructor(val requestId: String, cues: List<ExpressionCue>, val samples: Int) {
    val cues: List<ExpressionCue> = Collections.unmodifiableList(cues.toList())
    val sampleRate = 24000
    val clock = "PCM_SAMPLE_OFFSET"
    val qualification = "SEGMENT_BOUNDARIES_ONLY"
    val phonemesAvailable = false
    companion object {
        fun fromRendered(requestId: String, plan: LocalProsodyPlan, result: ExpressionRenderResult): ExpressionTimeline {
            require(UUID.fromString(requestId).toString()==requestId)
            require(result.pcm.size in 1..480000 && result.pcm.all { it.isFinite() })
            require(result.metrics.size==plan.segments.size && result.metrics.all { it.eosReached })
            val cues=mutableListOf<ExpressionCue>();var offset=0
            for((i,metric) in result.metrics.withIndex()) {
                require(metric.audioSeconds.isFinite() && metric.audioSeconds > 0)
                val exact=metric.audioSeconds*24000
                val count=exact.roundToLong()
                require(abs(exact-count)<.000001 && count in 1..480000)
                if(i>0 && plan.pauseMs>0) {
                    val end=offset+plan.pauseMs*24
                    cues+=ExpressionCue(cues.size,ExpressionCueKind.PAUSE,offset,end);offset=end
                }
                val end=offset+count.toInt()
                cues+=ExpressionCue(cues.size,ExpressionCueKind.SPEECH,offset,end);offset=end
            }
            require(offset==result.pcm.size) { "Durée des segments incompatible avec le PCM réellement rendu" }
            return ExpressionTimeline(requestId,cues,offset)
        }
    }
}
fun interface AvatarTimingCuePort { fun publish(timeline: ExpressionTimeline) }
