package com.jarvis.voice.pocket

import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest

/** Two private complete utterances. A/B identity includes exact text, seed, pack and all voice tensors. */
class QualityABStore(private val root: File) {
    private fun directory(slot: String): File { require(slot in setOf("A","B"));return File(root,slot) }
    private fun cache(slot: String)=PrivatePcmStore(File(directory(slot),"pcm"))
    fun exists(slot: String)=cache(slot).exists() && File(directory(slot),"comparison.json").isFile
    fun metadata(slot: String): JSONObject {
        val file=File(directory(slot),"comparison.json");require(file.length() in 1..8192);return JSONObject(file.readText())
    }
    private fun pcmHash(pcm: FloatArray): String {
        val digest=MessageDigest.getInstance("SHA-256");val buffer=ByteBuffer.allocate(65536)
        for(sample in pcm) { if(buffer.remaining()<4) { digest.update(buffer.array(),0,buffer.position());buffer.clear() };buffer.putInt(sample.toRawBits()) }
        digest.update(buffer.array(),0,buffer.position());return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    fun context(text: String,seed: Long,profile: PersonalVoiceProfile): String {
        val digest=MessageDigest.getInstance("SHA-256")
        digest.update((profile.packId+"\u0000"+seed+"\u0000"+text+"\u0000").toByteArray())
        for((name,tensor) in profile.tensors.toSortedMap()) {
            digest.update((name+"\u0000"+tensor.dtype+"\u0000"+tensor.shape.joinToString(",")+"\u0000").toByteArray());digest.update(tensor.bytes)
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    fun requireSameAsA(context: String,cancel: CancellationToken) {
        load("A",cancel)
        require(metadata("A").getString("context")==context) { "Texte, profil ou graine différents de A. Générer A à nouveau avant B." }
    }
    fun save(slot: String,context: String,text: String,seed: Long,settings: QualitySampling,pcm: FloatArray,cancel: CancellationToken) {
        cache(slot).save(pcm,cancel)
        val pending=File(directory(slot),"comparison.pending")
        try {
            pending.writeText(JSONObject().put("context",context).put("text",text).put("seed",seed).put("settings",settings.label()).put("pcmHash",pcmHash(pcm)).toString())
            cancel.check();check(pending.renameTo(File(directory(slot),"comparison.json")))
        } finally { pending.delete() }
    }
    fun load(slot: String,cancel: CancellationToken): FloatArray {
        val pcm=cache(slot).load(cancel)
        require(pcmHash(pcm)==metadata(slot).getString("pcmHash")) { "Métadonnées A/B incompatibles avec le PCM ; génération interrompue" }
        return pcm
    }
    fun loadComparable(slot: String,cancel: CancellationToken): FloatArray {
        val pcm=load(slot,cancel)
        if(slot=="B") requireSameAsA(metadata("B").getString("context"),cancel)
        return pcm
    }
    fun delete() { for(slot in listOf("A","B")) check(directory(slot).deleteRecursively()) }
}
