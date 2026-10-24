package com.jarvis.voice.expression

enum class VoiceEmotionIntent { NEUTRAL, JOY, SURPRISE, CONCERN, THOUGHTFUL, CALM, SADNESS, LAUGH_OPTIONAL }
enum class EmotionQualification { NEUTRAL_REFERENCE, SOFTWARE_EXPERIMENT, STYLE_UNSUPPORTED }
data class EmotionCapability(val qualification: EmotionQualification,val energyEnvelope: Boolean,
    val nativeEmotion: Boolean=false,val pitch: Boolean=false,val timeStretch: Boolean=false,val humanValidated: Boolean=false)
object EmotionCapabilityMatrix {
    const val VERSION="voice06.emotion-experiment/1"
    fun forIntent(intent: VoiceEmotionIntent)=when(intent) {
        VoiceEmotionIntent.NEUTRAL->EmotionCapability(EmotionQualification.NEUTRAL_REFERENCE,false)
        VoiceEmotionIntent.JOY,VoiceEmotionIntent.SURPRISE,VoiceEmotionIntent.CALM->EmotionCapability(EmotionQualification.SOFTWARE_EXPERIMENT,true)
        else->EmotionCapability(EmotionQualification.STYLE_UNSUPPORTED,false)
    }
}
