package com.jarvis.voice.sdk

import com.jarvis.voice.expression.*
import com.jarvis.voice.pocket.CancellationToken

data class PlannedExpressionRender(val plan: LocalProsodyPlan, val result: ExpressionRenderResult) {
    override fun toString() = "PlannedExpressionRender(segments=${plan.segments.size}, samples=${result.pcm.size})"
}
fun interface ExpressionRenderPort { fun render(request: SpeechRequest, cancel: CancellationToken): PlannedExpressionRender }

/** Host supplies its existing renderer and retains the same worker/admission as VOICE-01. */
class ExpressionSpeechAdapter(private val renderer: ExpressionRenderPort,
    private val cuePort: AvatarTimingCuePort = AvatarTimingCuePort {}) : LocalSpeechEnginePort {
    override fun render(request: SpeechRequest,cancel: CancellationToken): RenderedSpeech {
        cancel.check()
        val rendered=renderer.render(request,cancel)
        try {
            cancel.check()
            val actual=rendered.plan.expression
            require(actual.binding==request.plan.binding && actual.seed==request.plan.seed && actual.sampling==request.plan.sampling)
            LocalProsodyPlanner.requireExactText(request.plan.request.utteranceText,actual.request.utteranceText)
            val timeline=ExpressionTimeline.fromRendered(request.requestId,rendered.plan,rendered.result)
            cancel.check()
            // An avatar consumer cannot convert a successful render into a native cleanup failure.
            runCatching { cuePort.publish(timeline) }
            cancel.check()
            return RenderedSpeech(rendered.result.pcm,rendered.result.metrics.all { it.eosReached })
        } catch(t: Throwable) { rendered.result.pcm.fill(0f);throw t }
    }
}
