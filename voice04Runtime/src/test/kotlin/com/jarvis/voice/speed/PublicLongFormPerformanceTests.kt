package com.jarvis.voice.speed
import com.jarvis.voice.pocket.*
import com.jarvis.voice.identity.*
import org.json.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.lang.management.ManagementFactory

class PublicLongFormPerformanceTests {
    @Test fun sixNaturalFrenchJobsUseOneThreadCloseEachEngineAndRemainBounded() {
        val pack=System.getenv("VOICE04_PACK").orEmpty();val wav=System.getenv("VOICE04_PUBLIC_WAV").orEmpty()
        assumeTrue("Explicit existing pack and licensed public reference",pack.isNotBlank() && wav.isNotBlank())
        val text=listOf(
            "Demain matin, nous prendrons le train pour rejoindre Paris et retrouver nos amis près du grand jardin.",
            "Nous marcherons tranquillement le long de la rivière avant de choisir une petite table pour partager le déjeuner.",
            "Pendant le voyage, je vous raconterai cette histoire étonnante que notre voisin nous a confiée dimanche dernier.",
            "Si le soleil reste avec nous, nous pourrons visiter le quartier et prendre quelques photographies de ses rues anciennes.",
            "Avant de repartir, nous achèterons du pain et quelques fruits pour préparer ensemble un repas simple à la maison.",
            "Le soir, chacun pourra raconter son meilleur souvenir de cette journée et nous déciderons de notre prochaine promenade."
        ).joinToString(" ")
        val frontend=FrenchFrontend(JSONObject(File(pack,"tokenizer.json").readText()))
        val plan=LongFormPlanner.prepare(LongFormSpeechRequestV1(text),frontend);assertEquals(6,plan.segments.size)
        val c=CancellationToken();val source=File(wav)
        assertEquals("46264e83cb99115c3d210260e029117566d9c64f20266d10daa78107759ede3e",VoiceIdentityVault.sha(source))
        val temp=Files.createTempDirectory("voice04-public-long-").toFile();val rows=JSONArray();val peak=AtomicLong()
        val sampler=Executors.newSingleThreadScheduledExecutor()
        val cpu=ManagementFactory.getOperatingSystemMXBean() as com.sun.management.OperatingSystemMXBean
        val cpuStart=cpu.processCpuTime;val began=System.nanoTime()
        sampler.scheduleWithFixedDelay({
            runCatching { val pss=File("/proc/self/smaps_rollup").useLines { lines -> lines.firstOrNull { it.startsWith("Pss:") }?.trim()?.split(Regex("\\s+"))?.get(1)?.toLong() };if(pss!=null) peak.accumulateAndGet(pss,::maxOf) }
        },0,250,TimeUnit.MILLISECONDS)
        try {
            val v=VoiceIdentityVault(File(temp,"v"));val id=v.create("Alba publique","CC BY 4.0",true)
            val rid=v.addReference(id,IdentityWav.decode(source,0,5000,c),"WAV",VoiceIdentityVault.sha(source),0,ReferenceLabel.NEUTRE,"public test","CC BY 4.0",true,true,false,c)
            val pid=IdentityEncoding.official(v,id,rid,File(pack),c);val voice=v.binding().resolve(id,pid,c)
            val cursor=LongFormCursor(plan)
            cursor.run(c) { i,segment ->
                val trace=SpeedTrace();trace.tokens(segment.tokens)
                var job: ProgressiveJob?=null
                try {
                    val metrics=ObservedQualityEngine(File(pack),voice,loadCancel=c,sampling=QualitySampling(3),trace=trace).use { engine ->
                        job=ProgressiveJob(c,{ object: ProgressiveOutput {
                            private var samples=0
                            override fun start(preload: FloatArray) { samples+=preload.size }
                            override fun write(pcm: FloatArray) { samples+=pcm.size }
                            override fun health() {}
                            override fun finish(totalSamples: Int) { check(samples==totalSamples) }
                            override fun stop() {}
                            override fun close() {}
                        } })
                        engine.generate(segment.text,c,42,true) { requireNotNull(job).offer(it) }
                    }
                    val pcm=requireNotNull(job).finish(metrics.eosReached)
                    assertTrue(pcm.size in 1..480000);assertTrue(job!!.mode in setOf(ProgressiveMode.DEFERRED,ProgressiveMode.STREAM))
                    trace.pcm(pcm);trace.finish("COMPLETED")
                    val bytes=ByteBuffer.allocate(pcm.size*4).order(ByteOrder.LITTLE_ENDIAN);pcm.forEach(bytes::putFloat)
                    val sha=MessageDigest.getInstance("SHA-256").digest(bytes.array()).joinToString("") { "%02x".format(it) }
                    rows.put(JSONObject().put("job",i+1).put("tokens",metrics.tokens).put("samples",pcm.size).put("eos",metrics.eosReached)
                        .put("sha256PcmFloat32",sha).put("mode",job!!.mode.name).put("output","synthetic sink only, no audio").put("trace",trace.json()))
                    pcm.fill(0f)
                } finally { job?.close() }
            }
            assertEquals(6,cursor.completedJobs)
            System.getenv("VOICE04_LONG_OUT")?.let { File(it).writeText(JSONObject().put("schema","voice04.public-linux/1").put("publicText",text)
                .put("tokens",plan.tokens).put("jobs",rows).put("source","Linux CPU, public Alba CC BY4.0; not Xiaomi")
                .put("elapsedIncludingPublicEncodingMs",(System.nanoTime()-began)/1e6).put("cpuProcessIncludingPublicEncodingMs",(cpu.processCpuTime-cpuStart)/1e6)
                .put("pssPeakSampledMiB",if(peak.get()>0) peak.get()/1024.0 else JSONObject.NULL).put("pssPeriodMs",250)
                .put("thermal",JSONObject.NULL).put("batteryEnergy",JSONObject.NULL).put("audioTrack",JSONObject.NULL).put("humanListening",false).toString(2)) }
        } finally { sampler.shutdownNow();temp.deleteRecursively() }
    }
}
