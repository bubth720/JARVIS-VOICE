package com.jarvis.voice.voicestudio
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jarvis.voice.expression.*
import com.jarvis.voice.identity.*
import com.jarvis.voice.pocket.*
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.Assert.*
import org.junit.runner.RunWith
import android.content.Intent
import java.util.UUID
@RunWith(AndroidJUnit4::class)
class Voice06AudioTests {
    private val i get()=InstrumentationRegistry.getInstrumentation()
    private var a: StudioActivity?=null
    @Before fun start() { a=i.startActivitySync(Intent(i.targetContext,StudioActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as StudioActivity;i.waitForIdleSync() }
    @After fun end() { i.runOnMainSync { a?.finish() } }
    private fun signal()=FloatArray(12000) { (kotlin.math.sin(it*2*kotlin.math.PI*440/24000)*.1).toFloat() }
    @Test fun threeSoftwareEnvelopesHaveCompleteRealAudioTrackPlayback() {
        for(style in listOf(VoiceEmotionIntent.JOY,VoiceEmotionIntent.SURPRISE,VoiceEmotionIntent.CALM)) {
            val c=CancellationToken();val plan=(VoiceEmotionController().plan(style,2,true) as EmotionPlanning.Ready).plan
            val r=BoundedPcmProsody.render(signal(),plan,c);assertTrue(r.changedSamples>0)
            PcmPlayer(i.targetContext,c).use { p->p.write(r.pcm);p.drain();assertTrue(p.completed) };assertFalse(r.cues.humanEmotionVerified)
        }
    }
    @Test fun comparisonRejectsWrongBindingRequiresBothPlaybacksAndErasesBuffers() {
        val binding=ExpressionVoiceBinding(VoiceIdentityId(UUID.randomUUID().toString()),UUID.randomUUID().toString(),"a".repeat(64),"b".repeat(64),PocketIdentity.ENGINE,PocketIdentity.packId)
        val trial=AudibleComparisonSession(binding,signal());val p=(VoiceEmotionController().plan(VoiceEmotionIntent.CALM,2,true) as EmotionPlanning.Ready).plan
        val effect=trial.apply(p,CancellationToken())
        try { trial.review(true,binding);fail() } catch(_: IllegalStateException) {}
        try { trial.withPcm("A",binding.copy(profileId=UUID.randomUUID().toString())) { fail() };fail() } catch(_: IllegalStateException) {}
        for(side in listOf("A","B")) {
            trial.withPcm(side,binding) { pcm->PcmPlayer(i.targetContext,CancellationToken()).use { audio->audio.write(pcm);audio.drain();assertTrue(audio.completed) } }
            trial.markPresented(side,binding)
        }
        trial.review(true,binding);assertTrue(trial.humanReviewRecorded);assertFalse(EmotionCapabilityMatrix.forIntent(VoiceEmotionIntent.CALM).humanValidated)
        trial.close();assertTrue(effect.pcm.all { it==0f });try { trial.withPcm("A",binding) {};fail() } catch(_: IllegalStateException) {}
    }
}
