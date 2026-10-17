package com.jarvis.voice.bridge

/** Only an already authorized private content resolver can supply text/binding/options.
 * role strings and authorization UUIDs never authenticate an Android caller.
 */
class BridgeMapper(private val authorizer: AuthorizationPort=AuthorizationPort { _,_,_->null }) {
    fun request(raw: String,nowLocalMs: Long,remainingTrustedMs: Long,androidApi: Int): SpeechJobRequest {
        val j=UnifiedSchemas.parse(Envelope.REQUEST,raw)
        checkBridge(androidApi>=26,BridgeError.INCOMPATIBLE)
        checkBridge(j.getString("origin")=="core" && j.getString("destination")=="voice" && j.getString("capability")=="speech.synthesize",BridgeError.UNAUTHORIZED)
        val scope=j.getJSONObject("scope");val p=j.getJSONObject("payload");val l=j.getJSONObject("limits");val d=j.getJSONObject("deadline")
        checkBridge(nowLocalMs>=0 && nowLocalMs<=9007199254740991L-90000)
        val ttl=d.getLong("expires_at_ms")-d.getLong("sent_at_ms")
        checkBridge(remainingTrustedMs in 1..minOf(ttl,l.getLong("timeout_ms")),BridgeError.EXPIRED)
        val session=scope.getString("session_id");val auth=scope.getString("authorization_ref");val content=p.getString("content_ref")
        val grant=authorizer.resolve(session,auth,content) ?: throw BridgeFailure(BridgeError.UNAUTHORIZED)
        checkBridge(grant.sessionId==session && grant.authorizationRef==auth && grant.contentRef==content && grant.consent && grant.confirmed,BridgeError.UNAUTHORIZED)
        checkBridge(grant.correlationId==j.getString("correlation_id") && grant.cancellationId==j.getString("cancellation_id"),BridgeError.UNAUTHORIZED)
        checkBridge(grant.expiresLocalMs>nowLocalMs,BridgeError.EXPIRED)
        checkBridge(grant.binding.profileId==p.getString("voice_profile_ref"),BridgeError.UNAUTHORIZED)
        checkBridge(grant.binding.packId==p.getString("pack_sha256"),BridgeError.INCOMPATIBLE)
        checkBridge(p.has("model_sha256") && grant.binding.modelSha256==p.getString("model_sha256"),BridgeError.INCOMPATIBLE)
        checkBridge(grant.text.toByteArray(Charsets.UTF_8).size<=l.getLong("max_input_bytes"))
        // UNIFIED has no input-token field. Do not reinterpret an output-token
        // budget as a frontend budget: audio requests must request zero text tokens.
        checkBridge(l.getLong("max_output_tokens")==0L)
        return SpeechJobRequest(j.getString("correlation_id"),session,j.getString("cancellation_id"),grant.text,grant.binding,grant.options,
            minOf(nowLocalMs+remainingTrustedMs,grant.expiresLocalMs))
    }
    fun capability(query: VoiceCapabilityQuery,contentRef: String,nowLocalMs: Long,appAvailable: Boolean=false): VoiceCapabilityReply {
        uuid(contentRef)
        if(query.androidApi<26) return VoiceCapabilityReply(false,BridgeError.INCOMPATIBLE)
        if(!appAvailable) return VoiceCapabilityReply(false,BridgeError.MODULE_ABSENT)
        val g=authorizer.resolve(query.sessionId,query.authorizationRef,contentRef)
        if(g==null || !g.consent || !g.confirmed || g.sessionId!=query.sessionId || g.authorizationRef!=query.authorizationRef || g.contentRef!=contentRef)
            return VoiceCapabilityReply(false,BridgeError.UNAUTHORIZED)
        if(nowLocalMs<0 || g.expiresLocalMs<=nowLocalMs) return VoiceCapabilityReply(false,BridgeError.EXPIRED)
        // Capability lookup alone does not approve a request or extend a grant's deadline.
        return VoiceCapabilityReply(true,null,g.binding)
    }
    fun response(raw: String,pending: SpeechJobRequest,currentSessionId: String,nowLocalMs: Long,allowFixture: Boolean=false): SpeechJobResult {
        val j=UnifiedSchemas.parse(Envelope.RESPONSE,raw)
        checkBridge(nowLocalMs>=0)
        checkBridge(currentSessionId==pending.sessionId && j.getString("correlation_id")==pending.correlationId,BridgeError.INTERRUPTED)
        checkBridge(nowLocalMs<pending.deadlineLocalMs,BridgeError.EXPIRED)
        val p=j.getJSONObject("provenance")
        checkBridge(p.getString("module_id")=="voice" && p.getString("engine_id")==pending.binding.engineId && p.opt("model_sha256")==pending.binding.modelSha256,BridgeError.INCOMPATIBLE)
        checkBridge(p.getString("verification")=="runtime" && !p.isNull("source_commit") || allowFixture && p.getString("verification")=="fixture",BridgeError.UNAUTHORIZED)
        val x=j.getJSONObject("execution");val stopped=if(x.isNull("native_stopped")) null else x.getBoolean("native_stopped")
        if(j.getString("status")=="success") {
            val r=j.getJSONObject("result");checkBridge(r.getString("kind")=="audio")
            return SpeechJobResult(pending.correlationId,pending.sessionId,GenerationOutcome.SUCCEEDED,PlaybackOutcome.NOT_STARTED,null,r.getString("artifact_ref"),r.getString("sha256"),x.getLong("duration_ms"),stopped)
        }
        val code=BridgeError.valueOf(j.getJSONObject("error").getString("code"))
        val outcome=if(j.getString("status") in setOf("refused","unavailable","incompatible","unauthorized")) GenerationOutcome.REFUSED else GenerationOutcome.FAILED
        return SpeechJobResult(pending.correlationId,pending.sessionId,outcome,PlaybackOutcome.NOT_STARTED,code,durationMs=x.getLong("duration_ms"),nativeStopped=stopped)
    }
    fun requireCapability(raw: String,binding: VoiceBinding) {
        val j=UnifiedSchemas.parse(Envelope.CAPABILITY,raw)
        checkBridge(j.getString("module_id")=="voice",BridgeError.INCOMPATIBLE)
        checkBridge(j.getString("state")!="unknown",BridgeError.UNKNOWN)
        checkBridge(j.getString("state")=="available",BridgeError.MODULE_ABSENT)
        val caps=j.getJSONArray("capabilities")
        checkBridge((0 until caps.length()).any { caps.getJSONObject(it).let { c->c.getString("id")=="speech.synthesize" && c.getString("version")=="1.0.0" && c.getBoolean("available") } },BridgeError.INCOMPATIBLE)
        val matches=j.getJSONArray("compatibilities")
        checkBridge((0 until matches.length()).any { matches.getJSONObject(it).let { c->c.getString("engine_id")==binding.engineId && c.getString("model_sha256")==binding.modelSha256 && c.getString("profile_format")=="JVFP1" } },BridgeError.INCOMPATIBLE)
        val env=j.getJSONObject("environment")
        checkBridge(env.getString("platform")=="android" && env.opt("min_android_api")==26 && env.getString("abi") in setOf("arm64-v8a","x86_64"),BridgeError.INCOMPATIBLE)
    }
    fun requireHealth(raw: String) {
        val j=UnifiedSchemas.parse(Envelope.HEALTH,raw);checkBridge(j.getString("module_id")=="voice",BridgeError.INCOMPATIBLE)
        if(j.getString("state")!="available") throw BridgeFailure(BridgeError.valueOf(j.getString("reason_code")))
        // Available is not hardware admission; reservation still requires independent, current guards.
    }
    fun requireLease(raw: String,request: SpeechJobRequest,generation: Long) {
        val j=UnifiedSchemas.parse(Envelope.LEASE,raw)
        checkBridge(j.getString("owner")=="voice" && j.getString("operation_id")==request.correlationId && j.getLong("generation")==generation,BridgeError.UNAUTHORIZED)
        checkBridge(j.getString("decision")=="admitted" && j.getString("resource_class")=="heavy" && j.getString("state")=="running",BridgeError.INSUFFICIENT_RESOURCES)
        checkBridge(!j.getBoolean("cancellation_requested") && !j.getBoolean("effective_end") && !j.getBoolean("release_confirmed"),BridgeError.INTERRUPTED)
        // A valid lease string is only a simulation here, never evidence of Android RAM/global ownership.
    }
}
