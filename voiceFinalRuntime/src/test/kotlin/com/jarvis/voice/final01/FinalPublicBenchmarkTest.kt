package com.jarvis.voice.final01
import com.jarvis.voice.pocket.*
import com.jarvis.voice.identity.*
import com.jarvis.voice.speed.*
import org.json.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.file.Files
class FinalPublicBenchmarkTest {
    @Test fun officialPublicCorpusParityAndRepeatedWholeGeneration() {
        val pack=System.getenv("VOICE_FINAL_PACK").orEmpty();val wav=System.getenv("VOICE_FINAL_PUBLIC_WAV").orEmpty();val output=System.getenv("VOICE_FINAL_OUT").orEmpty()
        assumeTrue("Existing official pack + authorized public fixture; no download",pack.isNotEmpty() && wav.isNotEmpty() && output.isNotEmpty())
        val c=CancellationToken();val source=File(wav)
        assertEquals("46264e83cb99115c3d210260e029117566d9c64f20266d10daa78107759ede3e",VoiceIdentityVault.sha(source))
        val temp=Files.createTempDirectory("final-public").toFile();val rows=JSONArray();val out=File(output).apply { mkdirs() }
        try {
            val v=VoiceIdentityVault(File(temp,"v"));val id=v.create("Alba publique","CC BY4.0",true)
            val rid=v.addReference(id,IdentityWav.decode(source,0,5000,c),"WAV",VoiceIdentityVault.sha(source),0,ReferenceLabel.NEUTRE,"public","CC BY4.0",true,true,false,c)
            val encodeStart=System.nanoTime();val pid=IdentityEncoding.official(v,id,rid,File(pack),c);val encodeMs=(System.nanoTime()-encodeStart)/1e6
            val voice=v.binding().resolve(id,pid,c);val frontend=FrenchFrontend(JSONObject(File(pack,"tokenizer.json").readText()))
            val corpus=listOf("Demain, je prendrai le train pour Paris.","Le 24 octobre, le billet coûte 32,50 euros.")
            val longText=List(200) { "M. Dupont annonce : « Le train part à 12 h 30. »\nLe prix est de 32,50 euros ; demain aussi !\n" }.joinToString("")
            val longStart=System.nanoTime();val longPlan=DocumentPlanner.prepare(longText,frontend,c);val planMs=(System.nanoTime()-longStart)/1e6
            assertEquals(longText,longPlan.segments.joinToString("") { it.raw });assertTrue(longPlan.segments.size>6);assertTrue(longPlan.segments.all { it.tokens<=50 })
            for((phrase,text) in corpus.withIndex()) repeat(5) { repetition ->
                val results=mutableMapOf<String,FloatArray>()
                val order=if(repetition%2==0) listOf("REFERENCE_05","INSTRUMENTED_FINAL01") else listOf("INSTRUMENTED_FINAL01","REFERENCE_05")
                order.forEach { side ->
                    val trace=SpeedTrace();val detail=StageProfiler();val begin=System.nanoTime();val cpuBegin=ProcessHandle.current().info().totalCpuDuration().orElse(null)
                    val blocks=mutableListOf<FloatArray>()
                    val engine: ExperimentalSpeechBackend=if(side=="REFERENCE_05") ObservedQualityEngine(File(pack),voice,loadCancel=c,sampling=QualitySampling(3),trace=trace)
                        else FinalObservedQualityEngine(File(pack),voice,loadCancel=c,sampling=QualitySampling(3),trace=trace,profiler=detail,frontendOverride=frontend)
                    val loadMs=engine.modelLoadMs
                    val metrics=engine.use { it.generate(text,c,42,true) { a->blocks.add(a.copyOf()) } };trace.finish("COMPLETED")
                    val pcm=FloatArray(blocks.sumOf { it.size });var offset=0;blocks.forEach { it.copyInto(pcm,offset);offset+=it.size;it.fill(0f) }
                    val cpuEnd=ProcessHandle.current().info().totalCpuDuration().orElse(null)
                    require(pcm.size<=480000);results[side]=pcm
                    val pss=runCatching { File("/proc/self/smaps_rollup").readLines().single { it.startsWith("Pss:") }.trim().split(Regex("\\s+"))[1].toLong() }.getOrNull()
                    rows.put(JSONObject().put("phrase",phrase).put("repeat",repetition).put("side",side).put("elapsedMs",(System.nanoTime()-begin)/1e6)
                        .put("loadMs",loadMs).put("firstPcmMs",metrics.firstPcmMs).put("computeMs",metrics.computeMs).put("audioSeconds",metrics.audioSeconds)
                        .put("rtf",metrics.rtf).put("cpuProcessMs",if(cpuBegin!=null && cpuEnd!=null) (cpuEnd.toNanos()-cpuBegin.toNanos())/1e6 else JSONObject.NULL)
                        .put("pssAfterKiB",pss ?: JSONObject.NULL).put("thermal",JSONObject.NULL).put("energy",JSONObject.NULL).put("firstAudibleMs",JSONObject.NULL)
                        .put("stagesMs",detail.json()).put("trace",trace.json()).put("samples",pcm.size).put("peak",PcmStatistics.measure(pcm).peak))
                }
                assertTrue(results.getValue("REFERENCE_05").map { it.toRawBits() }==results.getValue("INSTRUMENTED_FINAL01").map { it.toRawBits() })
                results.values.forEach { it.fill(0f) }
            }
            val summary=JSONObject()
            for(side in listOf("REFERENCE_05","INSTRUMENTED_FINAL01")) {
                val selected=(0 until rows.length()).map(rows::getJSONObject).filter { it.getString("side")==side }
                summary.put(side,JSONObject().apply { for(key in listOf("elapsedMs","loadMs","firstPcmMs","computeMs","audioSeconds","rtf","cpuProcessMs","pssAfterKiB")) put(key,Quantiles.summarize(selected.filter { !it.isNull(key) }.map { it.getDouble(key) })) })
            }
            File(out,"benchmark.json").writeText(JSONObject().put("schema","voice-final01.public-benchmark/1").put("platform","Linux Cloud CPU; not Android or POCO")
                .put("sourceSha256",VoiceIdentityVault.sha(source)).put("voiceEncodingMs",encodeMs).put("corpus",JSONArray(corpus)).put("rows",rows).put("summary",summary)
                .put("longPlanningMs",planMs).put("longUnits",longText.length).put("longWords",longText.trim().split(Regex("\\s+")).size).put("longSegments",longPlan.segments.size)
                .put("longTextRendered",false).put("bitIdenticalAB",true).put("humanListening",false).put("modelRebuilt",false).toString(2))
        } finally { temp.deleteRecursively() }
    }

    @Test fun nativeCancelAtFirstPcmReturnsCloseWithoutClaimingUnload() {
        val pack=System.getenv("VOICE_FINAL_PACK").orEmpty();val wav=System.getenv("VOICE_FINAL_PUBLIC_WAV").orEmpty();val output=System.getenv("VOICE_FINAL_OUT").orEmpty()
        assumeTrue("Existing official pack and public fixture only",pack.isNotEmpty() && wav.isNotEmpty() && output.isNotEmpty())
        val c=CancellationToken();val source=File(wav);assertEquals("46264e83cb99115c3d210260e029117566d9c64f20266d10daa78107759ede3e",VoiceIdentityVault.sha(source))
        val temp=Files.createTempDirectory("final-cancel-public").toFile()
        try {
            val v=VoiceIdentityVault(File(temp,"v"));val id=v.create("Alba publique","CC BY4.0",true)
            val rid=v.addReference(id,IdentityWav.decode(source,0,5000,c),"WAV",VoiceIdentityVault.sha(source),0,ReferenceLabel.NEUTRE,"public","CC BY4.0",true,true,false,c)
            val pid=IdentityEncoding.official(v,id,rid,File(pack),c);val voice=v.binding().resolve(id,pid,c)
            val trace=SpeedTrace();val token=CancellationToken()
            assertThrows(java.util.concurrent.CancellationException::class.java) {
                FinalObservedQualityEngine(File(pack),voice,loadCancel=token,trace=trace,sampling=QualitySampling(3)).use { engine ->
                    engine.generate("Demain, je prendrai le train pour Paris.",token,42,true) { token.cancel() }
                }
            }
            assertTrue(trace.has(SpeedPoint.FIRST_PCM));assertTrue(trace.has(SpeedPoint.CLOSE_RETURNED));assertFalse(trace.has(SpeedPoint.EOS));trace.finish("CANCELLED")
            File(output).mkdirs();File(output,"native-cancel.json").writeText(JSONObject().put("scope","Linux ONNX cancellation at first PCM; not mid-stall or Xiaomi")
                .put("nativeUnloadVerified",false).put("audioPlayed",false).put("trace",trace.json()).toString(2))
        } finally { temp.deleteRecursively() }
    }
}
