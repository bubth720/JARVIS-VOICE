package com.jarvis.voice.bridge
import org.json.*
import org.junit.Assert.*
import org.junit.Test
class SchemaTests {
    @Test fun fivePinnedSchemasValidateAndMapOnlyClosedSpeechSubset() {
        val docs=listOf(Envelope.REQUEST to Fixtures.request(),Envelope.RESPONSE to Fixtures.response(),Envelope.CAPABILITY to Fixtures.capability(),Envelope.HEALTH to Fixtures.health(),Envelope.LEASE to Fixtures.lease())
        for((kind,doc) in docs) { UnifiedSchemas.parse(kind,doc.toString());Fixtures.reject { UnifiedSchemas.parse(kind,JSONObject(doc.toString()).put("unknown_field",true).toString()) } }
        val m=Fixtures.mapper();m.requireCapability(Fixtures.capability().toString(),Fixtures.binding);m.requireHealth(Fixtures.health().toString());m.requireLease(Fixtures.lease().toString(),Fixtures.job(),1)
    }
    @Test fun unknownNestedFieldsAndClosedVersionsFail() {
        for(k in listOf("scope","limits","deadline","payload")) { val j=Fixtures.request();j.getJSONObject(k).put("execute","rm -rf /");Fixtures.reject { UnifiedSchemas.parse(Envelope.REQUEST,j.toString()) } }
        for(k in listOf("protocol_version","contract_version")) { val j=Fixtures.request().put(k,"2.0");Fixtures.reject { UnifiedSchemas.parse(Envelope.REQUEST,j.toString()) } }
    }
    @Test fun duplicateEscapedKeysPermissiveSyntaxDepthAndTrailingBytesFail() {
        val raw=Fixtures.request().toString()
        val bad=listOf("{\"protocol_version\":\"1.0\",$raw".replace("{{","{"),raw.dropLast(1)+",\"or\\u0069gin\":\"core\"}",raw+" true",raw.replace("\"core\"","'core'"),raw.replace("\"origin\":","origin:"),raw.dropLast(1)+",}","{\"x\":"+"[".repeat(20)+"0"+"]".repeat(20)+"}"," ".repeat(32769))
        for(s in bad) Fixtures.reject { UnifiedSchemas.parse(Envelope.REQUEST,s) }
    }
    @Test fun booleansAndFloatingNumbersAreNeverTransportIntegers() {
        for(value in listOf<Any>(true,1.0,JSONObject.NULL,"1")) { val j=Fixtures.request();j.getJSONObject("limits").put("timeout_ms",value);Fixtures.reject { UnifiedSchemas.parse(Envelope.REQUEST,j.toString()) } }
        Fixtures.reject { UnifiedSchemas.parse(Envelope.REQUEST,Fixtures.request().toString().replace("90000","90000.0")) }
    }
    @Test fun unknownStateCannotBeAvailableAndNoFakeQualifiedCapability() {
        Fixtures.reject { UnifiedSchemas.parse(Envelope.HEALTH,Fixtures.health().put("state","unknown").toString()) }
        val cap=Fixtures.capability();cap.getJSONArray("capabilities").getJSONObject(0).put("qualification","unknown");Fixtures.reject { UnifiedSchemas.parse(Envelope.CAPABILITY,cap.toString()) }
        Fixtures.reject(BridgeError.UNKNOWN) { Fixtures.mapper().requireCapability(Fixtures.capability().put("state","unknown").put("capabilities",JSONArray()).toString(),Fixtures.binding) }
    }
    @Test fun resultErrorAreExclusiveAndNoPrivateLogFlag() {
        val j=Fixtures.response().put("error",JSONObject().put("code","UNKNOWN").put("retryable",false));Fixtures.reject { UnifiedSchemas.parse(Envelope.RESPONSE,j.toString()) }
        val k=Fixtures.response();k.getJSONObject("execution").put("data_logged",true);Fixtures.reject { UnifiedSchemas.parse(Envelope.RESPONSE,k.toString()) }
    }
    @Test fun releasedLeaseRequiresAllProofsAndGenerationIsFenced() {
        val l=Fixtures.lease().put("state","released");Fixtures.reject { UnifiedSchemas.parse(Envelope.LEASE,l.toString()) }
        l.put("effective_end",true).put("release_confirmed",true).put("model_resident",false);UnifiedSchemas.parse(Envelope.LEASE,l.toString())
        Fixtures.reject(BridgeError.UNAUTHORIZED) { Fixtures.mapper().requireLease(Fixtures.lease().toString(),Fixtures.job(),2) }
        Fixtures.reject(BridgeError.INSUFFICIENT_RESOURCES) { Fixtures.mapper().requireLease(l.toString(),Fixtures.job(),1) }
    }
    @Test fun invalidUtcUuidUnicodeAndOversizedObjectsFail() {
        Fixtures.reject { UnifiedSchemas.parse(Envelope.HEALTH,Fixtures.health().put("checked_at","2026-10-17T00:00:00+00:00").toString()) }
        Fixtures.reject { UnifiedSchemas.parse(Envelope.REQUEST,Fixtures.request().put("correlation_id","1-1-1-1-1").toString()) }
        Fixtures.reject { UnifiedSchemas.parse(Envelope.REQUEST,Fixtures.request().toString().replace("\"core\"","\"\\ud800\"")) }
        Fixtures.reject { UnifiedSchemas.parse(Envelope.REQUEST,"{\"x\":\""+"x".repeat(32768)+"\"}") }
    }
    @Test fun refusedResultIsDistinctAndStatusErrorMismatchOrUnknownApiCannotSucceed() {
        val j=Fixtures.response().put("status","unauthorized").put("result",JSONObject.NULL).put("error",JSONObject().put("code","UNAUTHORIZED").put("retryable",false))
        assertEquals(GenerationOutcome.REFUSED,Fixtures.mapper().response(j.toString(),Fixtures.job(),Fixtures.id(3),200,true).generation)
        j.getJSONObject("error").put("code","BUSY");Fixtures.reject { UnifiedSchemas.parse(Envelope.RESPONSE,j.toString()) }
        val cap=Fixtures.capability();cap.getJSONObject("environment").put("min_android_api",JSONObject.NULL)
        Fixtures.reject(BridgeError.INCOMPATIBLE) { Fixtures.mapper().requireCapability(cap.toString(),Fixtures.binding) }
        Fixtures.reject { UnifiedSchemas.parse(Envelope.HEALTH,Fixtures.health().put("checked_at","2026-02-30T00:00:00Z").toString()) }
    }
}
