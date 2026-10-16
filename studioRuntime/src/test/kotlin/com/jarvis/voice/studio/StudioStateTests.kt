package com.jarvis.voice.studio
import com.jarvis.voice.identity.*
import com.jarvis.voice.expression.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class StudioStateTests {
    private fun reject(block: ()->Unit) { try { block();fail("Refus attendu") } catch(_: IllegalArgumentException) {} }
    @Test fun navigationAndInterruptedWizardRoundTripRetainPrivateDraftButNoConsent() {
        val f=Files.createTempDirectory("studio-state-").toFile()
        try {
            val store=StudioStateStore(java.io.File(f,"state.json"));assertEquals(StudioState(),store.load())
            val w=WizardDraft(step=VoiceStep.REVIEW,identityId="00000000-0000-0000-0000-000000000001",name="Test local",note="synthétique",sourceMs=12000,sourceFormat="MP3",durationMs=5000)
            val s=StudioState(StudioScreen.CREATE,w,StudioSettings(pauses=true,pauseMs=500),"Bonjour à tous.\nNous sommes prêts.")
            store.save(s);assertEquals(s,StudioStateStore(java.io.File(f,"state.json")).load());assertEquals(VoiceStep.AUDIO,WizardPolicy.back(w.step))
            assertFalse(java.io.File(f,"state.json").readText().contains("password"));assertFalse(java.io.File(f,"state.json").readText().contains("consentConfirmed"))
            assertEquals(VoiceStep.ENCODE,WizardPolicy.next(w,true,false));reject { WizardPolicy.next(w,false,false) }
        } finally { f.deleteRecursively() }
    }
    @Test fun durationAndBoundariesRejectShortFilesAndNeverCropSilently() {
        for(ms in listOf(3000L,4000L,4999L,180001L)) reject { WizardPolicy.requireSource(ms) }
        for(ms in listOf(5000L,12000L,180000L)) WizardPolicy.requireSource(ms)
        WizardPolicy.requireRegion(12000,7000,5000)
        for((start,duration) in listOf(8000L to 5000L,0L to 4000L,0L to 16000L)) reject { WizardPolicy.requireRegion(12000,start,duration) }
    }
    @Test fun simpleSettingsKeepNeutralInputsAndRequireExplicitPauseOptIn() {
        val id=VoiceIdentityId("00000000-0000-0000-0000-000000000001");val s=StudioSettings()
        assertFalse(s.pauses);assertEquals(0,s.request("Bonjour.",id).pauseMs);assertFalse(s.request("Bonjour.",id).allowSentencePauses)
        val changed=s.copy(intent=VoiceExpressionIntent.CONVERSATION,pauses=true,pauseMs=250)
        assertEquals(s.sampling(),changed.sampling());assertTrue(changed.request("Bonjour à tous.\nNous sommes prêts.",id).allowSentencePauses)
        assertEquals("NEUTRAL",changed.request("Bonjour.",id,true).expressionIntent);assertEquals(0,changed.request("Bonjour.",id,true).pauseMs)
    }
    @Test fun profileConfirmationNeverAcceptsDifferentIdentityHashOrDisabledProfile() {
        val f=Files.createTempDirectory("studio-choice-").toFile()
        try {
            val p=VoiceProfile("00000000-0000-0000-0000-000000000002",VoiceIdentityId("00000000-0000-0000-0000-000000000001"),"00000000-0000-0000-0000-000000000003",PocketIdentity.ENGINE,PocketIdentity.packId,"a".repeat(64),"b".repeat(64),ProfileStatus.VALID,"synthetic",null)
            val c=StudioProfileChoice(java.io.File(f,"choice.json"));assertFalse(c.isConfirmed(p));c.confirm(p);assertTrue(c.isConfirmed(p));assertFalse(c.isConfirmed(p.copy(sha256="c".repeat(64))));assertFalse(c.isConfirmed(p.copy(status=ProfileStatus.DISABLED)))
        } finally { f.deleteRecursively() }
    }
}
