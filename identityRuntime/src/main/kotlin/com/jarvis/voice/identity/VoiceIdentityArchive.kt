package com.jarvis.voice.identity

import com.jarvis.voice.pocket.CancellationToken
import org.json.JSONObject
import java.io.*
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.UUID
import java.util.zip.*
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.*

/** JVIENC01: authenticated header + AES-256-GCM ZIP. Plaintext never leaves privateWork. */
class VoiceIdentityArchive(private val privateWork: File) {
    companion object {
        const val ITERATIONS=600000
        const val MAX_ARCHIVE=268435456L
        private val MAGIC="JVIENC01".toByteArray(Charsets.US_ASCII)
        private const val HEADER=40
        const val NOTICES="JARVIS VOICE IDENTITY schema 1. Pocket TTS by Kyutai: https://github.com/kyutai-labs/pocket-tts (code MIT 3.3.0). Official French checkpoint: https://huggingface.co/kyutai/pocket-tts revision d0ebc5deb50bc6b225f686782f124dd37e2dca0a; CC BY 4.0 https://creativecommons.org/licenses/by/4.0/ and Kyutai consent/privacy/model conditions. JARVIS adaptation: ONNX streaming FP32, individual JVFP1 conditioning. No model weights or public voice recordings included. User references require explicit lawful consent; no misleading impersonation. Synthetic speech must not be presented as a real recording. Personal voice data: owner-controlled private archive only; no automatic cloud transfer."
    }
    init { check(privateWork.mkdirs() || privateWork.isDirectory) }
    private fun workspace()=File(privateWork,UUID.randomUUID().toString()).apply { check(mkdir()) }
    private fun cipher(mode: Int,password: CharArray,header: ByteArray): Cipher {
        require(password.size in 12..128) { "Mot de passe : 12 à 128 caractères requis" }
        val b=ByteBuffer.wrap(header);val magic=ByteArray(8).also(b::get)
        require(magic.contentEquals(MAGIC) && b.int==ITERATIONS) { "Format ou coût cryptographique non pris en charge" }
        val salt=ByteArray(16).also(b::get);val nonce=ByteArray(12).also(b::get)
        val spec=PBEKeySpec(password,salt,ITERATIONS,256)
        val key=try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
        return try { Cipher.getInstance("AES/GCM/NoPadding").apply { init(mode,SecretKeySpec(key,"AES"),GCMParameterSpec(128,nonce));updateAAD(header) } } finally { key.fill(0) }
    }
    private fun transform(input: InputStream,out: File,cipher: Cipher,cancel: CancellationToken,initial: ByteArray?=null) {
        FileOutputStream(out).use { target ->
            if(initial!=null) target.write(initial)
            val buffer=ByteArray(65536);var total=0L;var written=initial?.size?.toLong() ?: 0L
            while(true) {
                cancel.check();val n=input.read(buffer);if(n<0) break;total+=n;require(total<=MAX_ARCHIVE)
                cipher.update(buffer,0,n)?.let { written+=it.size;require(written<=MAX_ARCHIVE);target.write(it) }
            }
            cancel.check();val last=cipher.doFinal();written+=last.size;require(written<=MAX_ARCHIVE);target.write(last);target.fd.sync()
        }
    }
    /** Output is ciphertext only; caller owns the SAF destination. Password is never persisted. */
    fun export(vault: VoiceIdentityVault,id: VoiceIdentityId,password: CharArray,includeReferences: Boolean,
        destination: OutputStream,cancel: CancellationToken) {
        val work=workspace()
        try {
            val zip=File(work,"identity.private")
            vault.snapshot(id,includeReferences) { manifest,files ->
                ZipOutputStream(zip.outputStream()).use { out ->
                    fun entry(name: String,data: ByteArray) { cancel.check();out.putNextEntry(ZipEntry(name));out.write(data);out.closeEntry() }
                    entry("manifest.json",manifest.toString().toByteArray());entry("NOTICES.txt",NOTICES.toByteArray())
                    for((name,file) in files) { cancel.check();out.putNextEntry(ZipEntry(name));file.inputStream().use { input ->
                        val b=ByteArray(65536);while(true) { cancel.check();val n=input.read(b);if(n<0) break;out.write(b,0,n) }
                    };out.closeEntry() }
                }
            }
            require(zip.length()<=MAX_ARCHIVE-56)
            val random=SecureRandom();val header=ByteBuffer.allocate(HEADER).put(MAGIC).putInt(ITERATIONS)
                .put(ByteArray(16).also(random::nextBytes)).put(ByteArray(12).also(random::nextBytes)).array()
            val encrypted=File(work,"identity.encrypted")
            zip.inputStream().use { transform(it,encrypted,cipher(Cipher.ENCRYPT_MODE,password,header),cancel,header) }
            encrypted.inputStream().use { input -> val b=ByteArray(65536);while(true) { cancel.check();val n=input.read(b);if(n<0) break;destination.write(b,0,n) } };destination.flush()
        } finally { check(work.deleteRecursively()) { "Nettoyage temporaire incomplet ; consulter le coffre et redémarrer pour réessayer" } }
    }
    /** Authenticate the entire ciphertext before ZIP parsing, then one all-or-nothing vault commit. */
    fun restore(vault: VoiceIdentityVault,password: CharArray,source: InputStream,cancel: CancellationToken): VoiceIdentityId {
        val work=workspace()
        try {
            val encrypted=File(work,"incoming.encrypted");VoiceIdentityVault.copy(source,encrypted,MAX_ARCHIVE,cancel)
            require(encrypted.length()>=HEADER+16)
            val clear=File(work,"authenticated.private")
            try { encrypted.inputStream().use { input ->
                val header=ByteArray(HEADER);DataInputStream(input).readFully(header)
                transform(input,clear,cipher(Cipher.DECRYPT_MODE,password,header),cancel)
            } } catch(t: javax.crypto.AEADBadTagException) { throw IllegalArgumentException("Mot de passe incorrect ou archive altérée ; coffre inchangé") }
            return restoreAuthenticatedZip(vault,clear,work,cancel)
        } finally { check(work.deleteRecursively()) { "Nettoyage temporaire incomplet ; consulter le coffre et redémarrer pour réessayer" } }
    }
    /** Internal seam for malicious authenticated-archive tests. Never accepts unencrypted public imports. */
    internal fun restoreAuthenticatedZip(vault: VoiceIdentityVault,clear: File,work: File,cancel: CancellationToken): VoiceIdentityId {
        val staged=linkedMapOf<String,File>();var total=0L
        ZipInputStream(clear.inputStream()).use { zip ->
            val first=zip.nextEntry;require(first!=null && !first.isDirectory && first.name=="manifest.json") { "Manifeste d'archive requis en premier" }
            fun bounded(out: OutputStream,max: Long): Long {
                val b=ByteArray(65536);var nbytes=0L
                while(true) { cancel.check();val n=zip.read(b);if(n<0) break;nbytes+=n;total+=n;require(nbytes<=max && total<=MAX_ARCHIVE) { "Archive trop grande ou bombe ZIP" };out.write(b,0,n) }
                zip.closeEntry();return nbytes
            }
            val bytes=ByteArrayOutputStream();bounded(bytes,VoiceIdentityVault.MAX_JSON.toLong())
            val text=bytes.toString("UTF-8");var depth=0;var quoted=false;var escape=false
            for(ch in text) {
                if(quoted) { if(escape) escape=false else if(ch=='\\') escape=true else if(ch=='"') quoted=false }
                else when(ch) { '"' -> quoted=true;'{','[' -> { depth++;require(depth<=32) { "Manifeste trop imbriqué" } };'}',']' -> { depth--;require(depth>=0) } }
            }
            require(!quoted && depth==0)
            val manifest=JSONObject(text);vault.validate(manifest,false)
            require(manifest.rows("identities").size==1)
            val identity=manifest.rows("identities").single();val expected=linkedMapOf<String,Long>()
            expected["NOTICES.txt"]=NOTICES.toByteArray().size.toLong()
            for(r in identity.rows("references")) if(r.getLong("bytes")>0) expected["references/${r.getString("id")}.wav"]=r.getLong("bytes")
            for(p in identity.rows("profiles")) expected["profiles/${p.getString("id")}.bin"]=p.getLong("bytes")
            ZipFile(clear).use { central ->
                val names=mutableSetOf<String>();val entries=central.entries()
                while(entries.hasMoreElements()) {
                    val e=entries.nextElement()
                    require(!e.isDirectory && names.add(e.name) && e.name in expected.keys+"manifest.json") { "Répertoire central ZIP incompatible ou dupliqué" }
                    require(e.size>=0 && e.size<=if(e.name=="manifest.json") VoiceIdentityVault.MAX_JSON.toLong() else expected.getValue(e.name))
                }
                require(names==expected.keys+"manifest.json")
            }
            val seen=mutableSetOf("manifest.json")
            while(true) {
                cancel.check();val entry=zip.nextEntry ?: break
                require(!entry.isDirectory && entry.name in expected && seen.add(entry.name)) { "Entrée d'archive inconnue, répertoire ou doublon" }
                require(entry.size<0 || entry.size==expected.getValue(entry.name)) { "Taille ZIP incompatible" }
                if(entry.name=="NOTICES.txt") {
                    val text=ByteArrayOutputStream();bounded(text,expected.getValue(entry.name));require(text.toString("UTF-8")==NOTICES)
                } else {
                    // No ZIP-derived path is ever resolved: opaque random private staging names only.
                    val file=File(work,UUID.randomUUID().toString()+".stage")
                    val actual=file.outputStream().use { bounded(it,expected.getValue(entry.name)) }
                    require(actual==expected.getValue(entry.name));staged[entry.name]=file
                }
            }
            require(seen==expected.keys+"manifest.json") { "Archive incomplète" }
            return vault.restore(manifest,staged,cancel)
        }
    }
}
