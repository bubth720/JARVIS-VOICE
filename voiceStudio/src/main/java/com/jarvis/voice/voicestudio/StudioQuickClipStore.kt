package com.jarvis.voice.voicestudio

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.jarvis.voice.identity.VoiceProfile
import com.jarvis.voice.studio.QuickReactionPhrase
import com.jarvis.voice.studio.StudioSettings
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * LOCAL ONLY: three short, consented clips generated with the exact active Pocket profile.
 * AES-256-GCM AndroidKeyStore + binding in AAD; not an audio bridge or a voice export.
 * A missing/invalid keystore key fails closed. This cache is NEVER backed up.
 */
internal class StudioQuickClipStore(context: Context) {
    private val folder=File(context.noBackupFilesDir,"expressive-v3-reactions")
    private val keyAlias="jarvis.voice.expressive.reactions.v3"
    private fun file(kind: QuickReactionPhrase)=File(folder,"clip-${kind.name.lowercase()}.enc")
    private fun binding(kind: QuickReactionPhrase,p: VoiceProfile,s: StudioSettings): ByteArray =
        listOf("v3",kind.name,kind.spoken,p.identityId.value,p.id,p.engineId,p.packId,
            p.sha256,p.conditioningSha256,s.seed.toString(),s.steps.toString(),
            s.temperature.toString()).joinToString("|").toByteArray(Charsets.UTF_8)
    private fun key(): SecretKey {
        val ks=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(keyAlias,null) as? SecretKey)?.let { return it }
        val gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
        gen.init(KeyGenParameterSpec.Builder(keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        return gen.generateKey()
    }
    @Synchronized fun save(kind: QuickReactionPhrase,p: VoiceProfile,s: StudioSettings,pcm: FloatArray) {
        require(pcm.size in 2400..72000 && pcm.all { it.isFinite() && it in -1f..1f }) {
            "La réaction dépasse trois secondes ou le PCM est invalide."
        }
        require(folder.isDirectory || folder.mkdirs()) { "Stockage local indisponible." }
        val raw=ByteBuffer.allocate(4+pcm.size*4).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(pcm.size).also { bb -> pcm.forEach(bb::putFloat) }.array()
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,key())
        cipher.updateAAD(binding(kind,p,s))
        try {
            val iv=cipher.iv
            require(iv.size==12)
            val encoded=cipher.doFinal(raw)
            val tmp=File.createTempFile("reaction-",".tmp",folder)
            try {
                FileOutputStream(tmp).use { output ->
                    output.write(byteArrayOf(3,iv.size.toByte()))
                    output.write(iv);output.write(encoded);output.fd.sync()
                }
                Files.move(tmp.toPath(),file(kind).toPath(),
                    StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
            } finally { tmp.delete() }
        } finally { raw.fill(0) }
    }
    @Synchronized fun load(kind: QuickReactionPhrase,p: VoiceProfile,s: StudioSettings): FloatArray? {
        val stored=file(kind)
        if(!stored.isFile || stored.length() !in 34..290000) return null
        return try {
            val all=stored.readBytes()
            if(all.size<32 || all[0].toInt()!=3 || all[1].toInt()!=12) return null
            val cipher=Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,all.copyOfRange(2,14)))
            cipher.updateAAD(binding(kind,p,s))
            val raw=cipher.doFinal(all,14,all.size-14)
            try {
                val bb=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
                val count=bb.int
                if(count !in 2400..72000 || bb.remaining()!=count*4) return null
                val pcm=FloatArray(count) { bb.float }
                if(pcm.any { !it.isFinite() || it !in -1f..1f }) {
                    pcm.fill(0f);null
                } else pcm
            } finally { raw.fill(0) }
        } catch(_: Exception) { null } // No private profile ID, file path or ciphertext in errors.
    }
    @Synchronized fun clear() {
        QuickReactionPhrase.entries.forEach { file(it).delete() }
        folder.listFiles()?.filter { it.name.startsWith("reaction-") }?.forEach { it.delete() }
    }
}
