package com.jarvis.voice.speed

enum class EmotionalIntentV1 { NEUTRAL, JOY, SURPRISE, SADNESS, CALM, SERIOUS, CURIOSITY }
enum class EffectCategory { ACOUSTIC, DSP, METADATA, UNSUPPORTED }
data class EmotionAvailability(val intent: EmotionalIntentV1,val selectable: Boolean,val category: EffectCategory,val reason: String)
/** Capability is engine-owned and fail-closed, never inferred from a style label or mutable score. */
object Pocket6LEmotionCapabilities {
    const val VERSION="voice04.emotional-intent/1"
    fun availability(intent: EmotionalIntentV1)=if(intent==EmotionalIntentV1.NEUTRAL)
        EmotionAvailability(intent,true,EffectCategory.ACOUSTIC,"Synthèse de référence uniquement, sans commande émotionnelle")
    else EmotionAvailability(intent,false,EffectCategory.UNSUPPORTED,"Aucune commande acoustique validée pour cette émotion dans Pocket French 6L")
    fun requireSupported(intent: EmotionalIntentV1,intensity: Int=0) {
        require(intensity in 0..2)
        require(intensity==0 && availability(intent).selectable) { "ACOUSTIC_EMOTION_UNSUPPORTED" }
    }
    fun legacyMetadata(intent: EmotionalIntentV1)=when(intent) {
        EmotionalIntentV1.NEUTRAL->"NEUTRAL"
        EmotionalIntentV1.JOY->"HUMOROUS"
        EmotionalIntentV1.SURPRISE->"SURPRISED"
        EmotionalIntentV1.SADNESS->null
        EmotionalIntentV1.CALM->null
        EmotionalIntentV1.SERIOUS->"SERIOUS"
        EmotionalIntentV1.CURIOSITY->"CURIOUS"
    }
}
