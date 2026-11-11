package com.jarvis.voice.studio

/**
 * V3 waiting feedback policy. This never accelerates the neuronal render, never starts a
 * second model, and never guesses that CORE is thinking. Only a visible, explicitly
 * opted-in Studio generation may play an already prepared local clip.
 */
object QuickReactionTiming {
    const val WAIT_MS = 1200L
    const val COOLDOWN_MS = 20000L
    fun shouldPlay(
        elapsedMs: Long, previousClipAgoMs: Long?, activeVisibleGeneration: Boolean,
        optedIn: Boolean, sameConfirmedProfile: Boolean, thermalSafe: Boolean,
        preparedClipAvailable: Boolean, finalAudioReady: Boolean, anotherAudioPlaying: Boolean
    ): Boolean {
        return elapsedMs >= WAIT_MS && elapsedMs >= 0 &&
            (previousClipAgoMs == null || previousClipAgoMs >= COOLDOWN_MS) &&
            activeVisibleGeneration && optedIn && sameConfirmedProfile && thermalSafe &&
            preparedClipAvailable && !finalAudioReady && !anotherAudioPlaying
    }
}

/** The phrase is intentionally truthful: never promise a response in two seconds. */
enum class QuickReactionPhrase(val spoken: String) {
    THINK("Je réfléchis à ta demande."),
    CHECK("Je vérifie ça."),
    SPEECH("Je prépare ma réponse.")
}
