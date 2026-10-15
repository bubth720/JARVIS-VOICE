package com.jarvis.voice.expression

import com.jarvis.voice.identity.*
import com.jarvis.voice.identity.VoiceProfile
import com.jarvis.voice.pocket.*
import org.json.JSONObject
import java.util.UUID

/** Conversational preferences only. Neither tensors nor an implicit voice selection. */
data class VoicePersonaDescriptor(val id: VoicePersonaId, val title: String,
    val polite: Boolean=true, val concise: Boolean=false, val humourAllowed: Boolean=false) {
    init { require(title.length in 1..80 && title.none { it.isISOControl() }) { "Titre de persona invalide" } }
}
enum class VoiceExpressionIntent { INFORMATIVE, CONVERSATION, SERIOUS, URGENT, CURIOUS, SURPRISED, HUMOROUS, NEUTRAL }

/** A strict, closed v1 contract. Unknown intent alone falls back; unknown fields/flags fail closed. */
data class CoreVoiceRequestV1(val utteranceText: String, val identityId: VoiceIdentityId,
    val personaId: VoicePersonaId?=null, val expressionIntent: String="NEUTRAL", val intensity: Double?=null,
    val priority: Int=0, val speedRequested: Double?=null, val pauseMs: Int?=null,
    val allowSentencePauses: Boolean=false) {
    init {
        require(utteranceText.length in 1..300 && utteranceText.isNotBlank() &&
            utteranceText.none { it.isISOControl() && it!='\n' } &&
            !Regex("<(?:unk|s|/s|pad)>").containsMatchIn(utteranceText)) { "Texte invalide ou jeton réservé" }
        require(expressionIntent.length in 1..40 && expressionIntent.all { it in 'A'..'Z' || it=='_' }) { "Intention invalide" }
        require(priority in 0..3) { "Priorité invalide" }
        require(intensity==null || intensity.isFinite() && intensity in 0.0..1.0) { "Intensité invalide" }
        require(speedRequested==null || speedRequested.isFinite() && speedRequested in 0.5..2.0) { "Vitesse invalide" }
        require(pauseMs==null || pauseMs in 0..1500) { "Pause invalide" }
    }
    fun json(): String=JSONObject().put("version",1).put("utteranceText",utteranceText).put("identityId",identityId.value)
        .put("personaId",personaId?.value ?: JSONObject.NULL).put("expressionIntent",expressionIntent)
        .put("intensity",intensity ?: JSONObject.NULL).put("priority",priority)
        .put("speedRequested",speedRequested ?: JSONObject.NULL).put("pauseMs",pauseMs ?: JSONObject.NULL)
        .put("flags",JSONObject().put("allowSentencePauses",allowSentencePauses)).toString()
    companion object {
        private val keys=setOf("version","utteranceText","identityId","personaId","expressionIntent","intensity","priority","speedRequested","pauseMs","flags")
        fun parse(raw: String): CoreVoiceRequestV1 = try { decode(raw) } catch(_: Exception) {
            throw IllegalArgumentException("Contrat v1 invalide : version, champs, types ou limites incompatibles")
        }
        private fun decode(raw: String): CoreVoiceRequestV1 {
            require(raw.toByteArray(Charsets.UTF_8).size<=8192 && raw.count { it=='{' || it=='[' }<=4) { "Contrat trop grand ou profond" }
            // JSONTokener in org.json accepts trailing data; prohibit it explicitly.
            val tok=org.json.JSONTokener(raw);val j=JSONObject(tok);require(tok.nextClean()=='\u0000') { "Données après le contrat" }
            require(j.keys().asSequence().toSet()==keys) { "Champs v1 incompatibles" }
            fun number(key: String): Double? { if(j.isNull(key)) return null;val n=j.get(key);require(n is Number) { "Nombre requis" };return n.toDouble() }
            fun integer(key: String): Int { val n=number(key);require(n!=null && n.isFinite() && n==n.toInt().toDouble()) { "Entier requis" };return n.toInt() }
            fun string(key: String): String { val v=j.get(key);require(v is String) { "Chaîne requise" };return v }
            require(integer("version")==1) { "Version non prise en charge" }
            val flags=j.getJSONObject("flags");require(flags.keys().asSequence().toSet()==setOf("allowSentencePauses")) { "Flags inconnus" }
            val pauses=flags.get("allowSentencePauses");require(pauses is Boolean) { "Booléen requis" }
            val p=if(j.isNull("personaId")) null else VoicePersonaId(string("personaId"))
            return CoreVoiceRequestV1(string("utteranceText"),VoiceIdentityId(string("identityId")),p,
                string("expressionIntent"),number("intensity"),integer("priority"),number("speedRequested"),
                if(j.isNull("pauseMs")) null else integer("pauseMs"),pauses)
        }
    }
}

data class ExpressionVoiceBinding(val identityId: VoiceIdentityId, val profileId: String,
    val profileSha256: String, val conditioningSha256: String, val engineId: String, val packId: String) {
    init {
        require(profileId==UUID.fromString(profileId).toString()) { "Profil invalide" }
        require(listOf(profileSha256,conditioningSha256,packId).all { it.matches(Regex("[0-9a-f]{64}")) }) { "Empreinte invalide" }
        require(engineId==PocketIdentity.ENGINE && packId==PocketIdentity.packId) { "Moteur ou pack incompatible" }
    }
    companion object { fun from(p: VoiceProfile)=ExpressionVoiceBinding(p.identityId,p.id,p.sha256,p.conditioningSha256,p.engineId,p.packId) }
}

data class ExpressionEffect(val field: String,val status: String,val reason: String)
data class VoiceExpressionPlan(val request: CoreVoiceRequestV1, val intent: VoiceExpressionIntent,
    val binding: ExpressionVoiceBinding, val sampling: QualitySampling, val seed: Long,
    val capabilities: ExpressionCapabilities, val effects: List<ExpressionEffect>,
    val provenance: String="explicit-local-v1", val priority: Int=request.priority) {
    val status: String get()=if(effects.any { it.status=="unsupported" || it.status=="degraded" }) "limitée / non prise en charge acoustiquement" else "prise en charge : synthèse neutre"
}

/** No classifier, rewriting, speaker/style substitution, scheduler bypass or sampler heuristic. */
object ExpressionPolicy {
    val capabilities=ExpressionCapabilities(PocketIdentity.ENGINE,setOf("sentencePause"))
    fun plan(request: CoreVoiceRequestV1,binding: ExpressionVoiceBinding,sampling: QualitySampling,seed: Long,
        runningPriority: Int?=null): VoiceExpressionPlan {
        require(request.identityId==binding.identityId) { "Identité de la demande différente du profil" }
        require(runningPriority==null || runningPriority in 0..3)
        // Priorities never interrupt audio/native work automatically. Explicit Arrêt remains required.
        require(runningPriority==null) { "Opération déjà en cours ; priorité sans préemption automatique" }
        val intent=VoiceExpressionIntent.entries.firstOrNull { it.name==request.expressionIntent } ?: VoiceExpressionIntent.NEUTRAL
        val effects=buildList {
            if(request.expressionIntent!=intent.name) add(ExpressionEffect("intent","degraded","Intention inconnue : repli NEUTRAL"))
            if(intent!=VoiceExpressionIntent.NEUTRAL) add(ExpressionEffect("intent","unsupported","Aucune commande d'émotion acoustique validée"))
            if(request.intensity!=null) add(ExpressionEffect("intensity","unsupported","Intensité non appliquée"))
            if(request.speedRequested!=null) add(ExpressionEffect("speed","unsupported","Vitesse non appliquée"))
            if(request.pauseMs!=null) add(ExpressionEffect("pause","deferred","Pause inter-phrases soumise à validation du plan local"))
            if(request.priority!=0) add(ExpressionEffect("priority","metadata","Aucune alarme temps réel ni latence garantie"))
        }
        return VoiceExpressionPlan(request,intent,binding,sampling,seed,capabilities,effects)
    }
}
