package com.jarvis.voice.expression

data class EmotionRenderPlan internal constructor(val requested: VoiceEmotionIntent,val effective: VoiceEmotionIntent,
    val intensity: Int,val softwareConsent: Boolean,val fallback: Boolean,val referenceStyle: ExpressionStyleProfile) {
    init {
        require(intensity in 0..2 && referenceStyle==ExpressionStyleProfiles.neutral())
        require(effective==VoiceEmotionIntent.NEUTRAL || softwareConsent && EmotionCapabilityMatrix.forIntent(effective).energyEnvelope)
    }
}
sealed interface EmotionPlanning {
    data class Ready(val plan: EmotionRenderPlan): EmotionPlanning
    data class Unsupported(val code: String="STYLE_UNSUPPORTED"): EmotionPlanning
}
class VoiceEmotionController {
    fun plan(intent: VoiceEmotionIntent,intensity: Int,softwareConsent: Boolean,fallbackNeutral: Boolean=false): EmotionPlanning {
        require(intensity in 0..2)
        if(intent==VoiceEmotionIntent.NEUTRAL || intensity==0) return EmotionPlanning.Ready(EmotionRenderPlan(intent,VoiceEmotionIntent.NEUTRAL,0,softwareConsent,intent!=VoiceEmotionIntent.NEUTRAL,ExpressionStyleProfiles.neutral()))
        if(!softwareConsent || !EmotionCapabilityMatrix.forIntent(intent).energyEnvelope) {
            return if(fallbackNeutral) EmotionPlanning.Ready(EmotionRenderPlan(intent,VoiceEmotionIntent.NEUTRAL,0,softwareConsent,true,ExpressionStyleProfiles.neutral())) else EmotionPlanning.Unsupported()
        }
        return EmotionPlanning.Ready(EmotionRenderPlan(intent,intent,intensity,true,false,ExpressionStyleProfiles.neutral()))
    }
}
/** Alternative engines remain unavailable; this selector cannot download or instantiate a model. */
object ExpressiveBackendSelection {
    enum class Candidate { POCKET_CURRENT, QWEN3_TTS, CHATTERBOX_MULTILINGUAL }
    fun enabled(candidate: Candidate)=candidate==Candidate.POCKET_CURRENT
}
