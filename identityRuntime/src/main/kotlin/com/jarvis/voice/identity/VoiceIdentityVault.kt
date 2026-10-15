package com.jarvis.voice.identity

import com.jarvis.voice.pocket.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.nio.channels.FileChannel
import java.nio.file.*
import java.security.MessageDigest
import java.util.UUID

/** Immutable objects + one fsynced, atomically replaced index. Never uses PersonalVoiceRepository. */
class VoiceIdentityVault(val root: File, private val beforeCommit: () -> Unit = {}) {
    companion object {
        private val mutex=Any()
        const val SCHEMA=1
        const val MAX_BYTES=536870912L
        const val MAX_JSON=1048576
        fun conditioningSha(profile: PersonalVoiceProfile)=QualityABStore(File(".")).context("",0L,profile)
        internal fun forceDirectory(directory: File) { FileChannel.open(directory.toPath(),StandardOpenOption.READ).use { it.force(true) } }
        fun sha(file: File,cancel: CancellationToken=CancellationToken()): String {
            val d=MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input -> val b=ByteArray(65536);while(true) { cancel.check();val n=input.read(b);if(n<0) break;d.update(b,0,n) } }
            return d.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        }
        internal fun copy(input: InputStream,file: File,limit: Long,cancel: CancellationToken) {
            file.outputStream().use { out -> val b=ByteArray(65536);var total=0L
                while(true) { cancel.check();val n=input.read(b);if(n<0) break;total+=n;require(total<=limit) { "Limite de taille dépassée" };out.write(b,0,n) };out.fd.sync()
            }
        }
        internal fun atomic(file: File,bytes: ByteArray,cancel: CancellationToken,check: () -> Unit={}) {
            val pending=File(file.parentFile,"${file.name}.pending")
            try {
                FileOutputStream(pending).use { it.write(bytes);it.fd.sync() };cancel.check();check()
                Files.move(pending.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
                forceDirectory(requireNotNull(file.parentFile))
            } finally { pending.delete() }
        }
    }
    private val objects=File(root,"objects")
    private val index=File(root,"vault.json")
    init {
        check(root.mkdirs() || root.isDirectory);check(objects.mkdirs() || objects.isDirectory)
        locked {
            forceDirectory(root);forceDirectory(objects);root.parentFile?.let(::forceDirectory)
            if(!index.exists()) atomic(index,JSONObject().put("schema",SCHEMA).put("activeIdentity",JSONObject.NULL).put("identities",JSONArray()).toString().toByteArray(),CancellationToken())
            val j=read();cleanup(j);File(root,"vault.json.pending").delete();forceDirectory(objects);forceDirectory(root)
        }
    }
    private fun <T> locked(block: () -> T): T = synchronized(mutex) {
        RandomAccessFile(File(root,"vault.lock"),"rw").use { f -> f.channel.lock().use { block() } }
    }
    private fun objectFile(id: String,extension: String): File {
        requireUuid(id);require(extension in setOf("wav","bin"))
        return File(objects,"$id.$extension").also { require(!Files.isSymbolicLink(it.toPath()) && it.canonicalFile.parentFile==objects.canonicalFile) }
    }
    private fun read(): JSONObject {
        require(index.length() in 1..MAX_JSON) { "Index privé absent ou trop grand" }
        return JSONObject(index.readText()).also { validate(it,false) }
    }
    internal fun validate(j: JSONObject,verifyObjects: Boolean,files: (String,String)->File=::objectFile) {
        require(j.getInt("schema")==SCHEMA && j.strings()==setOf("schema","activeIdentity","identities")) { "Schéma de coffre incompatible" }
        val identities=j.rows("identities");require(identities.size<=10)
        val ids=mutableSetOf<String>();val objectIds=mutableSetOf<String>();var bytes=0L
        for(i in identities) {
            require(i.strings()==setOf("schema","id","name","createdMs","consentConfirmed","consentNote","preferredNeutralReferenceId","activeProfiles","references","profiles"))
            val id=i.getString("id");requireUuid(id);require(ids.add(id)) { "Identité dupliquée" }
            require(i.getInt("schema")==SCHEMA && i.getBoolean("consentConfirmed"));require(i.getString("name").length in 1..80)
            require(i.getString("consentNote").length in 1..500 && i.getLong("createdMs")>0)
            val refs=i.rows("references");val profiles=i.rows("profiles");require(refs.size<=20 && profiles.size<=20)
            val refIds=mutableSetOf<String>();val profileIds=mutableSetOf<String>()
            for(r in refs) {
                require(r.strings()==setOf("id","identityId","sourceFormat","sourceSha256","sourceDurationMs","startMs","durationMs","label","comment","consentConfirmed","sameSpeakerConfirmed","consentNote","createdMs","keepAfterEncoding","analysis","bytes","sha256"))
                val rid=r.getString("id");requireUuid(rid);require(refIds.add(rid) && objectIds.add(rid))
                require(r.getString("identityId")==id && r.getBoolean("consentConfirmed") && r.getBoolean("sameSpeakerConfirmed"))
                require(r.getString("consentNote").length in 1..500 && r.getString("comment").length<=1000)
                ReferenceLabel.valueOf(r.getString("label"));require(r.getString("sourceFormat") in setOf("MP3","WAV"))
                require(r.getString("sourceSha256").matches(Regex("[0-9a-f]{64}")))
                val start=r.getLong("startMs");val duration=r.getLong("durationMs")
                require(duration in 5000..15000 && start>=0 && start+duration<=180000)
                require(r.getLong("sourceDurationMs") in (start+duration)..180000)
                require(r.getString("analysis").length in 1..4096 && r.getLong("createdMs")>0)
                r.getBoolean("keepAfterEncoding")
                val n=r.getLong("bytes");val hash=nullableString(r,"sha256")
                require((n==0L && hash==null) || (n in 240044..720044 && hash?.matches(Regex("[0-9a-f]{64}"))==true))
                bytes+=n
                if(n>0 && verifyObjects) { val f=files(rid,"wav");require(f.length()==n && sha(f)==hash) { "Référence corrompue" };val pcm=PrivateWav.read(f);require(pcm.size/24L==duration) }
            }
            for(p in profiles) {
                require(p.strings()==setOf("id","identityId","referenceId","engineId","packId","sha256","conditioningSha256","bytes","status","createdMs","protocol","provenance","evaluation"))
                val pid=p.getString("id");requireUuid(pid);require(profileIds.add(pid) && objectIds.add(pid))
                require(p.getString("identityId")==id && p.getString("referenceId") in refIds)
                require(p.getString("engineId")==PocketIdentity.ENGINE && p.getString("packId")==PocketIdentity.packId) { "Profil incompatible : réencoder localement depuis une référence autorisée" }
                require(p.getString("sha256").matches(Regex("[0-9a-f]{64}")))
                require(p.getString("conditioningSha256").matches(Regex("[0-9a-f]{64}")))
                ProfileStatus.valueOf(p.getString("status"));require(p.getLong("createdMs")>0)
                require(p.getString("protocol")=="official-FP32-Mimi-BOS-18states-JVFP1-v1")
                require(p.getString("provenance").length in 1..500)
                require(p.isNull("evaluation") || p.getJSONObject("evaluation").let { e ->
                    e.getBoolean("heardOnDevice") && e.getLong("timestampMs")>0 && listOf("naturalness","similarity","timbreConsistency","intelligibility","metallicArtifacts").all { e.getInt(it) in 0..5 }
                })
                val n=p.getLong("bytes");require(n in 100..10000000);bytes+=n
                if(verifyObjects) { val f=files(pid,"bin");require(f.length()==n && sha(f)==p.getString("sha256")) { "Profil corrompu" };require(conditioningSha(PersonalVoiceCodec.read(f))==p.getString("conditioningSha256")) { "Conditionnement incompatible" } }
            }
            val preferred=nullableString(i,"preferredNeutralReferenceId")
            require(preferred==null || refs.any { it.getString("id")==preferred && it.getString("label")==ReferenceLabel.NEUTRE.name })
            val active=i.getJSONObject("activeProfiles")
            require(active.strings().all { it==PocketIdentity.bindingKey })
            for(key in active.strings()) require(profiles.any { it.getString("id")==active.getString(key) && it.getString("status")=="VALID" })
        }
        require(bytes<=MAX_BYTES);val active=nullableString(j,"activeIdentity");require(active==null || active in ids)
    }
    private fun cleanup(j: JSONObject) {
        val keep=mutableSetOf<String>()
        for(i in j.rows("identities")) {
            i.rows("references").filter { it.getLong("bytes")>0 }.forEach { keep.add(it.getString("id")+".wav") }
            i.rows("profiles").forEach { keep.add(it.getString("id")+".bin") }
        }
        objects.listFiles()?.filter { it.name !in keep }?.forEach { check(it.delete()) { "Suppression privée incomplète ; réessayer" } }
    }
    private fun <T> change(cancel: CancellationToken=CancellationToken(),action: (JSONObject)->T): T = locked {
        val j=read()
        try {
            cancel.check();val result=action(j);validate(j,false)
            val bytes=j.toString().toByteArray();require(bytes.size<=MAX_JSON)
            forceDirectory(objects);atomic(index,bytes,cancel,beforeCommit);cleanup(j);forceDirectory(objects);result
        } catch(t: Throwable) {
            // If directory fsync failed after rename, keep BOTH generations until durability can be established.
            runCatching { forceDirectory(root);forceDirectory(objects);cleanup(read());forceDirectory(objects) }
            throw t
        }
    }
    private fun identity(j: JSONObject,id: VoiceIdentityId)=j.rows("identities").singleOrNull { it.getString("id")==id.value } ?: error("Identité absente ; sélectionner explicitement")
    private fun reference(i: JSONObject,id: String)=i.rows("references").singleOrNull { it.getString("id")==id } ?: error("Référence absente de cette identité")
    private fun profile(i: JSONObject,id: String)=i.rows("profiles").singleOrNull { it.getString("id")==id } ?: error("Profil absent de cette identité")
    private fun view(i: JSONObject): VoiceIdentity {
        val id=VoiceIdentityId(i.getString("id"));val active=i.getJSONObject("activeProfiles")
        return VoiceIdentity(id,i.getString("name"),i.getString("consentNote"),i.getLong("createdMs"),nullableString(i,"preferredNeutralReferenceId"),active.strings().associateWith(active::getString),
            i.rows("references").map { r -> VoiceReference(r.getString("id"),id,ReferenceLabel.valueOf(r.getString("label")),r.getString("sourceFormat"),r.getLong("sourceDurationMs"),r.getLong("startMs"),r.getLong("durationMs"),r.getLong("bytes")>0,r.getBoolean("keepAfterEncoding"),r.getString("analysis"),r.getString("comment")) },
            i.rows("profiles").map { p -> VoiceProfile(p.getString("id"),id,p.getString("referenceId"),p.getString("engineId"),p.getString("packId"),p.getString("sha256"),p.getString("conditioningSha256"),ProfileStatus.valueOf(p.getString("status")),p.getString("provenance"),if(p.isNull("evaluation")) null else p.getJSONObject("evaluation").toString()) })
    }
    fun list(): List<VoiceIdentity> = locked { read().rows("identities").map(::view) }
    fun selected(): VoiceIdentityId? = locked { nullableString(read(),"activeIdentity")?.let(::VoiceIdentityId) }
    fun create(name: String,note: String,consent: Boolean,cancel: CancellationToken=CancellationToken()): VoiceIdentityId = change(cancel) { j ->
        require(consent) { "Consentement explicite requis" };val id=VoiceIdentityId(UUID.randomUUID().toString())
        j.getJSONArray("identities").put(JSONObject().put("schema",SCHEMA).put("id",id.value).put("name",name.trim()).put("createdMs",System.currentTimeMillis())
            .put("consentConfirmed",true).put("consentNote",note.trim()).put("preferredNeutralReferenceId",JSONObject.NULL)
            .put("activeProfiles",JSONObject()).put("references",JSONArray()).put("profiles",JSONArray()))
        j.put("activeIdentity",id.value);id
    }
    fun select(id: VoiceIdentityId,cancel: CancellationToken=CancellationToken())=change(cancel) { j -> identity(j,id);j.put("activeIdentity",id.value);Unit }
    fun rename(id: VoiceIdentityId,name: String,cancel: CancellationToken=CancellationToken())=change(cancel) { j -> identity(j,id).put("name",name.trim());Unit }
    fun addReference(id: VoiceIdentityId,pcm: FloatArray,sourceFormat: String,sourceSha256: String,startMs: Long,label: ReferenceLabel,
        comment: String,note: String,consent: Boolean,sameSpeaker: Boolean,keep: Boolean,cancel: CancellationToken,sourceDurationMs: Long=startMs+pcm.size/24L): String = change(cancel) { j ->
        require(consent && sameSpeaker) { "Consentement et même personne à confirmer pour cette référence" }
        val i=identity(j,id);val rid=UUID.randomUUID().toString();val f=objectFile(rid,"wav")
        ReferenceAudio.writeWav(f,pcm,cancel::isCancelled);FileOutputStream(f,true).use { it.fd.sync() }
        val saved=PrivateWav.read(f,cancelled=cancel::isCancelled)
        i.getJSONArray("references").put(JSONObject().put("id",rid).put("identityId",id.value).put("sourceFormat",sourceFormat).put("sourceSha256",sourceSha256)
            .put("sourceDurationMs",sourceDurationMs).put("startMs",startMs).put("durationMs",saved.size/24L).put("label",label.name).put("comment",comment).put("consentConfirmed",true)
            .put("sameSpeakerConfirmed",true).put("consentNote",note.trim()).put("createdMs",System.currentTimeMillis()).put("keepAfterEncoding",keep)
            .put("analysis",ReferenceSelection.assess(saved).summary()).put("bytes",f.length()).put("sha256",sha(f,cancel)))
        rid
    }
    fun readReference(id: VoiceIdentityId,rid: String,cancel: CancellationToken): FloatArray=locked {
        val r=reference(identity(read(),id),rid);require(r.getLong("bytes")>0) { "Extrait supprimé après encodage ; réimporter localement la source" }
        val f=objectFile(rid,"wav");require(f.length()==r.getLong("bytes") && sha(f,cancel)==r.getString("sha256")) { "Référence corrompue" }
        PrivateWav.read(f,cancelled=cancel::isCancelled)
    }
    fun referenceDigest(id: VoiceIdentityId,rid: String,cancel: CancellationToken): String=locked {
        val r=reference(identity(read(),id),rid);require(r.getLong("bytes")>0)
        sha(objectFile(rid,"wav"),cancel).also { require(it==r.getString("sha256")) }
    }
    fun saveProfile(id: VoiceIdentityId,rid: String,value: PersonalVoiceProfile,cancel: CancellationToken): String = change(cancel) { j ->
        val i=identity(j,id);val r=reference(i,rid);require(r.getLong("bytes")>0) { "Référence privée absente" }
        require(sha(objectFile(rid,"wav"),cancel)==r.getString("sha256")) { "Référence corrompue" }
        val pid=UUID.randomUUID().toString();val f=objectFile(pid,"bin");PersonalVoiceCodec.write(f,value);PersonalVoiceCodec.read(f)
        i.getJSONArray("profiles").put(JSONObject().put("id",pid).put("identityId",id.value).put("referenceId",rid).put("engineId",PocketIdentity.ENGINE)
            .put("packId",value.packId).put("sha256",sha(f,cancel)).put("conditioningSha256",conditioningSha(value)).put("bytes",f.length()).put("status","VALID").put("createdMs",System.currentTimeMillis())
            .put("protocol","official-FP32-Mimi-BOS-18states-JVFP1-v1").put("provenance","Extrait contigu ${r.getString("sourceFormat")} consenti ; ${r.getLong("durationMs")} ms ; encodage officiel individuel ; aucun sampler pendant l'encodage")
            .put("evaluation",JSONObject.NULL))
        if(!r.getBoolean("keepAfterEncoding")) { r.put("bytes",0);r.put("sha256",JSONObject.NULL) }
        pid
    }
    fun activate(id: VoiceIdentityId,pid: String,neutral: Boolean=false,cancel: CancellationToken=CancellationToken())=change(cancel) { j ->
        val i=identity(j,id);val p=profile(i,pid);require(p.getString("status")=="VALID")
        if(neutral) { val r=reference(i,p.getString("referenceId"));require(r.getString("label")=="NEUTRE") { "Annoter NEUTRE avant de choisir la référence principale" };i.put("preferredNeutralReferenceId",r.getString("id")) }
        i.getJSONObject("activeProfiles").put(PocketIdentity.bindingKey,pid);Unit
    }
    fun annotateReference(id: VoiceIdentityId,rid: String,label: ReferenceLabel,comment: String,cancel: CancellationToken=CancellationToken())=change(cancel) { j ->
        val i=identity(j,id);val r=reference(i,rid);r.put("label",label.name).put("comment",comment)
        if(nullableString(i,"preferredNeutralReferenceId")==rid && label!=ReferenceLabel.NEUTRE) i.put("preferredNeutralReferenceId",JSONObject.NULL)
        Unit
    }
    fun deactivate(id: VoiceIdentityId,pid: String,cancel: CancellationToken=CancellationToken())=change(cancel) { j ->
        val i=identity(j,id);profile(i,pid).put("status","DISABLED");val a=i.getJSONObject("activeProfiles")
        if(a.optString(PocketIdentity.bindingKey)==pid) a.remove(PocketIdentity.bindingKey);Unit
    }
    fun enable(id: VoiceIdentityId,pid: String,cancel: CancellationToken=CancellationToken())=change(cancel) { j -> val p=profile(identity(j,id),pid);require(p.getString("status") in setOf("VALID","DISABLED"));p.put("status","VALID");Unit }
    fun noteProfile(id: VoiceIdentityId,pid: String,scores: List<Int>,heard: Boolean,cancel: CancellationToken=CancellationToken(),evidence: JSONObject?=null)=change(cancel) { j ->
        require(heard && scores.size==5 && scores.all { it in 0..5 });val p=profile(identity(j,id),pid)
        val e=JSONObject().put("heardOnDevice",true).put("timestampMs",System.currentTimeMillis())
        if(evidence!=null) { require(evidence.toString().toByteArray().size<=8192 && evidence.getString("profileId")==pid && evidence.getString("profileSha256")==p.getString("sha256"));e.put("audition",JSONObject(evidence.toString())) }
        listOf("naturalness","similarity","timbreConsistency","intelligibility","metallicArtifacts").zip(scores).forEach { (k,v)->e.put(k,v) };p.put("evaluation",e);Unit
    }
    fun removeProfile(id: VoiceIdentityId,pid: String,confirmed: Boolean,cancel: CancellationToken=CancellationToken())=change(cancel) { j ->
        require(confirmed);val i=identity(j,id);profile(i,pid);i.put("profiles",array(i.rows("profiles").filter { it.getString("id")!=pid }))
        val a=i.getJSONObject("activeProfiles");if(a.optString(PocketIdentity.bindingKey)==pid) a.remove(PocketIdentity.bindingKey);Unit
    }
    fun removeReferenceAudio(id: VoiceIdentityId,rid: String,confirmed: Boolean,cancel: CancellationToken=CancellationToken())=change(cancel) { j ->
        require(confirmed);reference(identity(j,id),rid).put("bytes",0).put("sha256",JSONObject.NULL);Unit
    }
    fun removeReference(id: VoiceIdentityId,rid: String,confirmed: Boolean,cancel: CancellationToken=CancellationToken())=change(cancel) { j ->
        require(confirmed);val i=identity(j,id);reference(i,rid)
        require(i.rows("profiles").none { it.getString("referenceId")==rid }) { "Supprimer d'abord les profils de cette référence" }
        i.put("references",array(i.rows("references").filter { it.getString("id")!=rid }))
        if(nullableString(i,"preferredNeutralReferenceId")==rid) i.put("preferredNeutralReferenceId",JSONObject.NULL);Unit
    }
    fun removeIdentity(id: VoiceIdentityId,confirmed: Boolean,cancel: CancellationToken=CancellationToken())=change(cancel) { j ->
        require(confirmed);identity(j,id);j.put("identities",array(j.rows("identities").filter { it.getString("id")!=id.value }))
        if(nullableString(j,"activeIdentity")==id.value) j.put("activeIdentity",JSONObject.NULL);Unit
    }
    fun binding(engine: String=PocketIdentity.ENGINE,pack: String=PocketIdentity.packId)=object: VoiceBackendIdentityBinding {
        override val engineId=engine;override val packId=pack
        override fun resolve(identity: VoiceIdentityId,profileId: String?,cancel: CancellationToken): PersonalVoiceProfile=locked {
            require(engineId==PocketIdentity.ENGINE && packId==PocketIdentity.packId) { "Backend/pack incompatible : réencodage local requis, caches non universels" }
            val i=identity(read(),identity);val pid=profileId ?: i.getJSONObject("activeProfiles").optString(PocketIdentity.bindingKey).takeIf { it.isNotEmpty() }
            require(pid!=null) { "Choisir explicitement un profil actif pour cette identité" }
            val p=profile(i,pid);require(p.getString("status")=="VALID") { "Profil désactivé ou incompatible" }
            val f=objectFile(pid,"bin");require(f.length()==p.getLong("bytes") && sha(f,cancel)==p.getString("sha256")) { "Profil corrompu" };cancel.check();PersonalVoiceCodec.read(f).also { require(conditioningSha(it)==p.getString("conditioningSha256")) }
        }
    }
    internal fun <T> snapshot(id: VoiceIdentityId,includeReferences: Boolean,action: (JSONObject,Map<String,File>)->T): T=locked {
        val i=JSONObject(identity(read(),id).toString());val files=linkedMapOf<String,File>()
        for(r in i.rows("references")) {
            if(includeReferences && r.getLong("bytes")>0) files["references/${r.getString("id")}.wav"]=objectFile(r.getString("id"),"wav")
            else r.put("bytes",0).put("sha256",JSONObject.NULL)
        }
        for(p in i.rows("profiles")) files["profiles/${p.getString("id")}.bin"]=objectFile(p.getString("id"),"bin")
        val j=JSONObject().put("schema",SCHEMA).put("activeIdentity",id.value).put("identities",JSONArray().put(i))
        validate(j,true);action(j,files)
    }
    internal fun restore(snapshot: JSONObject,staged: Map<String,File>,cancel: CancellationToken): VoiceIdentityId=change(cancel) { j ->
        require(snapshot.rows("identities").size==1)
        validate(snapshot,true) { id,ext -> requireNotNull(staged["${if(ext=="wav") "references" else "profiles"}/$id.$ext"]) }
        val i=snapshot.rows("identities").single();val id=VoiceIdentityId(i.getString("id"))
        require(j.rows("identities").none { it.getString("id")==id.value }) { "Identité déjà présente : restauration refusée, aucun remplacement" }
        for((name,f) in staged) {
            cancel.check();val target=objectFile(File(name).nameWithoutExtension,File(name).extension)
            require(!target.exists()) { "Objet déjà présent : aucun écrasement" };f.inputStream().use { copy(it,target,10000000,cancel) }
        }
        j.getJSONArray("identities").put(i);j.put("activeIdentity",id.value);id
    }
}
