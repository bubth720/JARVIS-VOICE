package com.jarvis.voice.speed

enum class SafePlaybackMode { AUTO_SAFE, DEFERRED, EXPERIMENTAL_STREAM }
enum class PlaybackDecisionReason { REQUESTED_DEFERRED, API_GUARD, PREVIOUS_SLOW_PRODUCER, CONDITIONAL_PREFLIGHT }
data class PlaybackDecision(val mayProbeStream: Boolean,val reason: PlaybackDecisionReason)
object SafePlaybackPolicy {
    fun decide(mode: SafePlaybackMode,api: Int,previousRtf: Double?): PlaybackDecision {
        require(api>=26 && (previousRtf==null || previousRtf.isFinite() && previousRtf>=0))
        return when {
            mode==SafePlaybackMode.DEFERRED->PlaybackDecision(false,PlaybackDecisionReason.REQUESTED_DEFERRED)
            api<=30->PlaybackDecision(false,PlaybackDecisionReason.API_GUARD)
            mode==SafePlaybackMode.AUTO_SAFE && previousRtf!=null && previousRtf>.65->PlaybackDecision(false,PlaybackDecisionReason.PREVIOUS_SLOW_PRODUCER)
            else->PlaybackDecision(true,PlaybackDecisionReason.CONDITIONAL_PREFLIGHT)
        }
    }
}
