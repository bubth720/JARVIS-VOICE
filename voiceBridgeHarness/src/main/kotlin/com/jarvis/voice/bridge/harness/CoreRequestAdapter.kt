package com.jarvis.voice.bridge.harness

import com.jarvis.voice.bridge.*
import com.jarvis.voice.expression.*
import com.jarvis.voice.identity.*
import com.jarvis.voice.pocket.*

/** JVM/test adapter only, never linked by a historical Android application. */
object CoreRequestAdapter {
    fun request(job: SpeechJobRequest)=CoreVoiceRequestV1(job.text,VoiceIdentityId(job.binding.identityId),
        expressionIntent=job.options.intent,priority=job.options.priority,pauseMs=job.options.pauseMs,
        allowSentencePauses=job.options.allowSentencePauses)
    fun plan(job: SpeechJobRequest): VoiceExpressionPlan {
        val b=job.binding
        require(b.engineId==PocketIdentity.ENGINE && b.packId==PocketIdentity.packId) { "INCOMPATIBLE" }
        val binding=ExpressionVoiceBinding(VoiceIdentityId(b.identityId),b.profileId,b.profileSha256,b.conditioningSha256,b.engineId,b.packId)
        return ExpressionPolicy.plan(request(job),binding,QualitySampling(job.options.steps,job.options.temperature),job.options.seed)
    }
}

/** The default adapter cannot discover/bind CORE, acquire Android authority or open a model. */
class DisabledVoiceAdapter: VoiceRendererPort {
    override fun render(request: SpeechJobRequest)=SpeechJobResult(request.correlationId,request.sessionId,GenerationOutcome.REFUSED,PlaybackOutcome.NOT_STARTED,BridgeError.MODULE_ABSENT)
    override fun cancel(cancellationId: String) { require(runCatching { java.util.UUID.fromString(cancellationId).toString()==cancellationId }.getOrDefault(false)) { "INVALID_INPUT" } }
}
