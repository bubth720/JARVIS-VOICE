package com.jarvis.voice.sdk

import com.jarvis.voice.expression.*
import com.jarvis.voice.pocket.*
import org.junit.Assert.*
import org.junit.Test

internal fun stylePlan(request: SpeechRequest): LocalProsodyPlan =
    (ProsodyPlannerAdapter.prepare(request.plan,ExpressionStyleProfiles.athena(),{ p ->
        LocalProsodyPlanner.prepareWith(p) { PreparedText(it,longArrayOf(1,2),0) }
    }) as ProsodyPlanningResult.Ready).plan
internal fun styleRequest(): SpeechRequest {
    val r=request("Bonjour à vous.\nLe test est prêt.")
    return r.copy(plan=r.plan.copy(request=r.plan.request.copy(allowSentencePauses=true)))
}
class ExpressionSpeechAdapterTest {
    @Test fun sdkUsesExistingRendererSoftwarePausesAndPublishesOnlyRelativeTiming() {
        var cue: ExpressionTimeline?=null;var calls=0
        val adapter=ExpressionSpeechAdapter(ExpressionRenderPort { r,c ->
            val plan=stylePlan(r)
            val result=ExpressionRenderer.renderWith(plan,c) { _,_,sink ->
                calls++;sink(FloatArray(240) { .1f });GenerationMetrics(1.0,1.0,1.0,.01,1,2,true)
            }
            PlannedExpressionRender(plan,result).also { assertFalse(it.toString().contains("Bonjour")) }
        },AvatarTimingCuePort { cue=it })
        val output=RecordingOutput();val result=SpeechSessionRepository(adapter).open(output).speak(SpeechQueue(listOf(styleRequest())))
        assertTrue(result is SpeechOutcome.Completed);assertEquals(2,calls);assertEquals(12480,output.samples.size)
        assertEquals(12480,cue!!.samples);assertEquals("SEGMENT_BOUNDARIES_ONLY",cue!!.qualification)
    }
    @Test fun cancellationBeforeOrAfterSoftwarePauseNeverPublishesLateTimelineOrAudio() {
        for(cancelOn in listOf(1,2)) {
            var cues=0;var calls=0
            val adapter=ExpressionSpeechAdapter(ExpressionRenderPort { r,c ->
                val plan=stylePlan(r)
                val result=ExpressionRenderer.renderWith(plan,c) { _,_,sink ->
                    calls++;if(calls==cancelOn) c.cancel()
                    sink(FloatArray(240));GenerationMetrics(1.0,1.0,1.0,.01,1,2,true)
                }
                PlannedExpressionRender(plan,result)
            },AvatarTimingCuePort { cues++ })
            val output=RecordingOutput()
            assertEquals(SpeechOutcome.Refused(SpeechRefusal.CANCELLED),SpeechSessionRepository(adapter).open(output).speak(SpeechQueue(listOf(styleRequest()))))
            assertEquals(0,cues);assertEquals(0,output.played);assertEquals(cancelOn,calls)
        }
    }
    @Test fun failedAvatarListenerDoesNotChangeAudioOrStartAnotherEngine() {
        val adapter=ExpressionSpeechAdapter(ExpressionRenderPort { r,c ->
            val plan=stylePlan(r)
            PlannedExpressionRender(plan,ExpressionRenderer.renderWith(plan,c) { _,_,sink ->
                sink(FloatArray(240));GenerationMetrics(1.0,1.0,1.0,.01,1,2,true)
            })
        },AvatarTimingCuePort { error("synthetic disconnected avatar") })
        assertTrue(SpeechSessionRepository(adapter).open(RecordingOutput()).speak(SpeechQueue(listOf(styleRequest()))) is SpeechOutcome.Completed)
    }
    @Test fun changedTextCannotBeDeliveredAsRequestedTimingOrAudio() {
        var cues=0
        val adapter=ExpressionSpeechAdapter(ExpressionRenderPort { r,c ->
            val other=r.copy(plan=r.plan.copy(request=r.plan.request.copy(utteranceText="Autre phrase.\nLe test est prêt.")))
            val p=stylePlan(other)
            PlannedExpressionRender(p,ExpressionRenderer.renderWith(p,c) { _,_,sink -> sink(FloatArray(240));GenerationMetrics(1.0,1.0,1.0,.01,1,2,true) })
        },AvatarTimingCuePort { cues++ })
        val output=RecordingOutput();val repo=SpeechSessionRepository(adapter)
        assertEquals(SpeechOutcome.Refused(SpeechRefusal.ENGINE_FAILED),repo.open(output).speak(SpeechQueue(listOf(styleRequest()))))
        assertEquals(0,cues);assertEquals(0,output.played);assertTrue(repo.isQuarantined)
    }
}
