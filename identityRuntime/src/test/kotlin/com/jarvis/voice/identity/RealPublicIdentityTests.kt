package com.jarvis.voice.identity

import com.jarvis.voice.pocket.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.json.JSONObject
import java.io.File
import java.nio.file.Files

class RealPublicIdentityTests {
    @Test fun twoContiguousPublicAlbaReferencesReallyEncodeAndGenerateDistinctDeterministicConditionings() {
        val pack=System.getenv("POCKET_EXPERIMENTAL_MODEL_DIR").orEmpty();val audio=System.getenv("IDENTITY_PUBLIC_ALBA_WAV").orEmpty()
        assumeTrue("Private existing pack and authorized public Alba fixture required",pack.isNotBlank() && audio.isNotBlank())
        val source=File(audio);assertEquals("46264e83cb99115c3d210260e029117566d9c64f20266d10daa78107759ede3e",VoiceIdentityVault.sha(source))
        val root=Files.createTempDirectory("identity-public-cycle-").toFile()
        try {
            val v=VoiceIdentityVault(File(root,"vault"));val id=v.create("Alba public CC BY 4.0","Public licensed fixture, no personal recording",true)
            val c=CancellationToken();val refs=(0L..1L).map { index ->
                val pcm=IdentityWav.decode(source,index*4000,5000,c)
                v.addReference(id,pcm,"WAV",VoiceIdentityVault.sha(source),index*4000,ReferenceLabel.NEUTRE,"public test","public licensed fixture",true,true,true,c,sourceDurationMs=IdentityWav.inspect(source).durationMs)
            }
            val ids=refs.map { IdentityEncoding.official(v,id,it,File(pack),c) };val profiles=v.list().single().profiles
            assertNotEquals(profiles[0].conditioningSha256,profiles[1].conditioningSha256)
            val phrase="Demain, je prendrai le train pour Paris.";val sampling=QualitySampling(steps=3)
            fun generate(pid: String): FloatArray {
                val voice=v.binding().resolve(id,pid,c);val chunks=mutableListOf<FloatArray>()
                QualityEngine(File(pack),voice,sampling=sampling).use { engine -> engine.generate(phrase,c,42,true) { chunks.add(it) } }
                val result=FloatArray(chunks.sumOf { it.size });var offset=0;chunks.forEach { it.copyInto(result,offset);offset+=it.size };return result
            }
            val a=generate(ids[0]);val b=generate(ids[1]);val againA=generate(ids[0]);assertArrayEquals(a,againA,0f);assertFalse(a.contentEquals(b))
            assertTrue(a.all { it.isFinite() } && b.all { it.isFinite() });val store=IdentityComparisonStore(File(root,"ab"))
            store.save("A",id,profiles[0],phrase,42,sampling,a,c);store.save("B",id,profiles[1],phrase,42,sampling,b,c)
            assertArrayEquals(a,store.load("A",v,c),0f);assertArrayEquals(b,store.load("B",v,c),0f)
            val report=JSONObject().put("publicFixture","Alba CC BY 4.0, pinned SHA256").put("personalAudioUsed",false).put("profiles",2)
                .put("steps",3).put("seed",42).put("distinctConditionings",true).put("distinctGeneratedPcm",true).put("repeatedABitIdentical",true)
                .put("samplesA",a.size).put("samplesB",b.size).put("pcmA",PcmStatistics.measure(a).summary()).put("pcmB",PcmStatistics.measure(b).summary())
            System.getenv("IDENTITY_VALIDATION_OUT")?.takeIf { it.isNotBlank() }?.let { File(it).apply { parentFile.mkdirs();writeText(report.toString(2)+"\n") } }
        } finally { root.deleteRecursively() }
    }
}
