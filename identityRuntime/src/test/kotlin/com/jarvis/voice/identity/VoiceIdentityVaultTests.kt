package com.jarvis.voice.identity

import com.jarvis.voice.pocket.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

class VoiceIdentityVaultTests {
    private fun work(action: (File)->Unit) { val dir=Files.createTempDirectory("identity-unit-").toFile();try { action(dir) } finally { dir.deleteRecursively() } }
    @Test fun twoReferencesTwoProfilesSurviveRestartAndSelectionWithoutOverwriting()=work { root ->
        val v=VoiceIdentityVault(root);val id=SyntheticVault.identity(v)
        val r1=SyntheticVault.reference(v,id);val r2=SyntheticVault.reference(v,id,label=ReferenceLabel.EXPRESSIVE)
        val p1=v.saveProfile(id,r1,SyntheticVault.profile(.01f),CancellationToken());val p2=v.saveProfile(id,r2,SyntheticVault.profile(.02f),CancellationToken())
        SyntheticVault.reject { v.binding().resolve(id,cancel=CancellationToken()) }
        v.activate(id,p1,neutral=true);v.rename(id,"Nom édité")
        val reloaded=VoiceIdentityVault(root);assertEquals(id,reloaded.selected());val identity=reloaded.list().single()
        assertEquals("Nom édité",identity.name);assertEquals(r1,identity.preferredNeutralReferenceId);assertEquals(2,identity.references.size);assertEquals(2,identity.profiles.size)
        assertEquals(p1,identity.activeProfiles[PocketIdentity.bindingKey]);assertNotEquals(identity.profiles[0].sha256,identity.profiles[1].sha256)
        assertEquals(.01f,java.nio.ByteBuffer.wrap(reloaded.binding().resolve(id,cancel=CancellationToken()).tensors.values.first { it.dtype==0 }.bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).float,0f)
        reloaded.activate(id,p2);assertEquals(p2,reloaded.list().single().activeProfiles[PocketIdentity.bindingKey])
    }
    @Test fun identityIsolationAndDestructiveConfirmationNeverSelectAnotherProfile()=work { root ->
        val v=VoiceIdentityVault(root);val a=SyntheticVault.identity(v,"A");val ra=SyntheticVault.reference(v,a);val pa=v.saveProfile(a,ra,SyntheticVault.profile(),CancellationToken())
        val b=SyntheticVault.identity(v,"B");val rb=SyntheticVault.reference(v,b);val pb=v.saveProfile(b,rb,SyntheticVault.profile(.1f),CancellationToken());v.activate(b,pb)
        SyntheticVault.reject { v.activate(a,pb) };SyntheticVault.reject { v.readReference(a,rb,CancellationToken()) }
        SyntheticVault.reject { v.removeProfile(a,pa,false) };SyntheticVault.reject { v.removeIdentity(a,false) };SyntheticVault.reject { v.removeReference(a,ra,true) }
        v.removeProfile(a,pa,true);assertEquals(pb,v.list().single { it.id==b }.profiles.single().id)
        v.removeIdentity(a,true);assertEquals(b,v.selected());assertNotNull(v.binding().resolve(b,cancel=CancellationToken()))
        v.removeIdentity(b,true);assertNull(v.selected());assertTrue(v.list().isEmpty())
    }
    @Test fun eachReferenceRequiresConsentAndSameSpeakerConfirmationAndNeutralIsManual()=work { root ->
        val v=VoiceIdentityVault(root)
        SyntheticVault.reject { v.create("A","note",false) };val id=SyntheticVault.identity(v)
        for((consent,same) in listOf(false to true,true to false)) SyntheticVault.reject {
            v.addReference(id,SyntheticVault.pcm(),"WAV","0".repeat(64),0,ReferenceLabel.INCONNUE,"","note",consent,same,true,CancellationToken())
        }
        assertTrue(v.list().single().references.isEmpty())
        val r=SyntheticVault.reference(v,id,label=ReferenceLabel.EXPRESSIVE);val p=v.saveProfile(id,r,SyntheticVault.profile(),CancellationToken())
        SyntheticVault.reject { v.activate(id,p,neutral=true) };v.annotateReference(id,r,ReferenceLabel.NEUTRE,"annotation humaine");v.activate(id,p,neutral=true)
        assertEquals(r,v.list().single().preferredNeutralReferenceId)
    }
    @Test fun compatibilityIntegrityAndSymlinksAreRefusedBeforeLoading()=work { root ->
        val v=VoiceIdentityVault(root);val id=SyntheticVault.identity(v);val r=SyntheticVault.reference(v,id);val p=v.saveProfile(id,r,SyntheticVault.profile(),CancellationToken());v.activate(id,p)
        SyntheticVault.reject { v.binding(pack="other").resolve(id,cancel=CancellationToken()) }
        SyntheticVault.reject { v.binding(engine="french24l").resolve(id,cancel=CancellationToken()) }
        val f=File(root,"objects/$p.bin");java.io.RandomAccessFile(f,"rw").use { it.seek(1000);it.writeByte(123) }
        SyntheticVault.reject { v.binding().resolve(id,p,CancellationToken()) }
        val wav=File(root,"objects/$r.wav");java.io.RandomAccessFile(wav,"rw").use { it.seek(100);it.writeByte(123) }
        SyntheticVault.reject { v.readReference(id,r,CancellationToken()) }
        val external=File(root,"external.wav");wav.renameTo(external);Files.createSymbolicLink(wav.toPath(),external.toPath())
        SyntheticVault.reject { v.readReference(id,r,CancellationToken()) }
    }
    @Test fun failureImmediatelyBeforeCommitPreservesIndexProfilesAndRetainedReferences()=work { root ->
        val fail=AtomicBoolean(false);val v=VoiceIdentityVault(root) { if(fail.get()) throw java.io.IOException("synthetic disk failure") }
        val id=SyntheticVault.identity(v);val r=SyntheticVault.reference(v,id);val p=v.saveProfile(id,r,SyntheticVault.profile(),CancellationToken());v.activate(id,p)
        val index=File(root,"vault.json").readBytes();val names=File(root,"objects").list()!!.toSet()
        fail.set(true);SyntheticVault.reject { v.saveProfile(id,r,SyntheticVault.profile(.2f),CancellationToken()) }
        assertArrayEquals(index,File(root,"vault.json").readBytes());assertEquals(names,File(root,"objects").list()!!.toSet())
        assertEquals(p,VoiceIdentityVault(root).list().single().profiles.single().id)
    }
    @Test fun preCancelledAndMidEncodingCancelledCannotAddProfileOrLoseReference()=work { root ->
        val v=VoiceIdentityVault(root);val id=SyntheticVault.identity(v);val r=SyntheticVault.reference(v,id,keep=false)
        var entered=false
        try { IdentityEncoding.create(v,id,r,CancellationToken().also { it.cancel() }) { _,_->entered=true;SyntheticVault.profile() };fail() } catch(_: CancellationException) { }
        assertFalse(entered)
        try { IdentityEncoding.create(v,id,r,CancellationToken()) { _,cancel -> entered=true;cancel.cancel();SyntheticVault.profile() };fail() } catch(_: CancellationException) { }
        assertTrue(entered);assertTrue(v.list().single().profiles.isEmpty());assertTrue(v.list().single().references.single().retained)
        v.saveProfile(id,r,SyntheticVault.profile(),CancellationToken());assertFalse(v.list().single().references.single().retained)
        SyntheticVault.reject { v.readReference(id,r,CancellationToken()) }
    }
    @Test fun interruptedOrUnknownSchemaFailsClosedWithoutResettingTheVault()=work { root ->
        val v=VoiceIdentityVault(root);val id=SyntheticVault.identity(v);val original=File(root,"vault.json").readText()
        for(bad in listOf("{",JSONObject(original).put("schema",99).toString(),JSONObject(original).apply { getJSONArray("identities").put(getJSONArray("identities").getJSONObject(0)) }.toString())) {
            File(root,"vault.json").writeText(bad);SyntheticVault.reject { VoiceIdentityVault(root) };assertEquals(bad,File(root,"vault.json").readText())
        }
        File(root,"vault.json").writeText(original);File(root,"objects/orphan.pending").writeText("interrupted")
        assertEquals(id,VoiceIdentityVault(root).selected());assertFalse(File(root,"objects/orphan.pending").exists())
    }
    @Test fun disabledProfilesAndMissingHumanScoresAreNotAutomaticallyActivatedOrRated()=work { root ->
        val v=VoiceIdentityVault(root);val id=SyntheticVault.identity(v);val r=SyntheticVault.reference(v,id);val p=v.saveProfile(id,r,SyntheticVault.profile(),CancellationToken());v.activate(id,p)
        v.deactivate(id,p);assertTrue(v.list().single().activeProfiles.isEmpty());SyntheticVault.reject { v.binding().resolve(id,p,CancellationToken()) }
        v.enable(id,p);assertTrue(v.list().single().activeProfiles.isEmpty())
        SyntheticVault.reject { v.noteProfile(id,p,listOf(1,2,3,4,5),false) };assertNull(v.list().single().profiles.single().evaluation)
        v.noteProfile(id,p,listOf(1,2,3,4,5),true);assertTrue(VoiceIdentityVault(root).list().single().profiles.single().evaluation!!.contains("heardOnDevice"))
    }
    @Test fun repeatedInstancesDoNotOverwriteEachOthersTransactionsAndQuotaRefusalIsAtomic()=work { root ->
        val a=VoiceIdentityVault(root);val b=VoiceIdentityVault(root);val id=SyntheticVault.identity(a)
        val t1=Thread { SyntheticVault.reference(a,id) };val t2=Thread { SyntheticVault.reference(b,id) };t1.start();t2.start();t1.join();t2.join()
        assertEquals(2,a.list().single().references.size)
        repeat(18) { SyntheticVault.reference(a,id) };val before=File(root,"vault.json").readBytes()
        SyntheticVault.reject { SyntheticVault.reference(b,id) };assertArrayEquals(before,File(root,"vault.json").readBytes());assertEquals(20,File(root,"objects").list()!!.size)
    }
    @Test fun comparisonUsesDistinctConditioningsAndRejectsOtherTextSeedSettingsIdentityOrInterruptedA()=work { root ->
        val v=VoiceIdentityVault(File(root,"vault"));val id=SyntheticVault.identity(v);val r=SyntheticVault.reference(v,id)
        val p1=v.saveProfile(id,r,SyntheticVault.profile(),CancellationToken());val p2=v.saveProfile(id,r,SyntheticVault.profile(.1f),CancellationToken())
        // Identical tensors serialized in a different order remain the SAME conditioning.
        val base=SyntheticVault.profile();val reordered=base.copy(tensors=base.tensors.entries.reversed().associate { it.key to it.value })
        val p3=v.saveProfile(id,r,reordered,CancellationToken());val ps=v.list().single().profiles.associateBy { it.id }
        assertNotEquals(ps.getValue(p1).sha256,ps.getValue(p3).sha256);assertEquals(ps.getValue(p1).conditioningSha256,ps.getValue(p3).conditioningSha256)
        val store=IdentityComparisonStore(File(root,"ab"));val sampling=QualitySampling(3);val c=CancellationToken();val pcm=SyntheticVault.pcm()
        store.save("A",id,ps.getValue(p1),"Bonjour.",42,sampling,pcm,c)
        val context=store.context(id,"Bonjour.",42,sampling)
        SyntheticVault.reject { store.requireB(id,ps.getValue(p1),context,c) };SyntheticVault.reject { store.requireB(id,ps.getValue(p3),context,c) }
        for(other in listOf(store.context(id,"Bonsoir.",42,sampling),store.context(id,"Bonjour.",43,sampling),store.context(id,"Bonjour.",42,QualitySampling(5)))) SyntheticVault.reject { store.requireB(id,ps.getValue(p2),other,c) }
        store.save("B",id,ps.getValue(p2),"Bonjour.",42,sampling,pcm,c);assertArrayEquals(pcm,store.load("B",v,c),0f)
        val other=SyntheticVault.identity(v,"autre");SyntheticVault.reject { store.load("A",v,c) };v.select(id)
        QualityABStore(File(root,"ab")).save("A",context,"Bonjour.",42,sampling,FloatArray(120000) { .2f },c)
        SyntheticVault.reject { store.load("B",v,c) };assertNotEquals(id,other)
    }
    @Test fun futureExpressionsAreExplicitlyUnsupportedAndAdmissionPreservesAllGuards() {
        assertEquals(setOf("intent","intensity","speed","pause","priority"),PocketIdentity.capabilities.unsupported(VoiceExpressionRequest("warm",.5,1.2,200,1)))
        IdentityAdmission.requireHeavy(2048L*1048576,false,0)
        for((ram,low,heat) in listOf(Triple(2048L*1048576-1,false,0),Triple(4096L*1048576,true,0),Triple(4096L*1048576,false,3))) SyntheticVault.reject { IdentityAdmission.requireHeavy(ram,low,heat) }
    }
}
