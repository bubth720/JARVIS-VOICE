package com.jarvis.voice.expression

/** Local software labels matching CORE ID-01's readStyle(context), not a new wire contract. */
enum class ExpressionContext { DAILY, WORK, URGENT }
enum class PublicPersonaStyle { ELEGANT, PLAIN }
data class PublicPersonaStyleIntent(val context: ExpressionContext, val style: PublicPersonaStyle, val tutoiement: Boolean)
fun interface PersonaStylePort { fun readStyle(context: ExpressionContext): PublicPersonaStyleIntent }
data class ExpressionStyleProfile(val id: String, val context: ExpressionContext, val pauseMs: Int) {
    init { require(id in setOf("neutral-v1", "athena-v1", "custom-v1") && pauseMs in 0..1500) }
}
object ExpressionStyleProfiles {
    fun neutral(context: ExpressionContext = ExpressionContext.DAILY) = ExpressionStyleProfile("neutral-v1",context,0)
    fun athena(context: ExpressionContext = ExpressionContext.DAILY) = ExpressionStyleProfile("athena-v1",context,
        when(context) { ExpressionContext.DAILY -> 500; ExpressionContext.WORK -> 250; ExpressionContext.URGENT -> 0 })
    fun fromPublicIntent(intent: PublicPersonaStyleIntent) = when(intent.style) {
        PublicPersonaStyle.PLAIN -> neutral(intent.context)
        PublicPersonaStyle.ELEGANT -> athena(intent.context)
    }
}
sealed interface ProsodyPlanningResult {
    data class Ready(val plan: LocalProsodyPlan, val style: ExpressionStyleProfile) : ProsodyPlanningResult {
        override fun toString() = "Ready(style=${style.id}, segments=${plan.segments.size})"
    }
    data class Unsupported(val fields: Set<String>, val code: String = "STYLE_UNSUPPORTED") : ProsodyPlanningResult
}
object ProsodyPlannerAdapter {
    fun unsupported(plan: VoiceExpressionPlan, requireNativeEmotion: Boolean = false): Set<String> = buildSet {
        if(plan.request.intensity != null) add("intensity")
        if(plan.request.speedRequested != null) add("rhythm")
        if(requireNativeEmotion && plan.request.expressionIntent != "NEUTRAL") add("emotion")
    }
    /** Intensity/rhythm never mutate sampling or text. Existing explicit pause values take priority. */
    fun prepare(plan: VoiceExpressionPlan, style: ExpressionStyleProfile,
        prepareExisting: (VoiceExpressionPlan) -> LocalProsodyPlan,
        requireNativeEmotion: Boolean = false): ProsodyPlanningResult {
        val unsupported=unsupported(plan,requireNativeEmotion)
        if(unsupported.isNotEmpty()) return ProsodyPlanningResult.Unsupported(unsupported)
        val pause=plan.request.pauseMs ?: if(plan.request.allowSentencePauses) style.pauseMs else 0
        val request=plan.request.copy(pauseMs=pause)
        LocalProsodyPlanner.requireExactText(plan.request.utteranceText,request.utteranceText)
        val policy=ExpressionPolicy.plan(request,plan.binding,plan.sampling,plan.seed)
        return ProsodyPlanningResult.Ready(prepareExisting(policy),style)
    }
}
