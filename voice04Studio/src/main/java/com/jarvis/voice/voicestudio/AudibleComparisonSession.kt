package com.jarvis.voice.voicestudio
import com.jarvis.voice.expression.*
import com.jarvis.voice.pocket.CancellationToken

/** Two <=20s PCM arrays in private RAM, tied to one exact voice/text/seed/sampler context. */
class AudibleComparisonSession(private val binding: ExpressionVoiceBinding,source: FloatArray): AutoCloseable {
    private val neutral=source.copyOf().also { require(it.size in 1..480000 && it.all(Float::isFinite)) }
    private var effect: EmotionPcmResult?=null
    private val presented=mutableSetOf<String>()
    private var closed=false
    var humanReviewRecorded=false;private set
    var selected: VoiceEmotionIntent?=null;private set
    fun apply(plan: EmotionRenderPlan,cancel: CancellationToken): EmotionPcmResult {
        check(!closed);effect?.pcm?.fill(0f);presented.remove("B");humanReviewRecorded=false
        return BoundedPcmProsody.render(neutral,plan,cancel).also { effect=it;selected=plan.effective }
    }
    fun <T> withPcm(side: String,current: ExpressionVoiceBinding,action: (FloatArray)->T): T {
        check(!closed && current==binding) { "COMPARISON_CONTEXT_CHANGED" };require(side in setOf("A","B"))
        return action(if(side=="A") neutral else requireNotNull(effect).pcm)
    }
    fun markPresented(side: String,current: ExpressionVoiceBinding) { withPcm(side,current) { presented.add(side) } }
    fun review(heardBoth: Boolean,current: ExpressionVoiceBinding) {
        check(!closed && current==binding && presented.containsAll(listOf("A","B")) && heardBoth) { "HUMAN_LISTENING_REQUIRED" }
        humanReviewRecorded=true // An observation, not a backend capability or EMOTION_AUDIBLE_VALIDATED.
    }
    override fun close() { if(!closed) { closed=true;neutral.fill(0f);effect?.pcm?.fill(0f);effect=null;presented.clear() } }
    override fun toString()="AudibleComparisonSession(samples=${neutral.size},humanReview=$humanReviewRecorded)"
}
