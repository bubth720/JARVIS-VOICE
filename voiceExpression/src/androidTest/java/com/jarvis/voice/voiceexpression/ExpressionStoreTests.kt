package com.jarvis.voice.voiceexpression
import com.jarvis.voice.expression.*
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jarvis.voice.identity.*
import com.jarvis.voice.pocket.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

@RunWith(AndroidJUnit4::class)
class ExpressionStoreTests {
    private fun work(block: (File)->Unit) { val root=Files.createTempDirectory("expression-cache-").toFile();try { block(root) } finally { root.deleteRecursively() } }
    @Test fun abLocksExactProfileSeedTextSettingsAndActiveIdentity()=work { root ->
        val v=VoiceIdentityVault(File(root,"vault"));val id=SyntheticVault.identity(v);val ref=SyntheticVault.reference(v,id)
        val a=v.saveProfile(id,ref,SyntheticVault.profile(),CancellationToken());val other=v.saveProfile(id,ref,SyntheticVault.profile(.1f),CancellationToken());v.activate(id,a)
        val profiles=v.list().single().profiles.associateBy { it.id };val p=profiles.getValue(a);val store=ExpressionComparisonStore(File(root,"cache"));val c=CancellationToken();val pcm=floatArrayOf(.1f,.2f)
        store.save("A",id,p,"Bonjour.",42,QualitySampling(3),pcm,c)
        for((text,seed,settings) in listOf(Triple("Bonjour !",42L,QualitySampling(3)),Triple("Bonjour.",43L,QualitySampling(3)),Triple("Bonjour.",42L,QualitySampling(5)))) SyntheticVault.reject { store.save("B",id,p,text,seed,settings,pcm,c,"CURIOUS") }
        SyntheticVault.reject { store.save("B",id,profiles.getValue(other),"Bonjour.",42,QualitySampling(3),pcm,c,"CURIOUS") }
        store.save("B",id,p,"Bonjour.",42,QualitySampling(3),pcm,c,"CURIOUS")
        assertArrayEquals(store.load("A",v,c),store.load("B",v,c),0f);assertEquals("CURIOUS",store.metadata("B").getString("intent"))
        store.note("B",v,listOf(3,0,4,5,0),true,store.metadata("B"),c);assertTrue(store.metadata("B").has("evaluation"))
        v.activate(id,other);SyntheticVault.reject { store.load("B",v,c) };v.activate(id,a)
        assertEquals(p.sha256,v.list().single().profiles.single { it.id==a }.sha256)
        File(root,"cache/expression-B.json").writeText(store.metadata("B").put("context","0".repeat(64)).toString());SyntheticVault.reject { store.load("B",v,c) }
    }
    @Test fun archiveMigrationPreservesExactActiveProfileAndOmittedWavsWithoutReencoding()=work { root ->
        val h=VoiceIdentityVault(File(root,"h"));val id=SyntheticVault.identity(h);val ref=SyntheticVault.reference(h,id,label=ReferenceLabel.CHALEUREUSE)
        val a=h.saveProfile(id,ref,SyntheticVault.profile(),CancellationToken());val b=h.saveProfile(id,ref,SyntheticVault.profile(.1f),CancellationToken());h.activate(id,b)
        val c=CancellationToken();val before=h.list().single();val originalIndex=File(h.root,"vault.json").readBytes();val bytes=java.io.ByteArrayOutputStream();val archive=VoiceIdentityArchive(File(root,"private"))
        archive.export(h,id,"synthetic-password-005h".toCharArray(),false,bytes,c)
        val i=VoiceIdentityVault(File(root,"i"));archive.restore(i,"synthetic-password-005h".toCharArray(),bytes.toByteArray().inputStream(),c)
        val after=i.list().single();assertEquals(before.id,after.id);assertEquals(before.profiles,after.profiles);assertEquals(b,after.activeProfiles[PocketIdentity.bindingKey]);assertFalse(after.references.single().retained);assertEquals(ReferenceLabel.CHALEUREUSE,after.references.single().label)
        assertEquals(VoiceIdentityVault.conditioningSha(h.binding().resolve(id,b,c)),VoiceIdentityVault.conditioningSha(i.binding().resolve(id,b,c)))
        assertArrayEquals(originalIndex,File(h.root,"vault.json").readBytes());assertNotEquals(a,b)
        val destIndex=File(i.root,"vault.json").readBytes()
        SyntheticVault.reject { archive.restore(i,"wrong-password-005h".toCharArray(),bytes.toByteArray().inputStream(),c) }
        val tampered=bytes.toByteArray().also { it[it.lastIndex]=(it.last().toInt() xor 1).toByte() };SyntheticVault.reject { archive.restore(i,"synthetic-password-005h".toCharArray(),tampered.inputStream(),c) }
        assertArrayEquals(destIndex,File(i.root,"vault.json").readBytes())
    }
    @Test fun canceledSaveAndDeletionAreIsolatedAndNotesNeedRealAudition()=work { root ->
        val v=VoiceIdentityVault(File(root,"v"));val id=SyntheticVault.identity(v);val ref=SyntheticVault.reference(v,id);val pid=v.saveProfile(id,ref,SyntheticVault.profile(),CancellationToken());v.activate(id,pid)
        val p=v.list().single().profiles.single();val c=CancellationToken();val s=ExpressionComparisonStore(File(root,"one"));val other=ExpressionComparisonStore(File(root,"two"));val pcm=floatArrayOf(.1f)
        for(store in listOf(s,other)) store.save("A",id,p,"Bonjour.",42,QualitySampling(),pcm,c)
        SyntheticVault.reject { s.save("B",id,p,"Bonjour.",42,QualitySampling(),pcm,CancellationToken().also { it.cancel() },"SERIOUS") }
        SyntheticVault.reject { s.note("A",v,listOf(1,2,3,4,5),false,s.metadata("A"),c) }
        s.deleteProfile(id,pid,c);assertArrayEquals(pcm,other.load("A",v,c),0f)
    }
}
