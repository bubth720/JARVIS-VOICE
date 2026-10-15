package com.jarvis.voice.pocket

import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.BufferedInputStream
import java.io.IOException
import java.io.EOFException
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream
import java.util.zip.ZipException

/** Messages contain fixed model names or a sanitized basename, never a provider URI/path. */
class FP32ImportException(val reason: String, detail: String) : IllegalArgumentException("FP32 [$reason] : $detail")

/** Separate fixed FP32 manifest. Never accepts the 005D INT8 pack or arbitrary ZIP manifests. */
object ExperimentalModelPack {
    val lock: JSONObject by lazy {
        val bytes=javaClass.getResourceAsStream("/experimental-fp32-lock.json")?.use { it.readBytes() }
            ?: throw FP32ImportException("MANIFESTE_EMBARQUE", "manifeste absent de l'APK")
        val digest=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        if(digest!="4afe48a1616af4ddbe0fe8cb1d71c690477e171b860880790e91fff6777d1fa3")
            throw FP32ImportException("MANIFESTE_EMBARQUE", "empreinte du manifeste incompatible")
        JSONObject(bytes.toString(Charsets.UTF_8)).also {
            if(it.getInt("schemaVersion")!=1 || it.getString("precision")!="float32" || it.getJSONObject("files").length()!=10)
                throw FP32ImportException("MANIFESTE_EMBARQUE", "schéma incompatible")
        }
    }
    val id: String get() = lock.getString("packId")
    val files: Set<String> get() = lock.getJSONObject("files").keys().asSequence().toSet()
    private fun safeName(name: String): String {
        val base=name.trimEnd('/','\\').substringAfterLast('/').substringAfterLast('\\')
        return base.take(80).map { if(it.code in 32..126 && it!='"') it else '?' }.joinToString("")
    }
    /** Shared by platform and JVM regression tests; no normalization of accepted names. */
    fun validateEntry(name: String, directory: Boolean, seen: MutableSet<String>) {
        val label="entrée \"${safeName(name)}\""
        if(directory) throw FP32ImportException("REPERTOIRE", "$label : répertoire interdit")
        if(name !in files) throw FP32ImportException("NOM_INCONNU", "$label : nom absent du manifeste embarqué (10 fichiers)")
        if(!seen.add(name)) throw FP32ImportException("DOUBLON", "$label : entrée déjà lue")
    }
    fun sha256(file: File, cancelled: () -> Boolean = { false }): String {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val bytes = ByteArray(65536)
            while (true) {
                if (cancelled()) throw java.util.concurrent.CancellationException()
                val count = input.read(bytes); if (count < 0) break
                hash.update(bytes, 0, count)
            }
        }
        return hash.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    fun verify(root: File, cancelled: () -> Boolean = { false }): JSONObject {
        val expected = lock.getJSONObject("files")
        for (name in files) {
            val file = File(root, name); val spec = expected.getJSONObject(name)
            if(!file.isFile) throw FP32ImportException("MANQUANT", name)
            if(file.length()!=spec.getLong("sizeBytes")) throw FP32ImportException("TAILLE", "$name : taille différente du manifeste")
            if(sha256(file, cancelled)!=spec.getString("sha256")) throw FP32ImportException("SHA256", "$name : empreinte différente du manifeste")
        }
        val bundle = JSONObject(File(root, "bundle.json").readText())
        if(bundle.getInt("experimental_schema")!=1 || bundle.getString("precision")!="float32" ||
            bundle.getString("checkpoint_sha256")!=lock.getString("checkpointSha256") ||
            !bundle.getBoolean("voice_state_includes_bos") || bundle.getInt("sample_rate")!=24000 ||
            File(root,"pack-identity.txt").readText().trim()!=id)
            throw FP32ImportException("MANIFESTE_PACK", "identité, précision, BOS ou fréquence incompatible")
        return bundle
    }
    /** New installation is staged and verified; failure/cancellation preserves the previous one. */
    fun install(input: InputStream, root: File, cancelled: () -> Boolean): File {
        root.mkdirs()
        val stage = File(root, "stage-${UUID.randomUUID()}").apply { mkdirs() }
        val current = File(root, "fp32"); val backup = File(root, "previous-${UUID.randomUUID()}")
        try {
            val seen = mutableSetOf<String>(); val expected = lock.getJSONObject("files")
            val source=BufferedInputStream(input,65536)
            source.mark(4)
            val signature=ByteArray(4); var count=0
            while(count<4) { val n=source.read(signature,count,4-count); if(n<0) break; count+=n }
            source.reset()
            if(count!=4 || !signature.contentEquals(byteArrayOf(0x50,0x4b,0x03,0x04))) {
                source.close()
                throw FP32ImportException("DOCUMENT_NON_ZIP", "document sélectionné sans en-tête ZIP ; sélectionner le ZIP complet reconstitué")
            }
            var activeName: String?=null
            try { ZipInputStream(source).use { zip ->
                while (true) {
                    if (cancelled()) throw java.util.concurrent.CancellationException()
                    val entry = zip.nextEntry ?: break
                    activeName=entry.name
                    validateEntry(entry.name,entry.isDirectory,seen)
                    val limit = expected.getJSONObject(entry.name).getLong("sizeBytes")
                    var bytes = 0L
                    File(stage, entry.name).outputStream().use { out ->
                        val buffer = ByteArray(65536)
                        while (true) {
                            if (cancelled()) throw java.util.concurrent.CancellationException()
                            val count = zip.read(buffer); if (count < 0) break
                            bytes += count
                            if(bytes>limit) throw FP32ImportException("TAILLE", "${safeName(entry.name)} : taille décompressée supérieure au manifeste ($limit octets)")
                            out.write(buffer, 0, count)
                        }
                    }
                    if(bytes!=limit) throw FP32ImportException("TAILLE", "${safeName(entry.name)} : $bytes octets lus, $limit attendus")
                    zip.closeEntry()
                }
            } } catch(error: ZipException) {
                throw FP32ImportException("ZIP_INVALIDE", "${activeName?.let(::safeName) ?: "en-tête"} : données compressées, CRC ou structure invalides")
            } catch(error: EOFException) {
                throw FP32ImportException("ZIP_INVALIDE", "${activeName?.let(::safeName) ?: "en-tête"} : archive ZIP tronquée")
            } catch(error: IOException) {
                throw FP32ImportException("LECTURE", "${activeName?.let(::safeName) ?: "document"} : lecture ou écriture interrompue ; vérifier le document et le stockage")
            }
            if(seen.isEmpty()) throw FP32ImportException("ZIP_INVALIDE", "en-tête local incomplet ; aucune entrée ZIP lisible")
            if(seen!=files) throw FP32ImportException("MANQUANT", "${seen.size}/10 fichiers lus ; absent : ${files.minus(seen).first()}")
            verify(stage, cancelled)
            if (cancelled()) throw java.util.concurrent.CancellationException()
            if (current.exists()) check(current.renameTo(backup)) { "Ancien pack non préservé" }
            if (!stage.renameTo(current)) {
                if (backup.exists()) check(backup.renameTo(current))
                error("Activation FP32 impossible")
            }
            backup.deleteRecursively()
            return current
        } finally { stage.deleteRecursively() }
    }
}
