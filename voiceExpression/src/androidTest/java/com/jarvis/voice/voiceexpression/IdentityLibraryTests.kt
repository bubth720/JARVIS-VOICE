package com.jarvis.voice.voiceexpression

import android.app.*
import android.content.*
import android.content.pm.*
import android.net.Uri
import android.view.*
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jarvis.voice.identity.*
import com.jarvis.voice.pocket.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.*
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class IdentityLibraryTests {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private fun work(block: (File)->Unit) { val root=File(context.noBackupFilesDir,"synthetic-${UUID.randomUUID()}").apply { mkdir() };try { block(root) } finally { root.deleteRecursively() } }
    private fun text(v: View): List<String> = when(v) { is TextView -> listOf(v.text.toString());is ViewGroup -> (0 until v.childCount).flatMap { text(v.getChildAt(it)) };else -> emptyList() }
    private fun activity()=instrumentation.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
    @Test fun independentExperimentalPackageHasNoNetworkRecorderBackupAndProtectsScreen() {
        assertEquals("com.jarvis.voice.voiceexpression005i",context.packageName)
        val info=context.packageManager.getPackageInfo(context.packageName,PackageManager.GET_PERMISSIONS)
        assertTrue(info.requestedPermissions.orEmpty().none { it in setOf("android.permission.INTERNET","android.permission.RECORD_AUDIO","android.permission.MANAGE_EXTERNAL_STORAGE","android.permission.READ_EXTERNAL_STORAGE") })
        assertEquals(0,context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
        val a=activity();try { instrumentation.runOnMainSync {
            assertTrue(text(a.window.decorView).any { it.contains("005I — EXPÉRIMENTAL") });assertTrue(text(a.window.decorView).any { it.contains("005D/005F/005G") })
            assertTrue(a.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE!=0)
        } } finally { instrumentation.runOnMainSync { a.finish() } }
    }
    @Test fun syntheticMp3ImportsAppendTwoReferencesWithPrivateMono24kSegments()=work { root ->
        val v=VoiceIdentityVault(File(root,"vault"));val id=SyntheticVault.identity(v)
        for(name in listOf("stereo44.mp3","mono48.mp3")) {
            val input=File(root,name);instrumentation.context.assets.open(name).use { source -> input.outputStream().use(source::copyTo) }
            assertTrue(LocalMp3.inspect(input).durationMs>=12000)
            val pcm=LocalMp3.decode(input,1000,5000,CancellationToken());assertEquals(120000,pcm.size);assertTrue(pcm.all { it.isFinite() })
            assertTrue(ReferenceSelection.assess(pcm).peak>.02)
            v.addReference(id,pcm,"MP3",VoiceIdentityVault.sha(input),1000,ReferenceLabel.NEUTRE,"synthetic","test synthétique, pas une voix",true,true,true,CancellationToken(),sourceDurationMs=LocalMp3.inspect(input).durationMs)
            input.delete()
        }
        val loaded=VoiceIdentityVault(v.root);assertEquals(2,loaded.list().single().references.size)
        for(r in loaded.list().single().references) assertEquals(120000,loaded.readReference(id,r.id,CancellationToken()).size)
        assertFalse(root.listFiles()!!.any { it.extension=="mp3" })
    }
    @Test fun syntheticWavImportAndNonFiniteOrTruncatedAudioCannotCorruptTheLibrary()=work { root ->
        val file=File(root,"synthetic.wav");ReferenceAudio.writeWav(file,SyntheticVault.pcm());assertEquals(5000,IdentityWav.inspect(file).durationMs)
        assertEquals(120000,IdentityWav.decode(file,0,5000,CancellationToken()).size)
        file.writeBytes(file.readBytes().copyOf(100));SyntheticVault.reject { IdentityWav.decode(file,0,5000,CancellationToken()) }
        val v=VoiceIdentityVault(File(root,"vault"));val id=SyntheticVault.identity(v)
        SyntheticVault.reject { v.addReference(id,FloatArray(120000) { Float.NaN },"WAV","0".repeat(64),0,ReferenceLabel.INCONNUE,"","synthetic",true,true,true,CancellationToken()) }
        assertTrue(v.list().single().references.isEmpty())
    }
    @Test fun twoSyntheticProfilesAndActiveIdentitySurviveActivityRestart() {
        val v=VoiceIdentityVault(File(context.noBackupFilesDir,"identity-vault"));val previous=v.selected();val id=SyntheticVault.identity(v,"SYNTHETIC-RESTART-005I")
        try {
            val r1=SyntheticVault.reference(v,id);val r2=SyntheticVault.reference(v,id)
            val p1=v.saveProfile(id,r1,SyntheticVault.profile(),CancellationToken());val p2=v.saveProfile(id,r2,SyntheticVault.profile(.1f),CancellationToken());v.activate(id,p1,neutral=true)
            repeat(2) {
                val a=activity();try { instrumentation.runOnMainSync {
                    val labels=text(a.window.decorView);assertTrue(labels.any { it.contains("SYNTHETIC-RESTART-005I") && it.contains(p1) })
                } } finally { instrumentation.runOnMainSync { a.finish() } }
            }
            assertEquals(2,VoiceIdentityVault(v.root).list().single { it.id==id }.profiles.size)
            v.removeProfile(id,p2,true);assertEquals(p1,v.list().single { it.id==id }.profiles.single().id)
        } finally { v.removeIdentity(id,true);if(previous!=null) v.select(previous) }
    }
    @Test fun identityIsolationAndDisabledProfileRefusalsUseRealAndroidStorage()=work { root ->
        val v=VoiceIdentityVault(root);val a=SyntheticVault.identity(v,"A");val ra=SyntheticVault.reference(v,a);val pa=v.saveProfile(a,ra,SyntheticVault.profile(),CancellationToken())
        val b=SyntheticVault.identity(v,"B");val rb=SyntheticVault.reference(v,b);val pb=v.saveProfile(b,rb,SyntheticVault.profile(.1f),CancellationToken())
        SyntheticVault.reject { v.activate(a,pb) };SyntheticVault.reject { v.readReference(a,rb,CancellationToken()) }
        v.activate(a,pa);v.deactivate(a,pa);assertTrue(v.list().single { it.id==a }.activeProfiles.isEmpty());SyntheticVault.reject { v.binding().resolve(a,pa,CancellationToken()) }
        v.removeIdentity(a,true);assertEquals(pb,v.list().single().profiles.single().id)
    }
    @Test fun encodingBoundaryCancellationPreservesReferencesAndDoesNotCommitSyntheticOutput()=work { root ->
        val v=VoiceIdentityVault(root);val id=SyntheticVault.identity(v);val r=SyntheticVault.reference(v,id,keep=false)
        var entered=false
        try { IdentityEncoding.official(v,id,r,File(root,"missing-models"),CancellationToken().also { it.cancel() });fail("Expected cancellation before model access") } catch(_: java.util.concurrent.CancellationException) { }
        try { IdentityEncoding.create(v,id,r,CancellationToken()) { _,cancel -> entered=true;cancel.cancel();SyntheticVault.profile() };fail("Expected cancellation before commit") } catch(_: java.util.concurrent.CancellationException) { }
        assertTrue(entered);assertTrue(v.list().single().profiles.isEmpty());assertTrue(v.list().single().references.single().retained)
    }
    @Test fun pickerRejectsFileUrisAndCloudProvidersWithoutDisplayingPrivateLocation() {
        val a=activity();try {
            for(uri in listOf(Uri.parse("file:///private-personal-name.mp3"),Uri.parse("content://com.google.android.apps.docs.storage/document/private-personal-name"))) {
                instrumentation.runOnMainSync {
                    MainActivity::class.java.getDeclaredMethod("onActivityResult",Integer.TYPE,Integer.TYPE,Intent::class.java).apply { isAccessible=true }.invoke(a,201,Activity.RESULT_OK,Intent().setData(uri))
                    val labels=text(a.window.decorView);assertTrue(labels.any { it.contains("fournisseur distant ou inconnu refusé") });assertFalse(labels.any { it.contains("private-personal-name") })
                }
            }
            instrumentation.runOnMainSync {
                val local=Uri.parse("content://com.android.externalstorage.documents/document/primary:Download/synthetic.jvi")
                MainActivity::class.java.getDeclaredMethod("onActivityResult",Integer.TYPE,Integer.TYPE,Intent::class.java).apply { isAccessible=true }.invoke(a,204,Activity.RESULT_OK,Intent().setData(local))
                assertTrue(text(a.window.decorView).any { it.contains("consentement légal des voix de cette archive") })
            }
        } finally { instrumentation.runOnMainSync { a.finish() } }
    }
    @Test fun androidAeadRoundTripWrongPasswordAndTagTamperPreserveDestination()=work { root ->
        val source=VoiceIdentityVault(File(root,"source"));val id=SyntheticVault.identity(source);val r=SyntheticVault.reference(source,id);val p=source.saveProfile(id,r,SyntheticVault.profile(),CancellationToken());source.activate(id,p)
        val archive=VoiceIdentityArchive(File(root,"private-work"));val pass="synthetic-password-005h".toCharArray();val bytes=ByteArrayOutputStream()
        archive.export(source,id,pass.copyOf(),true,bytes,CancellationToken())
        val dest=VoiceIdentityVault(File(root,"dest"));val old=SyntheticVault.identity(dest,"old");val before=File(dest.root,"vault.json").readBytes()
        SyntheticVault.reject { archive.restore(dest,"wrong-password-005i".toCharArray(),bytes.toByteArray().inputStream(),CancellationToken()) }
        val bad=bytes.toByteArray().also { it[it.lastIndex]=(it.last().toInt() xor 1).toByte() }
        SyntheticVault.reject { archive.restore(dest,pass.copyOf(),bad.inputStream(),CancellationToken()) }
        assertArrayEquals(before,File(dest.root,"vault.json").readBytes());assertEquals(old,dest.selected())
        assertEquals(id,archive.restore(dest,pass.copyOf(),bytes.toByteArray().inputStream(),CancellationToken()));assertEquals(18,dest.binding().resolve(id,p,CancellationToken()).tensors.size)
        assertTrue(File(root,"private-work").listFiles()!!.isEmpty())
    }
    @Test fun jvmEncryptedSyntheticFixtureRestoresOnAndroidCryptoProvider()=work { root ->
        val dest=VoiceIdentityVault(File(root,"dest"));val archive=VoiceIdentityArchive(File(root,"private-work"))
        instrumentation.context.assets.open("jvm-synthetic.jvi").use { archive.restore(dest,"synthetic-password-005h".toCharArray(),it,CancellationToken()) }
        assertEquals(1,dest.list().size);assertEquals(2,dest.list().single().profiles.size);assertEquals(2,dest.list().single().references.count { it.retained })
        assertNotNull(dest.binding().resolve(dest.selected()!!,cancel=CancellationToken()))
        val unicode=VoiceIdentityVault(File(root,"unicode-dest"))
        instrumentation.context.assets.open("jvm-synthetic-unicode.jvi").use { archive.restore(unicode,"mot-de-passe-étoilé-005h🔒".toCharArray(),it,CancellationToken()) }
        assertEquals(dest.list(),unicode.list())
    }
    @Test fun androidCacheActuallyKeepsDifferentPcmAndRefusesChangedIdentityAndSeed()=work { root ->
        val v=VoiceIdentityVault(File(root,"vault"));val id=SyntheticVault.identity(v);val r=SyntheticVault.reference(v,id)
        val p1=v.saveProfile(id,r,SyntheticVault.profile(),CancellationToken());val p2=v.saveProfile(id,r,SyntheticVault.profile(.1f),CancellationToken());val profiles=v.list().single().profiles.associateBy { it.id }
        val c=CancellationToken();val store=IdentityComparisonStore(File(root,"ab"));val sampling=QualitySampling(3);val a=FloatArray(24000) { .01f };val b=FloatArray(24000) { -.02f }
        store.save("A",id,profiles.getValue(p1),"Bonjour.",42,sampling,a,c)
        SyntheticVault.reject { store.save("B",id,profiles.getValue(p2),"Bonjour.",43,sampling,b,c) }
        store.save("B",id,profiles.getValue(p2),"Bonjour.",42,sampling,b,c)
        assertArrayEquals(a,store.load("A",v,c),0f);assertArrayEquals(b,store.load("B",v,c),0f)
        val other=SyntheticVault.identity(v,"other");SyntheticVault.reject { store.load("A",v,c) };v.select(id)
        v.removeProfile(id,p2,true);SyntheticVault.reject { store.load("B",v,c) };assertNotEquals(id,other)
    }
    @Test fun thermalAndMemoryAdmissionAreNotWeakenedOnAndroid() {
        IdentityAdmission.requireHeavy(2048L*1048576,false,0)
        SyntheticVault.reject { IdentityAdmission.requireHeavy(2048L*1048576-1,false,0) }
        SyntheticVault.reject { IdentityAdmission.requireHeavy(4096L*1048576,true,0) }
        SyntheticVault.reject { IdentityAdmission.requireHeavy(4096L*1048576,false,3) }
    }
    @Test fun profileAudioDeletionDoesNotTouchAnotherIdentitySandboxPartition()=work { root ->
        val v=VoiceIdentityVault(File(root,"vault"));val a=SyntheticVault.identity(v,"A");val ra=SyntheticVault.reference(v,a);val pa=v.saveProfile(a,ra,SyntheticVault.profile(),CancellationToken())
        val b=SyntheticVault.identity(v,"B");val rb=SyntheticVault.reference(v,b);val pb=v.saveProfile(b,rb,SyntheticVault.profile(.1f),CancellationToken())
        val profiles=v.list().associateBy { it.id };val ar=File(root,"audio/${a.value}");val br=File(root,"audio/${b.value}");val ac=IdentityComparisonStore(ar);val bc=IdentityComparisonStore(br)
        val c=CancellationToken();val pcm=FloatArray(24000) { .01f }
        ac.save("A",a,profiles.getValue(a).profiles.single(),"Bonjour.",42,QualitySampling(),pcm,c)
        bc.save("A",b,profiles.getValue(b).profiles.single(),"Bonjour.",42,QualitySampling(),pcm,c)
        ac.deleteProfile(a,pa,c);v.removeProfile(a,pa,true)
        assertFalse(File(ar,"A").exists());assertArrayEquals(pcm,bc.load("A",v,c),0f);assertEquals(pb,v.list().single { it.id==b }.profiles.single().id)
    }
    @Test fun unknownSchemaAndMaliciousIdentityIdFailClosedWithoutReplacingIndex()=work { root ->
        val v=VoiceIdentityVault(root);SyntheticVault.identity(v)
        val index=File(root,"vault.json");val original=index.readText()
        for(bad in listOf(org.json.JSONObject(original).put("schema",99).toString(),original.replace(v.selected()!!.value,"../../outside"))) {
            index.writeText(bad);SyntheticVault.reject { VoiceIdentityVault(root) };assertEquals(bad,index.readText())
        }
    }
}
