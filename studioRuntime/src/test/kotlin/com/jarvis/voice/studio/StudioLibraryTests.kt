package com.jarvis.voice.studio
import com.jarvis.voice.identity.*
import com.jarvis.voice.pocket.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class StudioLibraryTests {
    private fun work(block: (File)->Unit) { val root=Files.createTempDirectory("studio-library-").toFile();try { block(root) } finally { root.deleteRecursively() } }
    @Test fun twoFilesBelongToOneNewIdentityAndNeverReplacePreviouslyActiveVoice()=work { root ->
        val v=VoiceIdentityVault(File(root,"v"));val old=SyntheticVault.identity(v,"Previous");val ref=SyntheticVault.reference(v,old);val pid=v.saveProfile(old,ref,SyntheticVault.profile(),CancellationToken())
        val lib=StudioLibrary(v,StudioProfileChoice(File(root,"choice")));lib.activate(old,pid,CancellationToken());val oldHash=lib.activeConfirmed().sha256
        val d=lib.create(WizardDraft(name="Nouvelle",note="synthetic consent"),true,CancellationToken());val id=VoiceIdentityId(d.identityId!!)
        assertEquals(old,v.selected());assertNotEquals(old,id)
        val first=lib.append(d.copy(sourceMs=12000,sourceFormat="WAV",durationMs=5000),SyntheticVault.pcm(),"0".repeat(64),true,true,CancellationToken())
        val pa=lib.encode(first,CancellationToken()) { _,_->SyntheticVault.profile(.1f) }
        val second=lib.append(first.copy(referenceId=null,sourceMs=10000,sourceFormat="MP3",durationMs=5000),SyntheticVault.pcm(330.0),"1".repeat(64),true,true,CancellationToken())
        val pb=lib.encode(second,CancellationToken()) { _,_->SyntheticVault.profile(.2f) }
        assertEquals(2,v.list().size);assertEquals(2,v.list().single { it.id==id }.references.size);assertEquals(2,v.list().single { it.id==id }.profiles.size)
        assertEquals(old,v.selected());assertEquals(pid,lib.activeConfirmed().id);assertEquals(oldHash,lib.activeConfirmed().sha256)
        assertNotEquals(pa,pb);lib.activate(id,pb,CancellationToken());assertEquals(pb,lib.activeConfirmed().id)
    }
    @Test fun invalidConsentRegionOrCanceledEncodingDoesNotSelectNewVoiceOrCommitProfile()=work { root ->
        val v=VoiceIdentityVault(File(root,"v"));val id=SyntheticVault.identity(v);val lib=StudioLibrary(v,StudioProfileChoice(File(root,"c")))
        SyntheticVault.reject { lib.create(WizardDraft(name="No"),false,CancellationToken()) };assertEquals(1,v.list().size)
        val d=WizardDraft(step=VoiceStep.REVIEW,identityId=id.value,name="x",sourceMs=12000,sourceFormat="WAV",durationMs=5000,keepWav=true)
        SyntheticVault.reject { lib.append(d,SyntheticVault.pcm(),"0".repeat(64),false,true,CancellationToken()) }
        SyntheticVault.reject { lib.append(d.copy(startMs=10000),SyntheticVault.pcm(),"0".repeat(64),true,true,CancellationToken()) }
        assertTrue(v.list().single().references.isEmpty())
        val saved=lib.append(d,SyntheticVault.pcm(),"0".repeat(64),true,true,CancellationToken());val token=CancellationToken()
        SyntheticVault.reject { lib.encode(saved,token) { _,c->c.cancel();SyntheticVault.profile() } }
        assertTrue(v.list().single().profiles.isEmpty());assertTrue(v.list().single().references.single().retained);assertEquals(id,v.selected())
    }
    @Test fun encryptedIArchiveRestoresExactlyButStillRequiresNewStudioConfirmation()=work { root ->
        val v=VoiceIdentityVault(File(root,"source"));val id=SyntheticVault.identity(v);val ref=SyntheticVault.reference(v,id)
        val pid=v.saveProfile(id,ref,SyntheticVault.profile(.3f),CancellationToken());v.activate(id,pid)
        val out=java.io.ByteArrayOutputStream();val a=VoiceIdentityArchive(File(root,"work"));a.export(v,id,"synthetic-password-005j".toCharArray(),false,out,CancellationToken())
        val dest=VoiceIdentityVault(File(root,"dest"));val lib=StudioLibrary(dest,StudioProfileChoice(File(root,"choice")))
        a.restore(dest,"synthetic-password-005j".toCharArray(),out.toByteArray().inputStream(),CancellationToken())
        assertEquals(v.list().single().profiles,dest.list().single().profiles);assertFalse(dest.list().single().references.single().retained)
        SyntheticVault.reject { lib.activeConfirmed() };lib.activate(id,pid,CancellationToken());assertEquals(pid,lib.activeConfirmed().id)
        lib.choice.clear();SyntheticVault.reject { lib.activeConfirmed() }
        val before=File(dest.root,"vault.json").readBytes();SyntheticVault.reject { a.restore(dest,"wrong-password-005j".toCharArray(),out.toByteArray().inputStream(),CancellationToken()) };assertArrayEquals(before,File(dest.root,"vault.json").readBytes())
    }
}
