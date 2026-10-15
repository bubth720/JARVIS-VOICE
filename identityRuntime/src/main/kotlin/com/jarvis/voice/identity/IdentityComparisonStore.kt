package com.jarvis.voice.identity

import com.jarvis.voice.pocket.*
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** H compares different profiles of ONE identity, at identical text/seed/parameters and PCM16 output. */
class IdentityComparisonStore(private val root: File) {
    private val cache=QualityABStore(root)
    private fun hash(text: String)=MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
    fun context(id: VoiceIdentityId,text: String,seed: Long,settings: QualitySampling)=hash("${id.value}\u0000$text\u0000$seed\u0000${settings.label()}\u0000PCM16")
    private fun record(slot: String)=File(root,"profile-$slot.json").also { require(slot in setOf("A","B")) }
    fun metadata(slot: String): JSONObject { val f=record(slot);require(f.length() in 1..8192);return JSONObject(f.readText()) }
    fun requireB(id: VoiceIdentityId,profile: VoiceProfile,context: String,cancel: CancellationToken) {
        cache.requireSameAsA(context,cancel);val a=metadata("A")
        require(a.getString("pcmHash")==cache.metadata("A").getString("pcmHash") && a.getString("context")==cache.metadata("A").getString("context")) { "Écriture A interrompue ; régénérer A" }
        require(a.getString("identityId")==id.value && profile.identityId==id)
        require(a.getString("profileId")!=profile.id && a.getString("conditioningSha256")!=profile.conditioningSha256) { "Choisir deux profils et conditionnements distincts de la même personne" }
    }
    fun save(slot: String,id: VoiceIdentityId,profile: VoiceProfile,text: String,seed: Long,settings: QualitySampling,pcm: FloatArray,cancel: CancellationToken) {
        require(profile.identityId==id && profile.status==ProfileStatus.VALID)
        val context=context(id,text,seed,settings);if(slot=="B") requireB(id,profile,context,cancel)
        cache.save(slot,context,text,seed,settings,pcm,cancel)
        VoiceIdentityVault.atomic(record(slot),JSONObject().put("identityId",id.value).put("profileId",profile.id).put("profileSha256",profile.sha256)
            .put("conditioningSha256",profile.conditioningSha256).put("context",context).put("pcmHash",cache.metadata(slot).getString("pcmHash")).put("settings",settings.label()).put("text",text).put("seed",seed).toString().toByteArray(),cancel)
    }
    fun load(slot: String,vault: VoiceIdentityVault,cancel: CancellationToken): FloatArray {
        val m=metadata(slot);val id=VoiceIdentityId(m.getString("identityId"))
        require(vault.selected()==id) { "Sélectionner explicitement l'identité de cet audio avant réécoute" }
        val p=vault.list().single { it.id==id }.profiles.single { it.id==m.getString("profileId") }
        require(p.status==ProfileStatus.VALID && p.sha256==m.getString("profileSha256"))
        vault.binding().resolve(id,p.id,cancel)
        require(cache.metadata(slot).getString("pcmHash")==m.getString("pcmHash") && cache.metadata(slot).getString("context")==m.getString("context"))
        if(slot=="B") requireB(id,p,m.getString("context"),cancel)
        return cache.loadComparable(slot,cancel)
    }
    private fun deleteSlot(slot: String) {
        check(File(root,slot).deleteRecursively())
        for(f in listOf(record(slot),File(root,"profile-$slot.json.pending"))) check(!f.exists() || f.delete())
    }
    fun deleteProfile(id: VoiceIdentityId,pid: String,cancel: CancellationToken) {
        for(slot in listOf("A","B")) {
            val directory=File(root,slot);val file=record(slot)
            if(!directory.exists() && !file.exists()) continue
            require(file.isFile) { "Cache interrompu : effacer explicitement les audios de cette identité avant suppression du profil" }
            val m=metadata(slot);cache.load(slot,cancel)
            require(m.getString("pcmHash")==cache.metadata(slot).getString("pcmHash") && m.getString("context")==cache.metadata(slot).getString("context")) { "Cache incohérent : effacer explicitement les audios de cette identité" }
            require(m.getString("identityId")==id.value)
            if(m.getString("profileId")==pid) deleteSlot(slot)
        }
    }
    fun delete() { for(slot in listOf("A","B")) deleteSlot(slot) }
}
