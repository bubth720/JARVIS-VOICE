package com.jarvis.voice.expression
import com.jarvis.voice.identity.*
import com.jarvis.voice.pocket.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.nio.file.Files

class PublicNeutralParityTests {
    @Test fun publicAlbaNeutralAndUnsupportedStyleAreBitIdenticalToUnchanged005gBackend() {
        val pack=System.getenv("POCKET_EXPERIMENTAL_MODEL_DIR").orEmpty();val audio=System.getenv("IDENTITY_PUBLIC_ALBA_WAV").orEmpty()
        assumeTrue("Existing private FP32 pack and licensed public reference required",pack.isNotEmpty() && audio.isNotEmpty())
        val source=File(audio);assertEquals("46264e83cb99115c3d210260e029117566d9c64f20266d10daa78107759ede3e",VoiceIdentityVault.sha(source))
        val root=Files.createTempDirectory("expression-public-parity-").toFile()
        try {
            val c=CancellationToken();val v=VoiceIdentityVault(File(root,"vault"));val id=SyntheticVault.identity(v,"Public Alba CC BY 4.0")
            val r=v.addReference(id,IdentityWav.decode(source,0,5000,c),"WAV",VoiceIdentityVault.sha(source),0,ReferenceLabel.NEUTRE,"public fixture","licensed public",true,true,false,c)
            val pid=IdentityEncoding.official(v,id,r,File(pack),c);val p=v.list().single().profiles.single();val voice=v.binding().resolve(id,pid,c)
            val results=JSONArray()
            for((text,seed,steps) in listOf(Triple("Bonjour à tous.",7L,1),Triple("Demain, je prendrai le train pour Paris.",42L,3))) {
                val sampling=QualitySampling(steps);val chunks=mutableListOf<FloatArray>()
                val baseline=QualityEngine(File(pack),voice,sampling=sampling).use { it.generate(text,c,seed,true,chunks::add) }
                val expected=chunks.flatMap { it.toList() }.toFloatArray()
                val row=JSONObject().put("text",text).put("seed",seed).put("steps",steps).put("samples",expected.size).put("stats",PcmStatistics.measure(expected).summary()).put("baselineComputeMs",baseline.computeMs)
                for(intent in listOf("NEUTRAL","CONVERSATION")) {
                    val plan=ExpressionPolicy.plan(CoreVoiceRequestV1(text,id,expressionIntent=intent),ExpressionVoiceBinding.from(p),sampling,seed)
                    val actual=ExpressionRenderer.render(File(pack),voice,ExpressionRenderer.prepare(File(pack),plan),c)
                    assertArrayEquals(expected,actual.pcm,0f);row.put(intent,JSONObject().put("bitIdentical",true).put("maxAbsError",0).put("wallMs",actual.wallMs))
                }
                results.put(row)
            }
            val multi="Bonjour à tous.\nNous sommes prêts.";val sampling=QualitySampling(3)
            val pplan=ExpressionPolicy.plan(CoreVoiceRequestV1(multi,id,expressionIntent="CONVERSATION",pauseMs=250,allowSentencePauses=true),ExpressionVoiceBinding.from(p),sampling,42)
            val planned=ExpressionRenderer.prepare(File(pack),pplan);val expectedParts=mutableListOf<FloatArray>()
            for((index,segment) in planned.segments.withIndex()) {
                if(index>0) expectedParts.add(FloatArray(6000))
                QualityEngine(File(pack),voice,sampling=sampling).use { engine -> engine.generate(segment,c,42,true,expectedParts::add) }
            }
            val expected=expectedParts.flatMap { it.toList() }.toFloatArray();val paused=ExpressionRenderer.render(File(pack),voice,planned,c)
            assertArrayEquals(expected,paused.pcm,0f)
            results.put(JSONObject().put("test","explicit-two-sentence-pause").put("pauseMs",250).put("seed",42).put("steps",3).put("samples",paused.pcm.size).put("bitIdenticalToWholeSentenceConcatenation",true).put("stats",PcmStatistics.measure(paused.pcm).summary()).put("wallMs",paused.wallMs))
            val report=JSONObject().put("personalDataUsed",false).put("publicReference","Kyutai Alba CC BY 4.0, pinned SHA256").put("modelRebuilt",false).put("results",results).put("perceptualImprovementClaimed",false)
            System.getenv("EXPRESSION_VALIDATION_OUT")?.let { File(it).apply { parentFile.mkdirs();writeText(report.toString(2)+"\n") } }
        } finally { root.deleteRecursively() }
    }
}
