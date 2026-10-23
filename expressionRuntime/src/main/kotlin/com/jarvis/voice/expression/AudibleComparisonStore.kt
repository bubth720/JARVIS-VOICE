package com.jarvis.voice.expression

import com.jarvis.voice.identity.VoiceIdentityId
import com.jarvis.voice.identity.VoiceIdentityVault
import com.jarvis.voice.identity.VoiceProfile
import com.jarvis.voice.pocket.CancellationToken
import java.io.File

/** Uses the existing exact PCM32 cache, vault checks and A/B format; no profile copy or encoding. */
class AudibleComparisonStore(root: File) {
    private val existing=ExpressionComparisonStore(root)
    fun save(slot: String, profile: VoiceProfile, plan: LocalProsodyPlan, pcm: FloatArray,cancel: CancellationToken) {
        require(plan.expression.binding==ExpressionVoiceBinding.from(profile))
        existing.save(slot,profile.identityId,profile,plan.expression.request.utteranceText,
            plan.expression.seed,plan.expression.sampling,pcm,cancel,plan.expression.intent.name,plan.pauseMs)
    }
    fun requireB(profile: VoiceProfile,text: String,seed: Long,settings: com.jarvis.voice.pocket.QualitySampling,cancel: CancellationToken) =
        existing.requireB(profile.identityId,profile,existing.context(profile.identityId,text,seed,settings),cancel)
    fun load(slot: String,vault: VoiceIdentityVault,cancel: CancellationToken)=existing.load(slot,vault,cancel)
    fun deleteProfile(identity: VoiceIdentityId,profileId: String,cancel: CancellationToken)=existing.deleteProfile(identity,profileId,cancel)
    fun delete()=existing.delete()
}
