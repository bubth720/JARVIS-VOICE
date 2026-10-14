package com.jarvis.voice.pocket

import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream

/** Only this pinned, public preset bundle is accepted. Imported audio is never a model pack. */
object ModelPack {
    val lock: JSONObject by lazy {
        JSONObject(requireNotNull(javaClass.getResourceAsStream("/model-lock.json"))
            .bufferedReader().use { it.readText() })
    }
    val files: Set<String> get() = lock.getJSONObject("files").keys().asSequence().toSet()

    fun verify(root: File, cancelled: () -> Boolean = { false }): JSONObject {
        val expected = lock.getJSONObject("files")
        for (name in files) {
            check(!cancelled()) { "Opération annulée" }
            val f = File(root, name)
            val spec = expected.getJSONObject(name)
            require(f.isFile && f.length() == spec.getLong("sizeBytes")) { "Fichier manquant ou taille incorrecte : $name" }
            val md = MessageDigest.getInstance("SHA-256")
            f.inputStream().use { input ->
                val block = ByteArray(65536)
                while (true) {
                    check(!cancelled()) { "Opération annulée" }
                    val n = input.read(block)
                    if (n < 0) break
                    md.update(block, 0, n)
                }
            }
            val hash = md.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
            require(hash == spec.getString("sha256")) { "SHA-256 incorrect : $name" }
        }
        val bundle = JSONObject(File(root, "bundle.json").readText())
        require(bundle.getInt("schema_version") == 3 && bundle.getString("language") == "fr") { "Pack français de schéma 3 requis" }
        require(bundle.getInt("sample_rate") == 24000 && bundle.getBoolean("voice_state_includes_bos")) { "Conditionnement incompatible" }
        return bundle
    }

    /** Staging and validation precede replacement; bad imports preserve the installed pack. */
    fun install(input: InputStream, privateRoot: File, cancelled: () -> Boolean = { false }): File {
        privateRoot.mkdirs()
        val staging = File(privateRoot, "staging-${UUID.randomUUID()}").apply { mkdirs() }
        val current = File(privateRoot, "fr")
        val backup = File(privateRoot, "previous-${UUID.randomUUID()}")
        try {
            val seen = mutableSetOf<String>()
            val expected = lock.getJSONObject("files")
            ZipInputStream(input).use { zip ->
                while (true) {
                    check(!cancelled()) { "Import annulé" }
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    require(!entry.isDirectory && name in files && seen.add(name)) { "Entrée ZIP inconnue ou dupliquée" }
                    val limit = expected.getJSONObject(name).getLong("sizeBytes")
                    var count = 0L
                    File(staging, name).outputStream().use { output ->
                        val bytes = ByteArray(65536)
                        while (true) {
                            check(!cancelled()) { "Import annulé" }
                            val n = zip.read(bytes)
                            if (n < 0) break
                            count += n
                            require(count <= limit) { "Fichier ZIP trop volumineux" }
                            output.write(bytes, 0, n)
                        }
                    }
                    require(count == limit) { "ZIP incomplet" }
                    zip.closeEntry()
                }
            }
            require(seen == files) { "Pack incomplet" }
            verify(staging, cancelled)
            check(!cancelled()) { "Import annulé" }
            if (current.exists()) check(current.renameTo(backup)) { "Impossible de préserver le pack précédent" }
            if (!staging.renameTo(current)) {
                if (backup.exists()) check(backup.renameTo(current)) { "Restauration nécessaire" }
                error("Impossible d'activer le pack")
            }
            backup.deleteRecursively()
            return current
        } finally { staging.deleteRecursively() }
    }
}
