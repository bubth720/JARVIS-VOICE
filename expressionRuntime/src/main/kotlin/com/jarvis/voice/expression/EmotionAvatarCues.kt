package com.jarvis.voice.expression

enum class EmotionCueKind { SPEECH, SOFTWARE_ENERGY_ENVELOPE }
data class EmotionAvatarCue(val kind: EmotionCueKind,val startSample: Int,val endSample: Int) {
    init { require(startSample>=0 && endSample>startSample && endSample<=480000) }
}
/** Relative PCM offsets only. No face action, heard emotion, text, audio, identity or wall-clock anchor. */
data class EmotionAvatarCues(val sampleRate: Int,val samples: Int,val cues: List<EmotionAvatarCue>,val humanEmotionVerified: Boolean=false) {
    init { require(sampleRate==24000 && samples in 1..480000 && !humanEmotionVerified && cues.all { it.endSample<=samples }) }
}
