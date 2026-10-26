package com.jarvis.voice.fusion02
import com.jarvis.voice.pocket.*
import com.jarvis.voice.identity.*
import com.jarvis.voice.speed.*
import com.jarvis.voice.final01.*
import org.json.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.file.Files

/** Optional actual Linux ONNX render. No AudioTrack, no private reference, no new model. */
class PublicRenderBenchmarkTest {
    @Test fun publicWholeDocumentsThroughCorrelatedRenderOnlyProxy() {
        val pack=System.getenv("FUSION02_PACK").orEmpty();val wav=System.getenv("FUSION02_PUBLIC_WAV").orEmpty();val output=System.getenv("FUSION02_OUT").orEmpty()
        assumeTrue("Existing FP32 pack and hash-pinned public Alba only",pack.isNotEmpty() && wav.isNotEmpty() && output.isNotEmpty())
        val source=File(wav);assertEquals("46264e83cb99115c3d210260e029117566d9c64f20266d10daa78107759ede3e",VoiceIdentityVault.sha(source))
        val tmp=Files.createTempDirectory("fusion02-public").toFile();val c=CancellationToken()
        try {
            val vault=VoiceIdentityVault(File(tmp,"vault"));val id=vault.create("Alba publique","CC BY4.0",true)
            val rid=vault.addReference(id,IdentityWav.decode(source,0,5000,c),"WAV",VoiceIdentityVault.sha(source),0,ReferenceLabel.NEUTRE,"public","CC BY4.0",true,true,false,c)
            val encodingBegin=System.nanoTime();val pid=IdentityEncoding.official(vault,id,rid,File(pack),c)
            val encodingMs=(System.nanoTime()-encodingBegin)/1e6
            val voice=vault.binding().resolve(id,pid,c)
            val frontend=FrenchFrontend(JSONObject(File(pack,"tokenizer.json").readText()))
            val phrases=listOf(PublicUtterance.TRAIN.text,"Le 24 octobre, le billet coûte 32,50 euros.")
            val corpus=phrases+List(8){phrases[it%2]}.joinToString("\n")
            val rows=JSONArray()
            corpus.forEachIndexed { corpusIndex,text ->
                val plan=DocumentPlanner.prepare(text,frontend,c)
                assertEquals(text,plan.segments.joinToString(""){it.raw});assertTrue(plan.segments.all { it.tokens<=50 })
                if(corpusIndex==2) assertTrue(plan.segments.size>=8)
                repeat(3) { repetition ->
                    val cpuBegin=ProcessHandle.current().info().totalCpuDuration().orElse(null)
                    val key=AudioKey(java.util.UUID.randomUUID().toString(),java.util.UUID.randomUUID().toString(),1)
                    val proxy=LocalProxy();val job=proxy.prepare(key,plan.segments.size,ObservationOrigin.LINUX_NATIVE_RENDER_ONLY)
                    val stages=StageProfiler();var samples=0L;var sumSquares=0.0;var peak=0.0
                    val started=System.nanoTime()
                    proxy.execute(job,SegmentRenderer { index,stop,onPcm ->
                        val blocks=mutableListOf<FloatArray>();val trace=SpeedTrace()
                        val metrics=FinalObservedQualityEngine(File(pack),voice,loadCancel=c,sampling=QualitySampling(3),trace=trace,profiler=stages,frontendOverride=frontend).use { engine ->
                            engine.generate(plan.segments[index].raw,c,42,true) { block -> stop.check();onPcm(block.size);blocks.add(block.copyOf()) }
                        }
                        val pcm=FloatArray(blocks.sumOf { it.size });var offset=0
                        blocks.forEach { block -> block.copyInto(pcm,offset);offset+=block.size;block.fill(0f) }
                        pcm.forEach { samples++;sumSquares+=it.toDouble()*it;peak=maxOf(peak,kotlin.math.abs(it.toDouble())) }
                        RenderedSegment(pcm,metrics.eosReached)
                    })
                    val elapsedMs=(System.nanoTime()-started)/1e6
                    assertEquals(AudioStage.RenderedOnly,job.timeline.stage)
                    assertEquals(plan.segments.size,job.timeline.completed)
                    val events=job.timeline.snapshot()
                    assertFalse(events.any { it.stage in setOf(AudioStage.AudioTrackWrite,AudioStage.PlayingEstimated,AudioStage.Finished) })
                    val cpuEnd=ProcessHandle.current().info().totalCpuDuration().orElse(null)
                    val pss=runCatching { File("/proc/self/smaps_rollup").readLines().single { it.startsWith("Pss:") }.trim().split(Regex("\\s+"))[1].toLong() }.getOrNull()
                    rows.put(JSONObject().put("corpus",corpusIndex).put("repeat",repetition).put("segments",plan.segments.size)
                        .put("firstPcmMs",events.first { it.stage==AudioStage.FirstPCM }.elapsedNs/1e6).put("elapsedMs",elapsedMs)
                        .put("firstWriteMs",JSONObject.NULL).put("firstAudibleMs",JSONObject.NULL).put("audioSeconds",samples/24000.0)
                        .put("rtf",elapsedMs/1000/(samples/24000.0)).put("cpuProcessMs",if(cpuBegin!=null && cpuEnd!=null)(cpuEnd.toNanos()-cpuBegin.toNanos())/1e6 else JSONObject.NULL)
                        .put("pssAfterKiB",pss ?: JSONObject.NULL).put("thermal",JSONObject.NULL).put("energy",JSONObject.NULL)
                        .put("peak",peak).put("rms",kotlin.math.sqrt(sumSquares/samples)).put("stageMs",stages.json())
                        .put("eventStages",JSONArray(events.map { it.stage.name })))
                }
            }
            val summary=JSONArray()
            corpus.indices.forEach { i ->
                val group=(0 until rows.length()).map(rows::getJSONObject).filter { it.getInt("corpus")==i }
                summary.put(JSONObject().put("corpus",i).apply { for(field in listOf("firstPcmMs","elapsedMs","audioSeconds","rtf","cpuProcessMs","pssAfterKiB")) put(field,Quantiles.summarize(group.filter{!it.isNull(field)}.map{it.getDouble(field)})) })
            }
            File(output).mkdirs();File(output,"PUBLIC-RENDER.json").writeText(JSONObject().put("schema","voice.fusion02.public-render/1")
                .put("platform","Linux Cloud x86_64 CPU; no Android audio").put("threads",1).put("steps",3).put("temperature",.3).put("seed",42)
                .put("sourceSha256",VoiceIdentityVault.sha(source)).put("voiceEncodingMs",encodingMs).put("profileResolvedBeforeRuns",true)
                .put("corpus",JSONArray(corpus)).put("rows",rows).put("summary",summary).put("humanListening",false).put("nativeUnloadVerified",false)
                .put("modelsRebuilt",false).put("noLatencyGainClaim",true).toString(2))
        } finally { tmp.deleteRecursively() }
    }
}
