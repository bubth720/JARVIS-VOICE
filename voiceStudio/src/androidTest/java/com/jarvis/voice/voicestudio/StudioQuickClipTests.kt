package com.jarvis.voice.voicestudio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jarvis.voice.identity.*
import com.jarvis.voice.studio.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real Android Keystore cryptography; no model weights or personal user files needed. */
@RunWith(AndroidJUnit4::class)
class StudioQuickClipTests {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var store:StudioQuickClipStore
    private val p=VoiceProfile(
        "00000000-0000-4000-8000-000000000002",
        VoiceIdentityId("00000000-0000-4000-8000-000000000001"),
        "00000000-0000-4000-8000-000000000003",
        PocketIdentity.ENGINE,PocketIdentity.packId,
        "a".repeat(64),"b".repeat(64),ProfileStatus.VALID,"fixture",null)
    private val s=StudioSettings(seed=42,steps=1,temperature=.3)
    private val wave=FloatArray(2400) { .075f }
    @Before fun before() { store=StudioQuickClipStore(context);store.clear() }
    @After fun after() { store.clear() }
    @Test fun authenticatedRoundtrip() {
        store.save(QuickReactionPhrase.SPEECH,p,s,wave)
        val read=store.load(QuickReactionPhrase.SPEECH,p,s)
        assertArrayEquals(wave,requireNotNull(read),0f);read.fill(0f)
    }
    @Test fun neverCrossesProfile() {
        store.save(QuickReactionPhrase.SPEECH,p,s,wave)
        assertNull(store.load(QuickReactionPhrase.SPEECH,p.copy(id="00000000-0000-4000-8000-000000000004"),s))
    }
    @Test fun neverCrossesSampling() {
        store.save(QuickReactionPhrase.SPEECH,p,s,wave)
        assertNull(store.load(QuickReactionPhrase.SPEECH,p,s.copy(seed=43)))
    }
    @Test fun invalidCiphertextFailsClosed() {
        store.save(QuickReactionPhrase.SPEECH,p,s,wave)
        val f=File(context.noBackupFilesDir,"expressive-v3-reactions/clip-speech.enc")
        val b=f.readBytes();b[b.lastIndex]=(b.last().toInt() xor 0x55).toByte();f.writeBytes(b)
        assertNull(store.load(QuickReactionPhrase.SPEECH,p,s))
    }
    @Test fun deletionMakesReplayUnavailable() {
        store.save(QuickReactionPhrase.SPEECH,p,s,wave)
        store.clear();assertNull(store.load(QuickReactionPhrase.SPEECH,p,s))
    }
    @Test fun oversizeAudioRefused() {
        try { store.save(QuickReactionPhrase.SPEECH,p,s,FloatArray(72001) { .1f });fail("Unbounded PCM") }
        catch(_: IllegalArgumentException) { }
    }
}
