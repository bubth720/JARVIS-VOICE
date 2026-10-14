package com.jarvis.voice.pocket

import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream

/** Separate fixed FP32 manifest. Never accepts the 005D INT8 pack or arbitrary ZIP manifests. */
object ExperimentalModelPack {
    val lock: JSONObject by lazy { JSONObject(requireNotNull(javaClass.getResourceAsStream("/experimental-fp32-lock.json")).bufferedReader().use { it.readText() }) }
    val id: String get() = lock.getString("packId")
    val files: Set<String> get() = lock.getJSONObject("files").keys().asSequence().toSet()
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
            require(file.isFile && file.length() == spec.getLong("sizeBytes")) { "Pack FP32 incomplet : $name" }
            require(sha256(file, cancelled) == spec.getString("sha256")) { "Intégrité FP32 incorrecte : $name" }
        }
        val bundle = JSONObject(File(root, "bundle.json").readText())
        require(bundle.getInt("experimental_schema") == 1 && bundle.getString("precision") == "float32")
        require(bundle.getString("checkpoint_sha256") == lock.getString("checkpointSha256"))
        require(bundle.getBoolean("voice_state_includes_bos") && bundle.getInt("sample_rate") == 24000)
        require(File(root, "pack-identity.txt").readText().trim() == id)
        return bundle
    }
    /** New installation is staged and verified; failure/cancellation preserves the previous one. */
    fun install(input: InputStream, root: File, cancelled: () -> Boolean): File {
        root.mkdirs()
        val stage = File(root, "stage-${UUID.randomUUID()}").apply { mkdirs() }
        val current = File(root, "fp32"); val backup = File(root, "previous-${UUID.randomUUID()}")
        try {
            val seen = mutableSetOf<String>(); val expected = lock.getJSONObject("files")
            ZipInputStream(input).use { zip ->
                while (true) {
                    if (cancelled()) throw java.util.concurrent.CancellationException()
                    val entry = zip.nextEntry ?: break
                    require(!entry.isDirectory && entry.name in files && seen.add(entry.name)) { "Entrée ZIP FP32 inconnue ou dupliquée" }
                    val limit = expected.getJSONObject(entry.name).getLong("sizeBytes")
                    var bytes = 0L
                    File(stage, entry.name).outputStream().use { out ->
                        val buffer = ByteArray(65536)
                        while (true) {
                            if (cancelled()) throw java.util.concurrent.CancellationException()
                            val count = zip.read(buffer); if (count < 0) break
                            bytes += count; require(bytes <= limit) { "ZIP FP32 trop volumineux" }; out.write(buffer, 0, count)
                        }
                    }
                    require(bytes == limit) { "Fichier FP32 tronqué" }
                    zip.closeEntry()
                }
            }
            require(seen == files) { "Pack FP32 incomplet" }
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
