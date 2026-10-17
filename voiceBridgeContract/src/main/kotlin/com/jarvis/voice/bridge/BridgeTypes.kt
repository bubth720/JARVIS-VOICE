package com.jarvis.voice.bridge

import java.util.UUID

enum class BridgeError { UNKNOWN,MODULE_ABSENT,INCOMPATIBLE,UNAUTHORIZED,INTERRUPTED,EXPIRED,CANCELLED,INSUFFICIENT_RESOURCES,INVALID_INPUT,BUSY,CLEANUP_FAILED }
class BridgeFailure(val code: BridgeError): IllegalArgumentException(code.name)
internal fun checkBridge(ok: Boolean,code: BridgeError=BridgeError.INVALID_INPUT) { if(!ok) throw BridgeFailure(code) }
internal fun uuid(s: String) { checkBridge(runCatching { UUID.fromString(s).toString()==s }.getOrDefault(false)) }
internal fun digest(s: String) { checkBridge(s.matches(Regex("[0-9a-f]{64}"))) }
internal fun unicode(s: String) {
    var i=0
    while(i<s.length) { val c=s[i++];if(c.isHighSurrogate()) { checkBridge(i<s.length && s[i].isLowSurrogate());i++ } else checkBridge(!c.isLowSurrogate()) }
}
fun validateSpeechText(s: String) {
    unicode(s);checkBridge(s.length in 1..300 && s.isNotBlank() && s.toByteArray(Charsets.UTF_8).size<=1200)
    checkBridge(s.none { it.isISOControl() && it!='\n' } && !Regex("<(?:unk|s|/s|pad)>").containsMatchIn(s))
}
/** Exact private binding. No tensor, path, archive, default voice, or automatic substitution. */
data class VoiceBinding(val identityId: String,val profileId: String,val profileSha256: String,
    val conditioningSha256: String,val engineId: String,val modelSha256: String,val packId: String) {
    init { uuid(identityId);uuid(profileId);listOf(profileSha256,conditioningSha256,modelSha256,packId).forEach(::digest);checkBridge(engineId.matches(Regex("[a-z0-9._-]{1,120}"))) }
    override fun toString()="VoiceBinding[private]"
}
data class SpeechOptions(val seed: Long=42,val steps: Int=1,val temperature: Double=.3,
    val intent: String="NEUTRAL",val pauseMs: Int=0,val allowSentencePauses: Boolean=false,val priority: Int=0) {
    init {
        checkBridge(steps in setOf(1,2,3,5) && temperature.isFinite() && temperature in .1.. .6)
        checkBridge(intent in setOf("NEUTRAL","INFORMATIVE","CONVERSATION","SERIOUS","URGENT","CURIOUS","SURPRISED","HUMOROUS"))
        checkBridge(pauseMs in setOf(0,250,500,1000,1500) && (allowSentencePauses || pauseMs==0) && priority in 0..3)
    }
}
data class VoiceCapabilityQuery(val version: String="005k.1-proposed",val sessionId: String,val authorizationRef: String,val androidApi: Int) {
    init { checkBridge(version=="005k.1-proposed",BridgeError.INCOMPATIBLE);uuid(sessionId);uuid(authorizationRef);checkBridge(androidApi in 24..100) }
    override fun toString()="VoiceCapabilityQuery[private]"
}
data class VoiceCapabilityReply(val available: Boolean,val error: BridgeError?,val binding: VoiceBinding?=null,
    val version: String="005k.1-proposed",val minApi: Int=26,val nativeEmotion: Boolean=false,val streamingPlayback: Boolean=false) {
    init { checkBridge(version=="005k.1-proposed",BridgeError.INCOMPATIBLE);checkBridge(available==(error==null));checkBridge(!available || binding!=null);checkBridge(available || binding==null);checkBridge(!nativeEmotion && !streamingPlayback && minApi==26) }
    override fun toString()="VoiceCapabilityReply[private]"
}
/** Produced by a future trusted authorization boundary, never deserialized from a caller's grant. */
data class AuthorizedSpeech(val sessionId: String,val authorizationRef: String,val contentRef: String,
    val text: String,val binding: VoiceBinding,val options: SpeechOptions,val consent: Boolean,
    val confirmed: Boolean,val expiresLocalMs: Long,val correlationId: String,val cancellationId: String) {
    init { listOf(sessionId,authorizationRef,contentRef,correlationId,cancellationId).forEach(::uuid);validateSpeechText(text);checkBridge(expiresLocalMs>=0) }
    override fun toString()="AuthorizedSpeech[private]"
}
/** No implementation authenticates Android here. Default absence must deny access. */
fun interface AuthorizationPort { fun resolve(sessionId: String,authorizationRef: String,contentRef: String): AuthorizedSpeech? }
data class SpeechJobRequest(val correlationId: String,val sessionId: String,val cancellationId: String,
    val text: String,val binding: VoiceBinding,val options: SpeechOptions,val deadlineLocalMs: Long) {
    init { listOf(correlationId,sessionId,cancellationId).forEach(::uuid);validateSpeechText(text);checkBridge(deadlineLocalMs>=0) }
    override fun toString()="SpeechJobRequest[private]"
}
enum class GenerationOutcome { REFUSED,SUCCEEDED,FAILED }
enum class PlaybackOutcome { NOT_STARTED,STARTED,COMPLETED,CANCELLED,UNKNOWN }
data class SpeechJobResult(val correlationId: String,val sessionId: String,val generation: GenerationOutcome,
    val playback: PlaybackOutcome,val error: BridgeError?,val artifactRef: String?=null,val pcmSha256: String?=null,
    val durationMs: Long=0,val nativeStopped: Boolean?=null) {
    init {
        uuid(correlationId);uuid(sessionId);checkBridge(durationMs in 0..90000)
        if(generation==GenerationOutcome.SUCCEEDED) { checkBridge(error==null && artifactRef!=null && pcmSha256!=null);uuid(artifactRef!!);digest(pcmSha256!!) }
        else checkBridge(error!=null && artifactRef==null && pcmSha256==null && playback in setOf(PlaybackOutcome.NOT_STARTED,PlaybackOutcome.UNKNOWN))
    }
    // Even a frame-complete AudioTrack callback cannot prove that a human heard it.
    val humanAudibilityVerified get()=false
    override fun toString()="SpeechJobResult[generation=$generation,playback=$playback]"
}
