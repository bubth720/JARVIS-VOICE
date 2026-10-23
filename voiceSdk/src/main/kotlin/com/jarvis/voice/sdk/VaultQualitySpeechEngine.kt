package com.jarvis.voice.sdk

import com.jarvis.voice.expression.ExpressionRenderer
import com.jarvis.voice.identity.ProfileStatus
import com.jarvis.voice.identity.VoiceIdentityVault
import com.jarvis.voice.pocket.CancellationToken
import com.jarvis.voice.pocket.PersonalVoiceProfile
import java.io.File

/** Resolves only in the original private vault; never exports, activates or re-encodes a profile. */
class VoicePackResolver(private val vault: VoiceIdentityVault, private val pack: File) {
    fun resolve(request: SpeechRequest, cancel: CancellationToken): PersonalVoiceProfile {
        cancel.check()
        if (!File(pack, "bundle.json").isFile || !File(pack, "tokenizer.json").isFile)
            throw SpeechRejected(SpeechRefusal.MODEL_UNAVAILABLE)
        val binding = request.plan.binding
        val selected = vault.list().singleOrNull { it.id == binding.identityId }
            ?.profiles?.singleOrNull { it.id == binding.profileId && it.status == ProfileStatus.VALID }
            ?: throw SpeechRejected(SpeechRefusal.PROFILE_UNAVAILABLE)
        if (selected.sha256 != binding.profileSha256 || selected.conditioningSha256 != binding.conditioningSha256 ||
            selected.engineId != binding.engineId || selected.packId != binding.packId)
            throw SpeechRejected(SpeechRefusal.PROFILE_UNAVAILABLE)
        return vault.binding(binding.engineId, binding.packId).resolve(binding.identityId, binding.profileId, cancel)
    }
}

/** Existing renderer creates one QualityEngine, reuses it for its segments, then closes it.
 * Host MUST retain its existing heavy-operation admission around the entire SDK call.
 * This adapter neither reserves Android RAM nor authorizes JNI, Binder or another engine.
 */
class VaultQualitySpeechEngine(private val vault: VoiceIdentityVault, private val pack: File) : LocalSpeechEnginePort {
    override fun render(request: SpeechRequest, cancel: CancellationToken): RenderedSpeech {
        val voice = VoicePackResolver(vault, pack).resolve(request, cancel)
        val plan = try { ExpressionRenderer.prepare(pack, request.plan) }
            catch (_: IllegalArgumentException) { throw SpeechRejected(SpeechRefusal.INVALID_REQUEST) }
        val result = ExpressionRenderer.render(pack, voice, plan, cancel)
        return RenderedSpeech(result.pcm, result.metrics.all { it.eosReached })
    }
}
