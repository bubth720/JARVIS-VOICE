package com.jarvis.voice.sdk

import com.jarvis.voice.expression.*
import com.jarvis.voice.pocket.*

fun main() {
    val request=styleRequest()
    for(style in listOf(ExpressionStyleProfiles.neutral(),ExpressionStyleProfiles.athena())) {
        val plan=(ProsodyPlannerAdapter.prepare(request.plan,style,{ p -> LocalProsodyPlanner.prepareWith(p) { PreparedText(it,longArrayOf(1,2),0) } }) as ProsodyPlanningResult.Ready).plan
        val result=ExpressionRenderer.renderWith(plan,CancellationToken()) { _,_,sink ->
            sink(FloatArray(240));GenerationMetrics(1.0,1.0,1.0,.01,1,2,true)
        }
        val timeline=ExpressionTimeline.fromRendered(request.requestId,plan,result)
        println("SYNTHETIC_SOFTWARE_ONLY ${style.id}: segments=${plan.segments.size}, pauseMs=${plan.pauseMs}, samples=${timeline.samples}, cues=${timeline.cues.size}")
    }
    println("NO_NATIVE_EMOTION_PHONEME_ALIGNMENT_OR_HUMAN_LISTENING_VERIFIED")
}
