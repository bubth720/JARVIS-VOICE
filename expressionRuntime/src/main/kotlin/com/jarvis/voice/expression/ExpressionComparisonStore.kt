package com.jarvis.voice.expression
import com.jarvis.voice.identity.*
import com.jarvis.voice.identity.VoiceProfile
import com.jarvis.voice.pocket.*
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/** Opposite of H's profile comparison: I requires the SAME immutable profile for A and B. */
class ExpressionComparisonStore(private val root: File,private val comparison: Boolean=true) {
    private val cache=QualityABStore(root)
    private fun hash(text: String)=MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
    fun context(id: VoiceIdentityId,text: String,seed: Long,settings: QualitySampling)=hash("005i-v1\u0000${id.value}\u0000$text\u0000$seed\u0000${settings.label()}\u0000PCM16")
    private fun record(slot: String)=File(root,"expression-$slot.json").also { require(slot in setOf("A","B")) }
    fun metadata(slot: String): JSONObject { val f=record(slot);require(f.length() in 1..8192) { "Métadonnées de réécoute absentes ou invalides" };return JSONObject(f.readText()) }
    private fun write(slot: String,value: JSONObject,cancel: CancellationToken) {
        root.mkdirs();val bytes=value.toString().toByteArray();require(bytes.size<=8192)
        val pending=File(root,"expression-$slot.pending")
        try { FileOutputStream(pending).use { it.write(bytes);it.fd.sync() };cancel.check();check(pending.renameTo(record(slot))) } finally { pending.delete() }
    }
    private fun checked(slot: String): JSONObject {
        val m=metadata(slot);val c=cache.metadata(slot)
        require(listOf("pcmHash","context","text","seed","settings").all { m.get(it)==c.get(it) }) { "Cache interrompu ou incohérent ; régénérer" }
        return m
    }
    fun requireB(id: VoiceIdentityId,profile: VoiceProfile,context: String,cancel: CancellationToken) {
        cache.requireSameAsA(context,cancel);val a=checked("A")
        require(a.getString("intent")=="NEUTRAL" && a.getInt("pauseMs")==0) { "A doit être la référence NEUTRAL sans pause" }
        require(a.getString("identityId")==id.value && profile.identityId==id && profile.status==ProfileStatus.VALID)
        require(a.getString("profileId")==profile.id && a.getString("profileSha256")==profile.sha256 &&
            a.getString("conditioningSha256")==profile.conditioningSha256 && a.getString("packId")==profile.packId) { "A/B exige exactement le même profil, conditionnement et pack ; régénérer A" }
    }
    fun save(slot: String,id: VoiceIdentityId,profile: VoiceProfile,text: String,seed: Long,settings: QualitySampling,pcm: FloatArray,cancel: CancellationToken,
        intent: String="NEUTRAL",pauseMs: Int=0) {
        require(profile.identityId==id && profile.status==ProfileStatus.VALID && profile.packId==PocketIdentity.packId && profile.engineId==PocketIdentity.ENGINE)
        require(intent in VoiceExpressionIntent.entries.map { it.name } && pauseMs in 0..1500)
        if(comparison && slot=="A") require(intent=="NEUTRAL" && pauseMs==0)
        val context=context(id,text,seed,settings);if(slot=="B") requireB(id,profile,context,cancel)
        cache.save(slot,context,text,seed,settings,pcm,cancel)
        write(slot,JSONObject(cache.metadata(slot).toString()).put("identityId",id.value).put("profileId",profile.id)
            .put("profileSha256",profile.sha256).put("conditioningSha256",profile.conditioningSha256).put("packId",profile.packId)
            .put("intent",intent).put("pauseMs",pauseMs).put("pcmStats",PcmStatistics.measure(pcm).summary()),cancel)
    }
    fun load(slot: String,vault: VoiceIdentityVault,cancel: CancellationToken): FloatArray {
        val m=checked(slot);val id=VoiceIdentityId(m.getString("identityId"));require(vault.selected()==id) { "Sélectionner l'identité de cet audio" }
        val identity=vault.list().single { it.id==id };val p=identity.profiles.single { it.id==m.getString("profileId") }
        require(identity.activeProfiles[PocketIdentity.bindingKey]==p.id) { "Réactiver explicitement le profil de cet audio pour réécoute" }
        require(p.status==ProfileStatus.VALID && p.sha256==m.getString("profileSha256") && p.conditioningSha256==m.getString("conditioningSha256") && p.packId==m.getString("packId"))
        vault.binding().resolve(id,p.id,cancel)
        if(slot=="B") requireB(id,p,m.getString("context"),cancel)
        return cache.loadComparable(slot,cancel)
    }
    fun performance(slot: String,summary: String,cancel: CancellationToken) {
        require(summary.length<=2048);val m=checked(slot);m.put("generationMetrics",summary);write(slot,m,cancel)
    }
    fun note(slot: String,vault: VoiceIdentityVault,scores: List<Int>,heard: Boolean,evidence: JSONObject,cancel: CancellationToken) {
        require(heard && scores.size==5 && scores.all { it in 0..5 });load(slot,vault,cancel);val m=checked(slot)
        require(m.getString("pcmHash")==evidence.getString("pcmHash") && m.getString("context")==evidence.getString("context")) { "Audio remplacé : réécouter" }
        m.put("evaluation",JSONObject().put("heard",true).put("atMs",System.currentTimeMillis()).put("criteria",JSONArray(listOf("naturalness","expressivity","timbreConsistency","intelligibility","metallicArtifacts"))).put("scores",JSONArray(scores)))
        write(slot,m,cancel)
    }
    private fun deleteSlot(slot: String) { check(File(root,slot).deleteRecursively());for(f in listOf(record(slot),File(root,"expression-$slot.pending"))) check(!f.exists() || f.delete()) }
    fun deleteProfile(id: VoiceIdentityId,pid: String,cancel: CancellationToken) {
        for(slot in listOf("A","B")) {
            if(!File(root,slot).exists() && !record(slot).exists()) continue
            val m=checked(slot);cache.load(slot,cancel);require(m.getString("identityId")==id.value)
            if(m.getString("profileId")==pid) deleteSlot(slot)
        }
    }
    fun delete() { for(slot in listOf("A","B")) deleteSlot(slot) }
}
