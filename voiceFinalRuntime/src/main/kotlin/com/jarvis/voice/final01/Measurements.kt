package com.jarvis.voice.final01

import org.json.JSONObject
import kotlin.math.ceil

enum class Stage { TOKENIZATION, TEXT_ENCODING, FLOW_LM, FLOW_SAMPLER, PCM_DECODER, PROFILE_RESOLVE, AUDIO_TRANSFER, AUDIO_DRAIN }
class StageProfiler(private val clock: ()->Long=System::nanoTime) {
    private val totals=linkedMapOf<Stage,Double>()
    @Synchronized fun add(stage: Stage,ms: Double) { require(ms.isFinite() && ms>=0);totals[stage]=(totals[stage] ?: 0.0)+ms }
    fun <T> timed(stage: Stage,action: ()->T): T { val begin=clock();try { return action() } finally { add(stage,(clock()-begin)/1e6) } }
    @Synchronized fun json()=JSONObject().apply { Stage.entries.forEach { put(it.name,totals[it] ?: JSONObject.NULL) } }
}
object Quantiles {
    /** Nearest rank; sample count is always published. Null is unavailable, never zero. */
    fun summarize(values: List<Double>): JSONObject {
        require(values.all { it.isFinite() && it>=0 })
        val sorted=values.sorted()
        fun q(p: Double): Any=if(sorted.isEmpty()) JSONObject.NULL else sorted[(ceil(p*sorted.size).toInt()-1).coerceAtLeast(0)]
        return JSONObject().put("n",sorted.size).put("p50",q(.5)).put("p95",q(.95)).put("method","nearest-rank")
    }
}
enum class ResourceMode { ECO, BALANCED, AUTO }
object ResourcePolicy {
    // No experimental thread count, native cache or weakened admission rule.
    const val THREADS=1
    fun cooldownMs(mode: ResourceMode,batteryPercent: Int?,thermal: Int): Long {
        require(batteryPercent==null || batteryPercent in 0..100)
        require(thermal<3) { "THERMAL_BLOCKED" }
        require(batteryPercent==null || batteryPercent>=10) { "BATTERY_BLOCKED" }
        return if(mode==ResourceMode.ECO || mode==ResourceMode.AUTO && (thermal>=1 || batteryPercent!=null && batteryPercent<25)) 1000 else 0
    }
}
