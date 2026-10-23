package com.jarvis.voice.expression

import com.jarvis.voice.identity.*
import com.jarvis.voice.pocket.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.UUID

class ExpressionStyleTests {
    private val text="Bonjour à vous.\nLe test est prêt."
    private fun policy(allow: Boolean=true,pause: Int?=null,intensity: Double?=null,speed: Double?=null,intent: String="NEUTRAL"): VoiceExpressionPlan {
        val id=VoiceIdentityId("11111111-1111-4111-8111-111111111111")
        return ExpressionPolicy.plan(CoreVoiceRequestV1(text,id,expressionIntent=intent,intensity=intensity,speedRequested=speed,
            pauseMs=pause,allowSentencePauses=allow),ExpressionVoiceBinding(id,"22222222-2222-4222-8222-222222222222","a".repeat(64),"b".repeat(64),PocketIdentity.ENGINE,PocketIdentity.packId),QualitySampling(),42)
    }
    private fun prepare(p: VoiceExpressionPlan)=LocalProsodyPlanner.prepareWith(p) { PreparedText(it,longArrayOf(1,2),0) }
    private fun planned(p: VoiceExpressionPlan,style: ExpressionStyleProfile) =
        (ProsodyPlannerAdapter.prepare(p,style,::prepare) as ProsodyPlanningResult.Ready).plan
    private fun rendered(p: LocalProsodyPlan)=ExpressionRenderer.renderWith(p,CancellationToken()) { _,_,sink ->
        sink(FloatArray(240) { .12345678f });GenerationMetrics(1.0,1.0,1.0,.01,1,2,true)
    }
    @Test fun neutralAndAthenaPlansDifferReproduciblyWithoutTextOrSamplingChange() {
        val original=policy();val a=planned(original,ExpressionStyleProfiles.neutral());val b=planned(original,ExpressionStyleProfiles.athena())
        assertEquals(1,a.segments.size);assertEquals(2,b.segments.size);assertEquals(500,b.pauseMs)
        assertEquals(b,planned(original,ExpressionStyleProfiles.athena()))
        assertEquals(original.request.utteranceText,b.expression.request.utteranceText)
        assertEquals(original.sampling,b.expression.sampling);assertEquals(original.seed,b.expression.seed)
        assertNull(original.request.pauseMs)
    }
    @Test fun styleNeverGrantsPauseConsentOrOverridesExplicitValue() {
        assertEquals(0,planned(policy(allow=false),ExpressionStyleProfiles.athena()).pauseMs)
        assertEquals(250,planned(policy(pause=250),ExpressionStyleProfiles.athena()).pauseMs)
        assertEquals(0,planned(policy(pause=0),ExpressionStyleProfiles.athena()).pauseMs)
    }
    @Test fun intensityRhythmAndRequiredNativeEmotionAreRefusedBeforePreparation() {
        var calls=0
        val result=ProsodyPlannerAdapter.prepare(policy(intensity=.5,speed=1.2,intent="CURIOUS"),ExpressionStyleProfiles.athena(),{ calls++;prepare(it) },true)
        assertEquals(setOf("intensity","rhythm","emotion"),(result as ProsodyPlanningResult.Unsupported).fields)
        assertEquals("STYLE_UNSUPPORTED",result.code);assertEquals(0,calls)
    }
    @Test fun publicPersonaMappingOnlyChangesSoftwareLabelsAndDoesNotRewriteTutoiement() {
        val elegant=PublicPersonaStyleIntent(ExpressionContext.WORK,PublicPersonaStyle.ELEGANT,true)
        assertEquals(250,ExpressionStyleProfiles.fromPublicIntent(elegant).pauseMs)
        assertEquals(0,ExpressionStyleProfiles.athena(ExpressionContext.URGENT).pauseMs)
        assertEquals(0,ExpressionStyleProfiles.fromPublicIntent(elegant.copy(style=PublicPersonaStyle.PLAIN)).pauseMs)
        assertEquals(ExpressionStyleProfiles.fromPublicIntent(elegant),ExpressionStyleProfiles.fromPublicIntent(elegant.copy(tutoiement=false)))
    }
    @Test fun existingTokenAndAmbiguousBoundaryRefusalsRemainEffective() {
        assertThrows(IllegalArgumentException::class.java) {
            ProsodyPlannerAdapter.prepare(policy(),ExpressionStyleProfiles.athena(),{ p -> LocalProsodyPlanner.prepareWith(p) { PreparedText(it,LongArray(51),0) } })
        }
        val p=policy();val ambiguous=p.copy(request=p.request.copy(utteranceText="Bonjour (à vous).\nLe test est prêt."))
        assertThrows(IllegalArgumentException::class.java) { planned(ambiguous,ExpressionStyleProfiles.athena()) }
    }
    @Test fun timelineUsesExactRenderedSampleOffsetsAndActualZeroPause() {
        val p=planned(policy(),ExpressionStyleProfiles.athena());val rendered=rendered(p)
        val timeline=ExpressionTimeline.fromRendered(UUID.randomUUID().toString(),p,rendered)
        assertEquals(12480,timeline.samples)
        assertEquals(listOf(ExpressionCueKind.SPEECH,ExpressionCueKind.PAUSE,ExpressionCueKind.SPEECH),timeline.cues.map { it.kind })
        assertEquals(listOf(0,240,12240),timeline.cues.map { it.startSample })
        assertTrue(rendered.pcm.sliceArray(240 until 12240).all { it==0f })
        assertEquals("PCM_SAMPLE_OFFSET",timeline.clock);assertFalse(timeline.phonemesAvailable)
    }
    @Test fun inconsistentDurationOrIncompleteEosCannotProduceTimingProof() {
        val p=planned(policy(),ExpressionStyleProfiles.athena());val result=rendered(p)
        assertThrows(IllegalArgumentException::class.java) { ExpressionTimeline.fromRendered(UUID.randomUUID().toString(),p,result.copy(pcm=result.pcm.copyOf(12479))) }
        assertThrows(IllegalArgumentException::class.java) { ExpressionTimeline.fromRendered(UUID.randomUUID().toString(),p,result.copy(metrics=result.metrics.map { it.copy(eosReached=false) })) }
    }
    @Test fun comparisonReusesExactRawPcmAndVaultRevocationWithoutTouchingOtherCaches() {
        val root=Files.createTempDirectory("style-comparison-").toFile()
        try {
            val vault=VoiceIdentityVault(File(root,"vault"));val id=SyntheticVault.identity(vault);val ref=SyntheticVault.reference(vault,id)
            val pid=vault.saveProfile(id,ref,SyntheticVault.profile(),CancellationToken());vault.activate(id,pid)
            val profile=vault.list().single().profiles.single();val c=CancellationToken()
            val base=ExpressionPolicy.plan(CoreVoiceRequestV1(text,id,allowSentencePauses=true),ExpressionVoiceBinding.from(profile),QualitySampling(),42)
            val a=planned(base,ExpressionStyleProfiles.neutral());val b=planned(base,ExpressionStyleProfiles.athena())
            val store=AudibleComparisonStore(File(root,"new-style-cache"));val old=ExpressionComparisonStore(File(root,"old-cache"))
            val raw=floatArrayOf(.12345678f,-.33333334f,Float.MIN_VALUE,-0f)
            old.save("A",id,profile,text,42,QualitySampling(),raw,c)
            store.save("A",profile,a,raw,c);store.save("B",profile,b,raw,c)
            assertTrue(raw.map { it.toRawBits() }==store.load("B",vault,c).map { it.toRawBits() })
            store.delete();assertArrayEquals(raw,old.load("A",vault,c),0f)
            store.save("A",profile,a,raw,c);vault.deactivate(id,pid)
            assertThrows(IllegalArgumentException::class.java) { store.load("A",vault,c) }
        } finally { root.deleteRecursively() }
    }
}
