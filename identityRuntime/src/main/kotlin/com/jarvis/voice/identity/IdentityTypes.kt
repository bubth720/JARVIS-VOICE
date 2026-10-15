package com.jarvis.voice.identity

import com.jarvis.voice.pocket.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

@JvmInline value class VoiceIdentityId(val value: String) { init { requireUuid(value) } }
/** External CORE-owned identifier only; this module neither creates nor stores a persona. */
@JvmInline value class VoicePersonaId(val value: String) { init { requireUuid(value) } }
enum class ReferenceLabel { NEUTRE, CHALEUREUSE, SERIEUSE, EXPRESSIVE, INCONNUE }
enum class ProfileStatus { VALID, DISABLED, INCOMPATIBLE }
data class VoiceReference(val id: String, val identityId: VoiceIdentityId, val label: ReferenceLabel,
    val sourceFormat: String, val sourceDurationMs: Long, val startMs: Long, val durationMs: Long, val retained: Boolean,
    val keepAfterEncoding: Boolean, val analysis: String, val comment: String)
data class VoiceProfile(val id: String, val identityId: VoiceIdentityId, val referenceId: String,
    val engineId: String, val packId: String, val sha256: String, val conditioningSha256: String,
    val status: ProfileStatus, val provenance: String, val evaluation: String?)
data class VoiceIdentity(val id: VoiceIdentityId, val name: String, val consentNote: String, val createdMs: Long,
    val preferredNeutralReferenceId: String?, val activeProfiles: Map<String,String>,
    val references: List<VoiceReference>, val profiles: List<VoiceProfile>)
data class VoiceExpressionRequest(val intent: String, val intensity: Double?=null, val speed: Double?=null,
    val pauseMs: Int?=null, val priority: Int=0)
data class ExpressionCapabilities(val engineId: String, val supported: Set<String>) {
    fun unsupported(request: VoiceExpressionRequest): Set<String> = buildSet {
        if(request.intent.isNotBlank() && "intent" !in supported) add("intent")
        if(request.intensity!=null && "intensity" !in supported) add("intensity")
        if(request.speed!=null && "speed" !in supported) add("speed")
        if(request.pauseMs!=null && "pause" !in supported) add("pause")
        if(request.priority!=0 && "priority" !in supported) add("priority")
    }
}
interface VoiceBackendIdentityBinding {
    val engineId: String
    val packId: String
    fun resolve(identity: VoiceIdentityId, profileId: String?=null, cancel: CancellationToken): PersonalVoiceProfile
}
object PocketIdentity {
    const val ENGINE="pocket-tts-french-6l-fp32-v1"
    val packId get()=ExperimentalModelPack.id
    val bindingKey get()="$ENGINE:$packId"
    val capabilities=ExpressionCapabilities(ENGINE,emptySet())
}
internal fun requireUuid(id: String) { require(id==UUID.fromString(id).toString()) { "Identifiant local invalide" } }
internal fun JSONObject.rows(key: String): List<JSONObject> { val a=getJSONArray(key);return (0 until a.length()).map(a::getJSONObject) }
internal fun JSONObject.strings(): Set<String> = keys().asSequence().toSet()
internal fun nullableString(j: JSONObject,key: String)=if(j.isNull(key)) null else j.getString(key)
internal fun putNullable(j: JSONObject,key: String,value: String?) { j.put(key,value ?: JSONObject.NULL) }
internal fun array(rows: List<JSONObject>)=JSONArray().also { a -> rows.forEach(a::put) }
