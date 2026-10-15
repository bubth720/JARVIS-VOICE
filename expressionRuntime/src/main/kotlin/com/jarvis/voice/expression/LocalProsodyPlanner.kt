package com.jarvis.voice.expression

import com.jarvis.voice.identity.VoiceIdentityVault
import com.jarvis.voice.pocket.*
import org.json.JSONObject
import java.io.File

/** Boundaries are supplied by the human/CORE as NEWLINES, never inferred from punctuation. */
data class LocalProsodyPlan(val expression: VoiceExpressionPlan, val segments: List<String>, val pauseMs: Int,
    val preparedTexts: List<String>, val tokenCounts: List<Int>, val effects: List<ExpressionEffect>) {
    fun summary()="${expression.intent} : ${expression.status}\n"+
        (expression.effects.filter { it.field!="pause" }+effects).joinToString("\n") { "${it.field} : ${it.status} — ${it.reason}" }+
        "\n${segments.size} phrase(s), pause $pauseMs ms ; même profil, graine et paramètres."
}
object LocalProsodyPlanner {
    fun prepare(plan: VoiceExpressionPlan,frontend: FrenchFrontend)=prepareWith(plan,frontend::prepare)
    fun prepareWith(plan: VoiceExpressionPlan,prepareText: (String)->PreparedText): LocalProsodyPlan {
        val r=plan.request
        val pause=r.pauseMs ?: 0
        val split=r.allowSentencePauses && pause>0
        val segments=if(split) r.utteranceText.split('\n') else listOf(r.utteranceText)
        if(split) {
            require(segments.size in 2..3 && segments.all { it.isNotBlank() }) { "Pauses : fournir 2 ou 3 phrases complètes séparées par un retour à la ligne" }
            // Quotes/parentheses and abbreviations cannot be disambiguated without a linguistic parser.
            require(segments.all { s ->
                val t=s.trim()
                t.last() in ".!?…" && t.first().isUpperCase() &&
                    t.none { it in "\"«»“”()[]" } &&
                    t.dropLast(1).none { it in ".!?…" } &&
                    t.split(Regex("\\s+")).size>=2
            }) { "Frontière ambiguë : conserver la phrase entière sans pause ajoutée" }
            require(segments.joinToString("\n")==r.utteranceText) { "Contenu modifié refusé" }
        }
        val prepared=segments.map(prepareText) // 50-token limit, no truncation
        require(prepared.all { it.tokens.size in 1..50 }) { "Phrase trop longue : maximum 50 jetons" }
        val effects=buildList {
            if(r.pauseMs!=null) add(if(split) ExpressionEffect("pause","applied","Silence explicite uniquement entre phrases complètes")
                else ExpressionEffect("pause",if(pause==0) "applied" else "unsupported",if(pause==0) "Aucun silence ajouté" else "Autorisation des pauses inter-phrases absente"))
            if(prepared.map { it.text }!=segments) add(ExpressionEffect("frontend","baseline","Normalisation française 005G inchangée ; aucune réécriture d'expression"))
        }
        return LocalProsodyPlan(plan,segments,if(split) pause else 0,prepared.map { it.text },prepared.map { it.tokens.size },effects)
    }
    /** No punctuation edits are enabled. Even a benign proposed rewrite is refused. */
    fun requireExactText(original: String,proposed: String) { require(original==proposed) { "Modification du texte refusée" } }
}
data class ExpressionRenderResult(val pcm: FloatArray,val metrics: List<GenerationMetrics>,val wallMs: Double)

/** Deferred complete PCM; one backend, original reset per whole utterance, unchanged seed/settings. */
object ExpressionRenderer {
    fun render(pack: File,voice: PersonalVoiceProfile,plan: LocalProsodyPlan,cancel: CancellationToken): ExpressionRenderResult {
        cancel.check()
        require(voice.packId==plan.expression.binding.packId &&
            VoiceIdentityVault.conditioningSha(voice)==plan.expression.binding.conditioningSha256) { "Conditionnement du plan incompatible" }
        return QualityEngine(pack,voice,loadCancel=cancel,sampling=plan.expression.sampling).use { engine ->
            renderWith(plan,cancel) { text,seed,sink -> engine.generate(text,cancel,seed,true,sink) }
        }
    }
    /** Public seam for bounded/cancellation tests; production always supplies the unchanged QualityEngine. */
    fun renderWith(plan: LocalProsodyPlan,cancel: CancellationToken,
        generate: (String,Long,(FloatArray)->Unit)->GenerationMetrics): ExpressionRenderResult {
        val started=System.nanoTime();val chunks=mutableListOf<FloatArray>();var total=0;val metrics=mutableListOf<GenerationMetrics>()
        fun append(pcm: FloatArray) { cancel.check();require(pcm.isNotEmpty() && pcm.all { it.isFinite() }) { "PCM non fini ou vide" };require(total+pcm.size<=480000) { "Limite totale de 20 secondes, pauses incluses ; audio incomplet refusé" };chunks.add(pcm.copyOf());total+=pcm.size }
        for((i,text) in plan.segments.withIndex()) {
            cancel.check();if(i>0 && plan.pauseMs>0) append(FloatArray(plan.pauseMs*24))
            val m=generate(text,plan.expression.seed,::append);require(m.eosReached) { "Fin de phrase non fiable ; audio incomplet refusé" };metrics.add(m)
        }
        cancel.check();require(total>0) { "Aucun audio" };val pcm=FloatArray(total);var offset=0
        chunks.forEach { it.copyInto(pcm,offset);offset+=it.size };chunks.clear()
        return ExpressionRenderResult(pcm,metrics,(System.nanoTime()-started)/1e6)
    }
    fun prepare(pack: File,plan: VoiceExpressionPlan)=LocalProsodyPlanner.prepare(plan,FrenchFrontend(JSONObject(File(pack,"tokenizer.json").readText())))
}
