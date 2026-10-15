package com.jarvis.voice.expression
import com.jarvis.voice.identity.*
import com.jarvis.voice.pocket.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ExpressionContractTests {
    private val id=VoiceIdentityId("00000000-0000-0000-0000-000000000001")
    private val binding=ExpressionVoiceBinding(id,"00000000-0000-0000-0000-000000000002","a".repeat(64),"b".repeat(64),PocketIdentity.ENGINE,PocketIdentity.packId)
    private fun reject(f: ()->Unit) { try { f();fail("Doit refuser") } catch(_: IllegalArgumentException) {} }
    @Test fun roundTripAndNoPersonaVoiceCoupling() {
        val request=CoreVoiceRequestV1("Ne prenez pas le train de 12 h 30.",id,VoicePersonaId("00000000-0000-0000-0000-000000000003"),"SERIOUS",.7,2,1.1,250,true)
        assertEquals(request,CoreVoiceRequestV1.parse(request.json()))
        val p=ExpressionPolicy.plan(request,binding,QualitySampling(3,.3),42)
        assertEquals(binding,p.binding);assertEquals(QualitySampling(3,.3),p.sampling)
        assertEquals(setOf("intent","intensity","speed"),p.effects.filter { it.status=="unsupported" }.map { it.field }.toSet())
        val persona=VoicePersonaDescriptor(request.personaId!!,"JARVIS",humourAllowed=true)
        assertEquals("JARVIS",persona.title);assertEquals(binding,p.binding)
    }
    @Test fun unknownIntentDeterministicAndNeverApplied() {
        val r=CoreVoiceRequestV1("Bonjour.",id,expressionIntent="INCONNU")
        val a=ExpressionPolicy.plan(r,binding,QualitySampling(),42)
        assertEquals(a,ExpressionPolicy.plan(r,binding,QualitySampling(),42));assertEquals(VoiceExpressionIntent.NEUTRAL,a.intent)
        assertTrue(a.effects.any { it.status=="degraded" })
    }
    @Test fun strictValidationAndNoPayloadEcho() {
        val base=CoreVoiceRequestV1("Texte confidentiel.",id).json()
        val mutations=listOf<(JSONObject)->Unit>(
            { it.put("version",2) },{ it.put("priority",1.5) },{ it.put("priority","1") },
            { it.put("intensity",2) },{ it.put("speedRequested",0) },{ it.put("pauseMs",1501) },
            { it.put("unknown",true) },{ it.getJSONObject("flags").put("bypassThermal",true) },
            { it.getJSONObject("flags").put("allowSentencePauses","true") },{ it.put("identityId","secret-personal-path") },
            { it.put("utteranceText","<s>test") },{ it.put("utteranceText","x".repeat(301)) })
        for(change in mutations) { val j=JSONObject(base);change(j);try { CoreVoiceRequestV1.parse(j.toString());fail("Refus attendu") } catch(_: Exception) {} }
        for(bad in listOf(base.replace("\"version\":1", "\"version\":1,\"version\":1"),base.replace("\"version\"", "'version'"),base.replace("\"version\":1", "\"version\":01"))) reject { CoreVoiceRequestV1.parse(bad) }
        reject { CoreVoiceRequestV1.parse(base+" ignored") };reject { CoreVoiceRequestV1.parse(" ".repeat(9000)) }
        reject { CoreVoiceRequestV1("Bonjour",id,intensity=Double.NaN) }
        reject { CoreVoiceRequestV1("Bonjour",id,speedRequested=Double.POSITIVE_INFINITY) }
    }
    @Test fun identityAndPriorityCannotBypassGuards() {
        val r=CoreVoiceRequestV1("Bonjour.",id,expressionIntent="URGENT",priority=3)
        for(p in 0..3) reject { ExpressionPolicy.plan(r,binding,QualitySampling(),42,runningPriority=p) }
        reject { ExpressionPolicy.plan(r.copy(identityId=VoiceIdentityId("00000000-0000-0000-0000-000000000009")),binding,QualitySampling(),42) }
        val result=ExpressionPolicy.plan(r,binding,QualitySampling(),42)
        assertTrue(result.effects.any { it.field=="priority" && it.status=="metadata" })
        assertFalse(result.capabilities.supported.contains("intent"))
    }
}
