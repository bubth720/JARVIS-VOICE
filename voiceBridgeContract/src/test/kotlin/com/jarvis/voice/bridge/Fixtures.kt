package com.jarvis.voice.bridge
import org.json.*
internal object Fixtures {
    fun id(n: Int)="00000000-0000-4000-8000-"+n.toString().padStart(12,'0')
    val binding=VoiceBinding(id(1),id(2),"a".repeat(64),"b".repeat(64),"pocket-fp32", "c".repeat(64),"d".repeat(64))
    fun grant(text: String="Bonjour à tous.")=AuthorizedSpeech(id(3),id(4),id(5),text,binding,SpeechOptions(),true,true,90000,id(6),id(7))
    fun mapper(g: AuthorizedSpeech=grant())=BridgeMapper(AuthorizationPort { _,_,_->g })
    fun request()=JSONObject().put("protocol_version","1.0").put("contract_version","1.0.0").put("correlation_id",id(6)).put("origin","core").put("destination","voice").put("capability","speech.synthesize")
        .put("scope",JSONObject().put("session_id",id(3)).put("authorization_ref",id(4)))
        .put("limits",JSONObject().put("max_input_bytes",1200).put("max_output_tokens",0).put("timeout_ms",90000).put("declared_memory_bytes",2147483648L))
        .put("deadline",JSONObject().put("clock","sender_monotonic").put("sent_at_ms",500000).put("expires_at_ms",590000))
        .put("cancellation_id",id(7)).put("payload",JSONObject().put("content_ref",id(5)).put("voice_profile_ref",binding.profileId).put("pack_sha256",binding.packId).put("model_sha256",binding.modelSha256))
    fun job(n: Int=6)=mapper(grant().copy(correlationId=id(n))).request(request().put("correlation_id",id(n)).toString(),100,80000,26)
    fun response()=JSONObject().put("protocol_version","1.0").put("contract_version","1.0.0").put("correlation_id",id(6)).put("status","success")
        .put("result",JSONObject().put("kind","audio").put("artifact_ref",id(8)).put("sha256","e".repeat(64))).put("error",JSONObject.NULL)
        .put("provenance",JSONObject().put("module_id","voice").put("source_commit","f".repeat(40)).put("engine_id",binding.engineId).put("model_sha256",binding.modelSha256).put("verification","fixture"))
        .put("execution",JSONObject().put("duration_ms",1000).put("native_stopped",true).put("data_logged",false))
    fun capability()=JSONObject().put("contract_version","1.0.0").put("module_id","voice").put("module_version","0.6.0").put("state","available")
        .put("capabilities",JSONArray().put(JSONObject().put("id","speech.synthesize").put("version","1.0.0").put("available",true).put("qualification","fixture_tested")))
        .put("environment",JSONObject().put("platform","android").put("min_android_api",26).put("abi","arm64-v8a").put("offline_required",true))
        .put("compatibilities",JSONArray().put(JSONObject().put("engine_id",binding.engineId).put("model_sha256",binding.modelSha256).put("profile_format","JVFP1")))
    fun health()=JSONObject().put("contract_version","1.0.0").put("module_id","voice").put("state","available").put("checked_at","2026-10-17T00:00:00Z").put("reason_code","OK")
        .put("diagnostic",JSONObject().put("model_resident",false).put("active_heavy_work",false).put("qualified_hardware",JSONObject.NULL).put("details_logged",false))
    fun lease()=JSONObject().put("contract_version","1.0.0").put("lease_id",id(9)).put("owner","voice").put("operation_id",id(6)).put("resource_class","heavy").put("decision","admitted").put("state","running")
        .put("model_resident",true).put("cancellation_requested",false).put("effective_end",false).put("release_confirmed",false).put("declared_envelope_bytes",2147483648L).put("reserves_android_ram",false).put("coordination_scope","process").put("generation",1)
    fun reject(code: BridgeError?=null,block: ()->Unit) { try { block();org.junit.Assert.fail("Invalid contract accepted") } catch(e: BridgeFailure) { if(code!=null) org.junit.Assert.assertEquals(code,e.code) } }
}
