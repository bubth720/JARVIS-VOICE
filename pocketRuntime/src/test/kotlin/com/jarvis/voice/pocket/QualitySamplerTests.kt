package com.jarvis.voice.pocket

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CancellationException

class QualitySamplerTests {
    @Test fun matchesOfficialTorchTraceAndScheduleForEveryStepCount() {
        val fixture=JSONObject(javaClass.getResourceAsStream("/mission005g/official-lsd-fixture.json")!!.bufferedReader().use { it.readText() }).getJSONArray("cases")
        fun array(json: org.json.JSONArray)=FloatArray(json.length()) { json.getDouble(it).toFloat() }
        for(i in 0 until fixture.length()) {
            val item=fixture.getJSONObject(i);val steps=item.getInt("steps");val trace=item.getJSONArray("trace");var index=0
            val result=LsdSampler.decode(array(item.getJSONArray("noise")),steps,CancellationToken()) { s,t,current ->
                val ref=trace.getJSONObject(index++)
                assertEquals(ref.getDouble("s").toFloat(),s,0f);assertEquals(ref.getDouble("t").toFloat(),t,0f)
                assertArrayEquals(array(ref.getJSONArray("current")),current,2e-4f)
                array(ref.getJSONArray("direction"))
            }
            assertEquals(steps,index);assertArrayEquals(array(item.getJSONArray("expected")),result,2e-4f)
        }
    }
    @Test fun cancelledAndInvalidFieldCannotProduceASample() {
        try { LsdSampler.decode(FloatArray(32),5,CancellationToken().also { it.cancel() }) { _,_,_ -> fail("Cancelled sampler called Flow"); FloatArray(32) };fail() } catch(_: CancellationException) { }
        try { LsdSampler.decode(FloatArray(32),3,CancellationToken()) { _,_,_ -> FloatArray(32) { Float.NaN } };fail() } catch(_: IllegalArgumentException) { }
    }
    @Test fun referenceWarningsAndContiguousWindowPreserveInput() {
        val pcm=FloatArray(240000) { if(it<120000) 0f else kotlin.math.sin(it*.1).toFloat()*.15f };val copy=pcm.copyOf()
        val assessment=ReferenceSelection.assess(pcm)
        assertTrue(assessment.quietFramePercent>=50);assertEquals(5.0,assessment.leadingQuietSeconds,.03)
        val region=ReferenceSelection.suggestFiveSeconds(pcm);assertEquals(120000,region.first);assertEquals(120000,region.count())
        assertArrayEquals(copy,pcm,0f)
        assertTrue(ReferenceSelection.assess(FloatArray(120000) { 1f }).clippedPercent>99)
    }
    @Test fun comparisonRejectsChangedTextSeedVoiceAndCorruptedAudio() {
        val root=java.nio.file.Files.createTempDirectory("quality-ab-test").toFile()
        try {
            val store=QualityABStore(root);val cancel=CancellationToken()
            val voice=PersonalVoiceProfile(ExperimentalModelPack.id,linkedMapOf("synthetic" to VoiceTensor(0,longArrayOf(1),byteArrayOf(0,0,0,0))))
            val context=store.context("Bonjour.",42,voice);val pcm=FloatArray(100) { .01f }
            store.save("A",context,"Bonjour.",42,QualitySampling(),pcm,cancel)
            store.requireSameAsA(context,cancel)
            for(changed in listOf(store.context("Bonsoir.",42,voice),store.context("Bonjour.",43,voice),store.context("Bonjour.",42,voice.copy(tensors=linkedMapOf("synthetic" to VoiceTensor(0,longArrayOf(1),byteArrayOf(1,0,0,0))))))) {
                try { store.requireSameAsA(changed,cancel);fail("Mismatched comparison accepted") } catch(_: IllegalArgumentException) { }
            }
            store.save("B",context,"Bonjour.",42,QualitySampling(steps=5),pcm,cancel)
            assertArrayEquals(pcm,store.load("B",cancel),0f)
            store.save("A",store.context("Bonsoir.",42,voice),"Bonsoir.",42,QualitySampling(),pcm,cancel)
            try { store.loadComparable("B",cancel);fail("Stale B compared with a new A") } catch(_: IllegalArgumentException) { }
            store.delete();assertFalse(store.exists("A"));assertFalse(store.exists("B"))
        } finally { root.deleteRecursively() }
    }
    @Test fun qualityOneStepExactlyPreservesStableEngineWithSamePublicProfileAndSeed() {
        val dir=System.getenv("POCKET_EXPERIMENTAL_MODEL_DIR").orEmpty();assumeTrue("Private FP32 pack required",dir.isNotEmpty())
        val root=File(dir);val profile=PersonalVoiceCodec.read(File(root,"public-alba-fp32.bin"))
        val a=mutableListOf<Float>();val b=mutableListOf<Float>()
        ExperimentalEngine(root,profile).use { it.generate("Demain, je prendrai le train pour Paris.",CancellationToken(),42,true) { p -> p.forEach { a.add(it) } } }
        QualityEngine(root,profile,sampling=QualitySampling()).use { it.generate("Demain, je prendrai le train pour Paris.",CancellationToken(),42,true) { p -> p.forEach { b.add(it) } } }
        assertArrayEquals(a.toFloatArray(),b.toFloatArray(),0f)
    }
}
