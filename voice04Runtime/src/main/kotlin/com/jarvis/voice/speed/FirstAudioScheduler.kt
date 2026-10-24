package com.jarvis.voice.speed
import com.jarvis.voice.pocket.FrenchFrontend
import org.json.JSONObject

/** Session-scoped light frontend only. Native models are never cached here. */
class FirstAudioScheduler {
    private var fingerprint: String?=null
    private var frontend: FrenchFrontend?=null
    private var previousRtf: Double?=null
    @Synchronized fun frontend(verifiedSha256: String,load: ()->FrenchFrontend): FrenchFrontend {
        require(verifiedSha256.matches(Regex("[0-9a-f]{64}")))
        if(fingerprint!=verifiedSha256) { frontend=null;fingerprint=null }
        return frontend ?: load().also { frontend=it;fingerprint=verifiedSha256 }
    }
    @Synchronized fun beginSession() { previousRtf=null }
    @Synchronized fun decision(mode: SafePlaybackMode,api: Int)=SafePlaybackPolicy.decide(mode,api,previousRtf)
    @Synchronized fun produced(computeMs: Double,seconds: Double) { require(computeMs.isFinite() && computeMs>=0 && seconds.isFinite() && seconds>0);previousRtf=computeMs/(seconds*1000) }
    fun report(trace: SpeedTrace,decision: PlaybackDecision)=trace.json().put("requestedPolicyReason",decision.reason.name)
        .put("humanFirstSoundMs",JSONObject.NULL).put("modelResidencyBetweenJobs",false)
}
