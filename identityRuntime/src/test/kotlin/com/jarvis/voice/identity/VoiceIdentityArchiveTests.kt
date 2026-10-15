package com.jarvis.voice.identity

import com.jarvis.voice.pocket.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.nio.file.Files
import java.util.zip.*

class VoiceIdentityArchiveTests {
    private val password="synthetic-password-005h".toCharArray()
    private fun work(action: (File)->Unit) { val d=Files.createTempDirectory("identity-archive-").toFile();try { action(d) } finally { d.deleteRecursively() } }
    private fun source(root: File): Pair<VoiceIdentityVault,VoiceIdentityId> {
        val v=VoiceIdentityVault(root);val id=SyntheticVault.identity(v,"PRIVATE-SYNTHETIC-IDENTITY")
        repeat(2) { index -> val r=SyntheticVault.reference(v,id);val p=v.saveProfile(id,r,SyntheticVault.profile(index*.1f),CancellationToken());if(index==0) v.activate(id,p,neutral=true) }
        return v to id
    }
    private fun encrypted(archive: VoiceIdentityArchive,v: VoiceIdentityVault,id: VoiceIdentityId,refs: Boolean=true): ByteArray {
        val out=ByteArrayOutputStream();archive.export(v,id,password.copyOf(),refs,out,CancellationToken());return out.toByteArray()
    }
    @Test fun authenticatedRoundTripPreservesLogicalIdsProfilesReferencesAndActiveChoice()=work { root ->
        val (v,id)=source(File(root,"source"));val archive=VoiceIdentityArchive(File(root,"private-work"))
        val a=encrypted(archive,v,id);val b=encrypted(archive,v,id)
        System.getenv("IDENTITY_ARCHIVE_FIXTURE_OUT")?.takeIf { it.isNotBlank() }?.let { File(it).apply { parentFile.mkdirs();writeBytes(a) } }
        assertFalse(a.contentEquals(b));assertEquals("JVIENC01",a.copyOfRange(0,8).toString(Charsets.US_ASCII))
        assertFalse(a.toString(Charsets.ISO_8859_1).contains("PRIVATE-SYNTHETIC-IDENTITY"))
        val dest=VoiceIdentityVault(File(root,"dest"));assertEquals(id,archive.restore(dest,password.copyOf(),a.inputStream(),CancellationToken()))
        assertEquals(v.list(),dest.list());assertEquals(id,dest.selected());assertEquals(18,dest.binding().resolve(id,cancel=CancellationToken()).tensors.size)
        assertTrue(File(root,"private-work").listFiles()!!.isEmpty())
    }
    @Test fun wrongPasswordTamperTruncationHeaderCostAndExistingIdentityNeverMutateDestination()=work { root ->
        val (v,id)=source(File(root,"source"));val archive=VoiceIdentityArchive(File(root,"private-work"));val good=encrypted(archive,v,id)
        val dest=VoiceIdentityVault(File(root,"dest"));val old=SyntheticVault.identity(dest,"existing")
        val before=File(dest.root,"vault.json").readBytes()
        val bad=good.copyOf().also { it[it.lastIndex]=(it.last().toInt() xor 1).toByte() }
        val changedCost=good.copyOf().also { it[11]=(it[11].toInt() xor 1).toByte() }
        for((bytes,pass) in listOf(good to "incorrect-password".toCharArray(),bad to password,good.copyOf(35) to password,changedCost to password,good.copyOf(good.size-10) to password)) {
            SyntheticVault.reject { archive.restore(dest,pass.copyOf(),bytes.inputStream(),CancellationToken()) }
            assertArrayEquals(before,File(dest.root,"vault.json").readBytes());assertEquals(old,dest.selected())
            assertTrue(File(root,"private-work").listFiles()!!.isEmpty())
        }
        archive.restore(dest,password.copyOf(),good.inputStream(),CancellationToken());val after=File(dest.root,"vault.json").readBytes()
        SyntheticVault.reject { archive.restore(dest,password.copyOf(),good.inputStream(),CancellationToken()) };assertArrayEquals(after,File(dest.root,"vault.json").readBytes())
    }
    @Test fun referencesAreOptionalAndCancelledImportOrOutputFailurePreservesEveryProfile()=work { root ->
        val (v,id)=source(File(root,"source"));val archive=VoiceIdentityArchive(File(root,"private-work"));val bytes=encrypted(archive,v,id,false)
        val dest=VoiceIdentityVault(File(root,"dest"));archive.restore(dest,password.copyOf(),bytes.inputStream(),CancellationToken())
        assertEquals(2,dest.list().single().profiles.size);assertTrue(dest.list().single().references.none { it.retained })
        assertNotNull(dest.binding().resolve(id,cancel=CancellationToken()))
        val before=File(dest.root,"vault.json").readBytes()
        SyntheticVault.reject { archive.restore(dest,password.copyOf(),bytes.inputStream(),CancellationToken().also { it.cancel() }) }
        assertArrayEquals(before,File(dest.root,"vault.json").readBytes())
        val inputBefore=File(v.root,"vault.json").readBytes()
        val failing=object: OutputStream() { override fun write(b: Int) { throw IOException("synthetic output failure") } }
        SyntheticVault.reject { archive.export(v,id,password.copyOf(),true,failing,CancellationToken()) }
        assertArrayEquals(inputBefore,File(v.root,"vault.json").readBytes());assertTrue(File(root,"private-work").listFiles()!!.isEmpty())
    }
    @Test fun authenticatedMaliciousZipNamesDirectoriesDuplicatesHashesAndMissingFilesAreRejectedAtomically()=work { root ->
        val (v,id)=source(File(root,"source"));val archive=VoiceIdentityArchive(File(root,"private-work"));val dest=VoiceIdentityVault(File(root,"dest"))
        SyntheticVault.identity(dest,"untouched");val before=File(dest.root,"vault.json").readBytes()
        v.snapshot(id,true) { manifest,files ->
            fun zip(extra: String?=null,omit: String?=null,corrupt: Boolean=false,badManifest: JSONObject=manifest): File {
                val path=File(root,"malicious.private")
                ZipOutputStream(path.outputStream()).use { out ->
                    fun entry(name: String,bytes: ByteArray) { out.putNextEntry(ZipEntry(name));out.write(bytes);out.closeEntry() }
                    entry("manifest.json",badManifest.toString().toByteArray());entry("NOTICES.txt",VoiceIdentityArchive.NOTICES.toByteArray())
                    files.entries.filter { it.key!=omit }.forEachIndexed { n,(name,f) -> entry(name,f.readBytes().also { if(corrupt && n==0) it[100]=(it[100].toInt() xor 1).toByte() }) }
                    if(extra!=null) entry(extra,ByteArray(1))
                };return path
            }
            fun refuse(file: File) {
                val stage=File(root,"staging").apply { mkdir() }
                try { SyntheticVault.reject { archive.restoreAuthenticatedZip(dest,file,stage,CancellationToken()) } }
                finally { stage.deleteRecursively() }
                assertArrayEquals(before,File(dest.root,"vault.json").readBytes())
                assertTrue(File(dest.root,"objects").listFiles()!!.isEmpty())
            }
            for(name in listOf("../escape.bin","references/","unknown","/absolute.bin")) refuse(zip(extra=name))
            refuse(zip(omit=files.keys.first()));refuse(zip(corrupt=true))
            val incompatible=JSONObject(manifest.toString());incompatible.rows("identities").single().rows("profiles").first().put("packId","other-pack")
            refuse(zip(badManifest=incompatible))
            // Duplicate local+central manifest entries, bypassing ZipOutputStream's own duplicate-name guard.
            val dup=zip(extra="manifesx.json");val bytes=dup.readBytes();val old="manifesx.json".toByteArray();val new="manifest.json".toByteArray()
            for(i in 0..bytes.size-old.size) if(old.indices.all { bytes[i+it]==old[it] }) new.copyInto(bytes,i)
            dup.writeBytes(bytes);refuse(dup)
            val large=JSONObject(manifest.toString());large.rows("identities").single().rows("profiles").first().put("bytes",10000001)
            refuse(zip(badManifest=large))
        }
        assertFalse(File(root,"escape.bin").exists())
    }
    @Test fun failedRestoreCommitRollsBackAllNewFilesAndKeepsPreviousIdentity()=work { root ->
        val (v,id)=source(File(root,"source"));val archive=VoiceIdentityArchive(File(root,"private-work"));val bytes=encrypted(archive,v,id)
        var fail=false;val dest=VoiceIdentityVault(File(root,"dest")) { if(fail) throw IOException("synthetic transaction interruption") }
        val old=SyntheticVault.identity(dest,"old");val before=File(dest.root,"vault.json").readBytes();fail=true
        SyntheticVault.reject { archive.restore(dest,password.copyOf(),bytes.inputStream(),CancellationToken()) }
        assertArrayEquals(before,File(dest.root,"vault.json").readBytes());assertEquals(old,dest.selected());assertTrue(File(dest.root,"objects").listFiles()!!.isEmpty())
    }
}
