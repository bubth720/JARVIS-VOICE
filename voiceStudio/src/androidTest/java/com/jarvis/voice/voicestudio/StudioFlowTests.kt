package com.jarvis.voice.voicestudio
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.SystemClock
import android.view.*
import android.widget.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jarvis.voice.identity.*
import com.jarvis.voice.pocket.*
import com.jarvis.voice.studio.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class StudioFlowTests {
    private val i get()=InstrumentationRegistry.getInstrumentation()
    private val app get()=i.targetContext.applicationContext as StudioTestApplication
    private val c get()=app.controller
    private fun activity()=i.startActivitySync(Intent(i.targetContext,StudioActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as StudioActivity
    private fun texts(v: View): List<String> = when(v){is TextView->listOf(v.text.toString());is ViewGroup->(0 until v.childCount).flatMap { texts(v.getChildAt(it)) };else->emptyList()}
    private fun waitIdle() { val end=SystemClock.elapsedRealtime()+45000;while(c.busy && SystemClock.elapsedRealtime()<end) Thread.sleep(25);i.waitForIdleSync();assertFalse("Operation stuck: ${c.message}",c.busy) }
    private fun action(block: ()->Unit) { i.runOnMainSync(block);waitIdle() }
    private fun uri(file: String)=Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload%2F$file")
    private fun fixture(block: ()->Unit) {
        waitIdle();val before=c.state;val old=c.vault.selected();val ids=c.vault.list().map { it.id }.toSet();app.fixture.available=true
        try { block() } finally {
            app.fixture.hold=false;if(c.busy) action { c.stop() };waitIdle()
            for(row in c.vault.list().filter { it.id !in ids }) { File(i.targetContext.noBackupFilesDir,"identity-audio/${row.id.value}").deleteRecursively();c.library.choice.forget(row.id);c.vault.removeIdentity(row.id,true) }
            if(old!=null) c.vault.select(old);c.update { before };c.source.delete();app.fixture.available=false
        }
    }
    private fun readyVoice(): VoiceIdentityId {
        val id=SyntheticVault.identity(c.vault,"SYNTHETIC STUDIO");val r=SyntheticVault.reference(c.vault,id);val p=c.vault.saveProfile(id,r,SyntheticVault.profile(),CancellationToken())
        c.library.activate(id,p,CancellationToken());return id
    }
    @Test fun applicationIsIndependentOfflineNoBackupAndNoMic() {
        assertEquals("com.jarvis.voice.voicestudio005j",i.targetContext.packageName)
        val info=i.targetContext.packageManager.getPackageInfo(i.targetContext.packageName,PackageManager.GET_PERMISSIONS)
        assertTrue(info.requestedPermissions.orEmpty().none { it in setOf("android.permission.INTERNET","android.permission.RECORD_AUDIO","android.permission.READ_EXTERNAL_STORAGE","android.permission.MANAGE_EXTERNAL_STORAGE") })
        assertEquals(0,i.targetContext.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }
    @Test fun twoLocalFilesCreateOneIdentityAndTwoProfilesThroughWizardWhileOldVoiceRemainsActive()=fixture {
        val old=readyVoice();val oldProfile=c.library.activeConfirmed()
        action { c.update { it.copy(screen=StudioScreen.CREATE,wizard=WizardDraft(step=VoiceStep.CONSENT,name="Nouvelle synthétique")) };c.createIdentity(true) }
        val id=c.state.wizard.identityId;assertNotNull(id);assertEquals(old,c.vault.selected())
        for(name in listOf("stereo44.mp3","mono48.mp3")) {
            action { c.addSource(uri(name)) };assertEquals(VoiceStep.REVIEW,c.state.wizard.step);assertTrue(c.state.wizard.sourceMs!!>=12000)
            val decoded=StudioAudio.decode(c.source,requireNotNull(c.state.wizard.sourceFormat),0,5000,CancellationToken())
            println("STUDIO_SYNTHETIC_MP3 $name samples=${decoded.size} requested=120000")
            action { c.draft { it.copy(durationMs=5000,keepWav=true) };c.saveReference(true,true) };assertEquals(c.message,VoiceStep.ENCODE,c.state.wizard.step)
            action { c.encodeReference() };assertEquals(VoiceStep.TRY,c.state.wizard.step)
        }
        val row=c.vault.list().single { it.id.value==id };assertEquals(2,row.references.size);assertEquals(2,row.profiles.size)
        assertNotEquals(row.profiles[0].conditioningSha256,row.profiles[1].conditioningSha256)
        assertEquals(oldProfile,c.library.activeConfirmed());assertEquals(old,c.vault.selected())
        val a=activity();try { i.runOnMainSync { assertTrue(texts(a.window.decorView).any { it.contains("Étape 6 sur 7") });a.findViewById<View>(R.id.wizard_back).performClick();assertEquals(VoiceStep.ENCODE,c.state.wizard.step) } } finally { i.runOnMainSync { a.finish() } }
        assertEquals(2,c.vault.list().single { it.id.value==id }.profiles.size)
    }
    @Test fun short3And4SecondsAreRefusedEarlyAndFiveSecondsAcceptedWithoutChangingActiveVoice()=fixture {
        val old=readyVoice();action { c.draft { WizardDraft(name="New") };c.createIdentity(true) };val d=c.state.wizard
        for(name in listOf("short3.wav","short4.wav")) {
            action { c.addSource(uri(name)) };assertTrue(c.message.contains("5 secondes"));assertTrue(c.message.contains(if(name.contains("3")) "3.0" else "4.0"));assertEquals(d,c.state.wizard);assertEquals(old,c.vault.selected())
        }
        action { c.addSource(uri("exact5.wav")) };assertEquals(5000L,c.state.wizard.sourceMs);assertEquals(VoiceStep.REVIEW,c.state.wizard.step)
        val refs=c.wizardIdentity()!!.references.size;action { c.saveReference(false,true) };assertEquals(refs,c.wizardIdentity()!!.references.size);assertEquals(old,c.vault.selected())
    }
    @Test fun mp3WindowsAtZeroAndOneSecondAreExactAndTruncatedInputIsNeverPadded() {
        val file=File(i.targetContext.cacheDir,"synthetic-window.private")
        try {
            for(name in listOf("stereo44.mp3","mono48.mp3")) {
                i.context.assets.open(name).use { input -> file.outputStream().use(input::copyTo) }
                for(start in listOf(0L,1000L)) {
                    val pcm=LocalMp3.decode(file,start,5000,CancellationToken())
                    println("STUDIO_MP3_WINDOW $name startMs=$start samples=${pcm.size}")
                    assertEquals(120000,pcm.size);assertTrue(pcm.all { it.isFinite() });assertTrue(PcmStatistics.measure(pcm).peak>.02)
                }
            }
            i.context.assets.open("truncated.mp3").use { input -> file.outputStream().use(input::copyTo) }
            try { LocalMp3.decode(file,0,5000,CancellationToken());fail("Incomplete MP3 padded or accepted") } catch(_: IllegalArgumentException) { }
        } finally { file.delete() }
    }
    @Test fun encryptedJvmArchiveRestoresExactlyAndWrongPasswordDoesNotLoseOldVoice()=fixture {
        val old=readyVoice();val original=c.library.activeConfirmed();val before=File(c.vault.root,"vault.json").readBytes()
        action { c.restore(uri("jvm-synthetic.jvi"),"wrong-password-005h".toCharArray(),true) }
        assertEquals(old,c.vault.selected());assertArrayEquals(before,File(c.vault.root,"vault.json").readBytes());assertEquals(original,c.library.activeConfirmed())
        action { c.restore(uri("jvm-synthetic.jvi"),"synthetic-password-005h".toCharArray(),true) }
        val restored=c.wizardIdentity()!!;assertNotEquals(old,restored.id);assertEquals(2,restored.profiles.size);assertTrue(restored.references.all { it.retained })
        assertEquals(old,c.vault.selected());assertEquals(original,c.library.activeConfirmed());assertFalse(c.library.choice.isConfirmed(restored.profiles[1]))
        val encoded=app.fixture.calls.get();assertEquals(restored.name,c.state.wizard.name)
        action { c.draft { it.copy(step=VoiceStep.ENCODE) };c.encodeReference() }
        assertEquals(encoded,app.fixture.calls.get());assertEquals(VoiceStep.TRY,c.state.wizard.step)
        val hashes=restored.profiles.map { it.sha256 };action { c.activate(restored.id,restored.profiles[1].id) };assertEquals(hashes,c.currentIdentity()!!.profiles.map { it.sha256 });assertEquals(restored.profiles[1].id,c.library.activeConfirmed().id)
    }
    @Test fun actualUiSpeaksReplaysAndStopsWithoutRunningASecondModel()=fixture {
        readyVoice();action { c.update { it.copy(screen=StudioScreen.SPEAK,text="Bonjour à tous.") } };val a=activity()
        try {
            val start=app.fixture.calls.get();action { a.findViewById<View>(R.id.talk).performClick() };assertTrue(c.message.contains("Lecture terminée"));assertTrue(c.hasReplay())
            assertTrue(c.diagnostics.contains("EOS true"));assertTrue(c.diagnostics.contains("non finis 0"))
            val generated=app.fixture.calls.get();assertEquals(start+1,generated)
            action { a.findViewById<View>(R.id.replay).performClick() };assertEquals(generated,app.fixture.calls.get())
            assertTrue(c.diagnostics.contains("Mesures de génération avant lecture"));assertTrue(c.diagnostics.contains("CPU processus"))
            app.fixture.hold=true;i.runOnMainSync { a.findViewById<View>(R.id.talk).performClick() };i.waitForIdleSync();assertTrue(c.busy)
            action { a.findViewById<View>(R.id.stop).performClick() };app.fixture.hold=false;assertTrue(c.message.contains("Annulé"));assertTrue(c.hasReplay())
        } finally { i.runOnMainSync { a.finish() } }
    }
    @Test fun refusedPausesNeverSilentlyGenerateAndNeutralContextKeepsSameSettings()=fixture {
        readyVoice();action {
            c.update { it.copy(text="M. Martin arrive.\nNous sommes prêts.",settings=it.settings.copy(pauses=true,pauseMs=250)) }
            // Same boundary as StudioActivity.guard: this policy fails before a worker is started.
            runCatching { c.generate() }.onFailure(c::error)
        }
        assertTrue(c.message.contains("ambiguë"));assertFalse(c.hasReplay())
        val settings=c.state.settings
        action { c.update { it.copy(text="Bonjour à tous.\nNous sommes prêts.") };c.generate() };assertTrue(c.hasReplay());assertEquals(settings,c.state.settings)
        assertTrue(c.diagnostics.contains("pause"))
    }
    @Test fun pickerGuardRejectsRemoteProviderAndInvalidModelZipPreservesVoice()=fixture {
        readyVoice();val before=c.library.activeConfirmed();val a=activity()
        try {
            for(uri in listOf(Uri.parse("file:///private-name.wav"),Uri.parse("content://com.google.android.apps.docs.storage/document/private-name"))) action {
                StudioActivity::class.java.getDeclaredMethod("onActivityResult",Int::class.javaPrimitiveType,Int::class.javaPrimitiveType,Intent::class.java).apply { isAccessible=true }.invoke(a,201,Activity.RESULT_OK,Intent().setData(uri))
            }
            i.runOnMainSync { assertTrue(texts(a.window.decorView).any { it.contains("Fournisseur distant") });assertFalse(texts(a.window.decorView).any { it.contains("private-name") }) }
            action { c.importPack(uri("invalid-pack.zip")) };assertEquals(before,c.library.activeConfirmed());assertFalse(File(c.pack,"bundle.json").exists())
        } finally { i.runOnMainSync { a.finish() } }
    }
    @Test fun privateDraftSurvivesActivityRecreationWithoutConsentAutofillOrProfileLoss()=fixture {
        val id=readyVoice();action { c.update { it.copy(screen=StudioScreen.CREATE,wizard=WizardDraft(step=VoiceStep.CONSENT,name="Brouillon synthétique"),text="Texte local synthétique.") } }
        var a=activity()
        try {
            val monitor=i.addMonitor(StudioActivity::class.java.name,null,false)
            i.runOnMainSync { (a.findViewById<View>(R.id.consent) as android.widget.CompoundButton).isChecked=true;a.recreate() };a=requireNotNull(monitor.waitForActivityWithTimeout(5000)) as StudioActivity;i.removeMonitor(monitor);i.waitForIdleSync()
            i.runOnMainSync {
                assertEquals("Brouillon synthétique",(a.findViewById<TextView>(R.id.voice_name)).text.toString())
                assertFalse((a.findViewById<View>(R.id.consent) as android.widget.CompoundButton).isChecked)
                assertEquals(View.IMPORTANT_FOR_AUTOFILL_NO,a.findViewById<View>(R.id.voice_name).importantForAutofill)
            };assertEquals(id,c.vault.selected());assertEquals(c.state,StudioStateStore(File(i.targetContext.noBackupFilesDir,"studio-state.json")).load())
        } finally { i.runOnMainSync { a.finish() } }
    }
}
