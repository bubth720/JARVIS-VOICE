package com.jarvis.voice.studio

import com.jarvis.voice.identity.*
import com.jarvis.voice.expression.*
import com.jarvis.voice.pocket.*
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

enum class StudioScreen { SPEAK, VOICES, CREATE, ADVANCED }
enum class VoiceStep { CHOOSE, CONSENT, AUDIO, REVIEW, ENCODE, TRY, BACKUP }
data class WizardDraft(val step: VoiceStep=VoiceStep.CHOOSE,val identityId: String?=null,
    val referenceId: String?=null,val name: String="",val note: String="",val startMs: Long=0,val durationMs: Long=5000,
    val sourceMs: Long?=null,val sourceFormat: String?=null,val label: ReferenceLabel=ReferenceLabel.NEUTRE,
    val keepWav: Boolean=false,val comment: String="") {
    init {
        identityId?.let { require(it==UUID.fromString(it).toString()) };referenceId?.let { require(it==UUID.fromString(it).toString()) }
        require(name.length<=80 && note.length<=500 && comment.length<=1000)
        require(startMs in 0..175000 && durationMs in 5000..15000)
        require(sourceMs==null || sourceMs in 5000..180000);require(sourceFormat==null || sourceFormat in setOf("WAV","MP3"))
    }
}
data class StudioSettings(val seed: Long=42,val steps: Int=1,val temperature: Double=.3,
    val intent: VoiceExpressionIntent=VoiceExpressionIntent.NEUTRAL,val pauses: Boolean=false,val pauseMs: Int=250) {
    init { QualitySampling(steps,temperature);require(pauseMs in setOf(0,250,500,1000,1500)) }
    fun sampling()=QualitySampling(steps,temperature)
    fun request(text: String,id: VoiceIdentityId,baseline: Boolean=false)=CoreVoiceRequestV1(text,id,
        expressionIntent=if(baseline) "NEUTRAL" else intent.name,pauseMs=if(baseline || !pauses) 0 else pauseMs,
        allowSentencePauses=!baseline && pauses)
}
data class StudioState(val screen: StudioScreen=StudioScreen.SPEAK,val wizard: WizardDraft=WizardDraft(),
    val settings: StudioSettings=StudioSettings(),val text: String="Demain, je prendrai le train pour Paris.") {
    init { require(text.length<=300) }
}
object WizardPolicy {
    fun requireSource(durationMs: Long) {
        require(durationMs>=5000) { "Cet audio dure moins de 5 secondes. Choisir un fichier d’au moins 5 secondes." }
        require(durationMs<=180000) { "Choisir un audio de 3 minutes maximum." }
    }
    fun requireRegion(sourceMs: Long,startMs: Long,durationMs: Long) {
        requireSource(sourceMs);require(startMs in 0..175000 && durationMs in 5000..15000 && startMs+durationMs<=sourceMs) { "Choisir une portion contiguë de 5 à 15 secondes à l’intérieur de l’audio." }
    }
    fun back(step: VoiceStep)=VoiceStep.entries[(step.ordinal-1).coerceAtLeast(0)]
    fun next(draft: WizardDraft,referencePresent: Boolean,profilePresent: Boolean): VoiceStep = when(draft.step) {
        VoiceStep.CHOOSE -> VoiceStep.CONSENT
        VoiceStep.CONSENT -> { require(draft.identityId!=null) { "Créer ou choisir l’identité consentie." };VoiceStep.AUDIO }
        VoiceStep.AUDIO -> { require(draft.sourceMs!=null) { "Ajouter un audio local." };VoiceStep.REVIEW }
        VoiceStep.REVIEW -> { require(referencePresent) { "Valider l’extrait et son consentement." };VoiceStep.ENCODE }
        VoiceStep.ENCODE -> { require(profilePresent) { "Créer un profil pour cet extrait." };VoiceStep.TRY }
        VoiceStep.TRY -> { require(profilePresent) { "Choisir un profil valide." };VoiceStep.BACKUP }
        VoiceStep.BACKUP -> VoiceStep.BACKUP
    }
}
/** Private noBackup metadata only. No passwords, URI grants or consent checkbox are persisted. */
class StudioStateStore(private val file: File) {
    fun save(s: StudioState) {
        val w=s.wizard;val p=s.settings
        val j=JSONObject().put("schema",1).put("screen",s.screen.name).put("text",s.text)
            .put("wizard",JSONObject().put("step",w.step.name).put("identity",w.identityId ?: JSONObject.NULL).put("reference",w.referenceId ?: JSONObject.NULL)
                .put("name",w.name).put("note",w.note).put("start",w.startMs).put("duration",w.durationMs).put("sourceMs",w.sourceMs ?: JSONObject.NULL)
                .put("format",w.sourceFormat ?: JSONObject.NULL).put("label",w.label.name).put("keep",w.keepWav).put("comment",w.comment))
            .put("settings",JSONObject().put("seed",p.seed).put("steps",p.steps).put("temperature",p.temperature).put("intent",p.intent.name).put("pauses",p.pauses).put("pauseMs",p.pauseMs))
        atomic(file,j.toString())
    }
    fun load(): StudioState {
        if(!file.exists()) return StudioState()
        require(file.length() in 1..16384) { "Brouillon privé invalide ; données du coffre conservées." }
        try {
            val j=JSONObject(file.readText());require(j.getInt("schema")==1 && j.keys().asSequence().toSet()==setOf("schema","screen","text","wizard","settings"))
            val w=j.getJSONObject("wizard");val p=j.getJSONObject("settings")
            fun str(key: String)=if(w.isNull(key)) null else w.getString(key)
            return StudioState(StudioScreen.valueOf(j.getString("screen")),WizardDraft(VoiceStep.valueOf(w.getString("step")),str("identity"),str("reference"),w.getString("name"),w.getString("note"),w.getLong("start"),w.getLong("duration"),if(w.isNull("sourceMs")) null else w.getLong("sourceMs"),str("format"),ReferenceLabel.valueOf(w.getString("label")),w.getBoolean("keep"),w.getString("comment")),StudioSettings(p.getLong("seed"),p.getInt("steps"),p.getDouble("temperature"),VoiceExpressionIntent.valueOf(p.getString("intent")),p.getBoolean("pauses"),p.getInt("pauseMs")),j.getString("text"))
        } catch(_: Exception) { throw IllegalStateException("Brouillon privé incompatible ; aucun coffre ou profil effacé.") }
    }
    companion object {
        fun atomic(file: File,text: String) {
            val bytes=text.toByteArray();require(bytes.size<=16384);file.parentFile?.mkdirs();val tmp=File(file.parentFile,file.name+".pending")
            try { FileOutputStream(tmp).use { it.write(bytes);it.fd.sync() };check(tmp.renameTo(file)) } finally { tmp.delete() }
        }
    }
}
class StudioProfileChoice(private val file: File) {
    fun confirm(p: com.jarvis.voice.identity.VoiceProfile) {
        require(p.status==ProfileStatus.VALID && p.engineId==PocketIdentity.ENGINE && p.packId==PocketIdentity.packId)
        StudioStateStore.atomic(file,JSONObject().put("identity",p.identityId.value).put("profile",p.id).put("sha",p.sha256).put("pack",p.packId).toString())
    }
    fun isConfirmed(p: com.jarvis.voice.identity.VoiceProfile): Boolean=runCatching {
        require(file.length() in 1..1024);val j=JSONObject(file.readText())
        p.status==ProfileStatus.VALID && j.getString("identity")==p.identityId.value && j.getString("profile")==p.id && j.getString("sha")==p.sha256 && j.getString("pack")==p.packId
    }.getOrDefault(false)
    fun requireConfirmed(p: com.jarvis.voice.identity.VoiceProfile) { require(isConfirmed(p)) { "Choisir ce profil dans Mes voix puis confirmer son activation locale." } }
}
