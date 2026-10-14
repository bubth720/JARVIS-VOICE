package com.jarvis.voice.pocket

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExperimentalImportTests {
    private fun archive(vararg names: String): ByteArray {
        val bytes=ByteArrayOutputStream()
        ZipOutputStream(bytes).use { out ->
            for(name in names) {
                out.putNextEntry(ZipEntry(name))
                if(name=="pack-identity.txt") out.write((ExperimentalModelPack.id+"\n").toByteArray())
                out.closeEntry()
            }
        }
        return bytes.toByteArray()
    }
    private fun rejects(bytes: ByteArray, reason: String): FP32ImportException {
        val root=Files.createTempDirectory("private-import-errors").toFile()
        val old=root.resolve("fp32").apply { mkdirs() }.resolve("preserve.txt").apply { writeText("previous installation") }
        try {
            try { ExperimentalModelPack.install(bytes.inputStream(),root) { false }; fail("Invalid pack accepted") }
            catch(error: FP32ImportException) {
                assertEquals(reason,error.reason)
                assertEquals("previous installation",old.readText())
                assertEquals(listOf("fp32"),root.listFiles()!!.map { it.name })
                return error
            }
            error("unreachable")
        } finally { root.deleteRecursively() }
    }
    @Test fun wrongDocumentDirectoryUnknownNameMissingAndSizeHaveDistinctReasons() {
        rejects("not a ZIP".toByteArray(),"DOCUMENT_NON_ZIP")
        rejects(archive("folder/"),"REPERTOIRE")
        val error=rejects(archive("private/path/unknown.txt"),"NOM_INCONNU")
        assertTrue(error.message!!.contains("unknown.txt"))
        assertFalse(error.message!!.contains("private/path"))
        rejects(archive("pack-identity.txt"),"MANQUANT")
        rejects(archive("MODEL_LICENSE.txt"),"TAILLE")
        rejects(byteArrayOf(0x50,0x4b,0x03,0x04,0,0),"ZIP_INVALIDE")
    }
    @Test fun duplicateLocalZipHeadersAreRejectedEvenWithValidFirstEntry() {
        val bytes=archive("pack-identity.txt","pack-identity.txX")
        val needle="pack-identity.txX".toByteArray()
        for(i in 0..bytes.size-needle.size) {
            if(needle.indices.all { bytes[i+it]==needle[it] }) bytes[i+needle.lastIndex]='t'.code.toByte()
        }
        rejects(bytes,"DOUBLON")
    }
    @Test fun everyOfficialStreamingNameIsAcceptedWithoutNormalization() {
        val seen=mutableSetOf<String>()
        for(name in ExperimentalModelPack.files) {
            java.util.zip.ZipInputStream(archive(name).inputStream()).use { zip ->
                val entry=zip.nextEntry!!
                ExperimentalModelPack.validateEntry(entry.name,entry.isDirectory,seen)
            }
        }
        assertEquals(10,seen.size)
        assertEquals(ExperimentalModelPack.files,seen)
    }
}
