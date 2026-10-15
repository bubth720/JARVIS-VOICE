package com.jarvis.voice.expression
import com.jarvis.voice.identity.*
import com.jarvis.voice.pocket.*
import org.junit.Assert.*
import org.junit.Test

class ProsodyTests {
    private val id=VoiceIdentityId("00000000-0000-0000-0000-000000000001")
    private val binding=ExpressionVoiceBinding(id,"00000000-0000-0000-0000-000000000002","a".repeat(64),"b".repeat(64),PocketIdentity.ENGINE,PocketIdentity.packId)
    private fun plan(text: String,intent: String="NEUTRAL",pause: Int?=null,allow: Boolean=false)=ExpressionPolicy.plan(CoreVoiceRequestV1(text,id,expressionIntent=intent,pauseMs=pause,allowSentencePauses=allow),binding,QualitySampling(3),42)
    private fun prepare(p: VoiceExpressionPlan)=LocalProsodyPlanner.prepareWith(p) { PreparedText(it,LongArray(it.split(' ').size),3) }
    private fun m()=GenerationMetrics(0.0,1.0,1.0,.01,1,1,true)
    @Test fun longSentenceAndMeaningAreNeverEditedOrSplitByIntent() {
        val text="Le docteur Martin dit : « Ne prenez pas le train de 12 h 30, car il ne s'arrête pas à Paris. »"
        for(intent in VoiceExpressionIntent.entries) {
            val p=prepare(plan(text,intent.name));assertEquals(listOf(text),p.segments);assertEquals(binding,p.expression.binding);assertEquals(QualitySampling(3),p.expression.sampling)
        }
        for(changed in listOf(text.replace("pas ",""),text.replace("12","13"),text.replace("Martin","Marie"),text+"!")) SyntheticVault.reject { LocalProsodyPlanner.requireExactText(text,changed) }
    }
    @Test fun explicitWholeSentencePausesOnlyAndNoImplicitPermission() {
        val p=prepare(plan("Bonjour à tous.\nNous sommes prêts.","CONVERSATION",250,true))
        assertTrue(p.expression.capabilities.supported.isEmpty());assertEquals(setOf("pause"),p.capabilities.supported);assertEquals(setOf("intent"),p.unsupportedEffects.map { it.field }.toSet())
        assertEquals(2,p.segments.size);assertEquals(250,p.pauseMs);assertTrue(p.effects.any { it.field=="pause" && it.status=="applied" })
        val notAllowed=prepare(plan("Bonjour à tous.\nNous sommes prêts.",pause=250));assertEquals(1,notAllowed.segments.size);assertEquals(0,notAllowed.pauseMs);assertTrue(notAllowed.capabilities.supported.isEmpty());assertEquals(setOf("pause"),notAllowed.unsupportedEffects.map { it.field }.toSet())
        for(bad in listOf("Bonjour.\n", "M. Martin arrive.\nNous sommes prêts.","Il est 12.30 h.\nNous sommes prêts.","« Bonjour à tous.\nNous sommes prêts. »", "Bonjour,\nNous sommes prêts.")) SyntheticVault.reject { prepare(plan(bad,pause=500,allow=true)) }
    }
    @Test fun rendererPreservesExactFloatPcmAndInsertsOnlyRequestedSilence() {
        val p=prepare(plan("Bonjour à tous.\nNous sommes prêts.",pause=250,allow=true))
        val input=floatArrayOf(.12f,-.02f);var calls=0
        val out=ExpressionRenderer.renderWith(p,CancellationToken()) { text,seed,sink -> assertEquals(p.segments[calls++],text);assertEquals(42L,seed);sink(input);m() }
        assertEquals(2,calls);assertEquals(6004,out.pcm.size);assertArrayEquals(input,out.pcm.copyOfRange(0,2),0f);assertTrue(out.pcm.copyOfRange(2,6002).all { it==0f });assertArrayEquals(input,out.pcm.copyOfRange(6002,6004),0f)
        val baseline=ExpressionRenderer.renderWith(prepare(plan("Bonjour à tous.")),CancellationToken()) { _,_,sink -> sink(input);m() }
        assertArrayEquals(input,baseline.pcm,0f)
    }
    @Test fun boundsEosAndCancellationNeverDeliverPartialSuccess() {
        val p=prepare(plan("Bonjour à tous."))
        SyntheticVault.reject { p.copy(segments=listOf("Texte modifié.")) }
        SyntheticVault.reject { p.copy(pauseMs=500) }
        SyntheticVault.reject { ExpressionRenderer.renderWith(p,CancellationToken()) { _,_,sink -> sink(FloatArray(480001));m() } }
        SyntheticVault.reject { ExpressionRenderer.renderWith(p,CancellationToken()) { _,_,sink -> sink(floatArrayOf(Float.NaN));m() } }
        SyntheticVault.reject { ExpressionRenderer.renderWith(p,CancellationToken()) { _,_,sink -> sink(floatArrayOf(.1f));m().copy(eosReached=false) } }
        SyntheticVault.reject { ExpressionRenderer.renderWith(p,CancellationToken().also { it.cancel() }) { _,_,_ -> fail("Backend must not start");m() } }
        val c=CancellationToken();SyntheticVault.reject { ExpressionRenderer.renderWith(p,c) { _,_,sink -> sink(floatArrayOf(.1f));c.cancel();m() } }
        SyntheticVault.reject { LocalProsodyPlanner.prepareWith(plan("Bonjour à tous.")) { PreparedText(it,LongArray(51),3) } }
    }
}
