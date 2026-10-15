package com.jarvis.voice.voicequality

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jarvis.voice.pocket.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class QualityABTests {
    @Test fun androidSamplerChangesTimeAndCurrentAtEverySubstep() {
        val times=mutableListOf<Pair<Float,Float>>();val inputs=mutableListOf<Float>()
        val out=LsdSampler.decode(FloatArray(32),3,CancellationToken()) { s,t,x ->
            times.add(s to t);inputs.add(x[0]);FloatArray(32) { 2*t+s+x[it] }
        }
        assertEquals(3,times.size);assertEquals(0f,times[0].first,0f);assertEquals(1f,times.last().second,0f)
        assertTrue(inputs[1]>inputs[0]);assertTrue(inputs[2]>inputs[1]);assertEquals(2.0246913f,out[0],2e-6f)
    }
    @Test fun privateABCacheRejectsAChangedSeedAndPreservesPcm() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val root=File(context.noBackupFilesDir,"synthetic-quality-ab")
        val store=QualityABStore(root);val cancel=CancellationToken()
        val voice=PersonalVoiceProfile(ExperimentalModelPack.id,linkedMapOf("synthetic-context-only" to VoiceTensor(0,longArrayOf(1),byteArrayOf(0,0,0,0))))
        try {
            val identity=store.context("Bonjour.",42,voice);val pcm=FloatArray(2400) { .01f }
            store.save("A",identity,"Bonjour.",42,QualitySampling(),pcm,cancel)
            assertArrayEquals(pcm,store.load("A",cancel),0f)
            store.requireSameAsA(identity,cancel)
            try { store.requireSameAsA(store.context("Bonjour.",43,voice),cancel);fail("Changed seed accepted") } catch(_: IllegalArgumentException) { }
            store.save("B",identity,"Bonjour.",42,QualitySampling(steps=5),pcm,cancel)
            assertTrue(store.exists("B"));store.delete();assertFalse(store.exists("A"));assertFalse(store.exists("B"))
        } finally { root.deleteRecursively() }
    }
    @Test fun noHumanRatingIsSavedWithoutAnAuditionAndExplicitScores() {
        val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
        val file=File(context.noBackupFilesDir,"human-ratings.json")
        assertFalse(file.exists())
        val activity=instrumentation.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        try {
            instrumentation.runOnMainSync {
                MainActivity::class.java.getDeclaredMethod("recordRating").apply { isAccessible=true }.invoke(activity)
            }
            assertFalse(file.exists())
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
}
