package com.jarvis.voice.studio
import com.jarvis.voice.identity.VoiceProfile
import com.jarvis.voice.expression.*
import com.jarvis.voice.pocket.*
import java.io.File

/** This is the production studio path, not a second sampler or a text rewriting layer. */
object StudioRendering {
    fun plan(state: StudioState,profile: VoiceProfile,baseline: Boolean=false)=ExpressionPolicy.plan(
        state.settings.request(state.text,profile.identityId,baseline),ExpressionVoiceBinding.from(profile),state.settings.sampling(),state.settings.seed)
    fun render(pack: File,voice: PersonalVoiceProfile,plan: VoiceExpressionPlan,cancel: CancellationToken)=
        ExpressionRenderer.render(pack,voice,ExpressionRenderer.prepare(pack,plan),cancel)
}
