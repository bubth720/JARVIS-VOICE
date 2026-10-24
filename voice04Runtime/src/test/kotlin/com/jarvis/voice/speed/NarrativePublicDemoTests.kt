package com.jarvis.voice.speed
import com.jarvis.voice.pocket.*
import com.jarvis.voice.identity.*
import com.jarvis.voice.expression.*
import org.json.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.file.Files
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt
class NarrativePublicDemoTests {
    @Test fun sixPublicNativeJobsMatchBaselineAndWriteLocalDemonstration() {
        val pack=System.getenv("VOICE05_PACK").orEmpty();val wav=System.getenv("VOICE05_PUBLIC_WAV").orEmpty();val output=System.getenv("VOICE05_DEMO_OUT").orEmpty()
        assumeTrue("Existing model and licensed public fixture only",pack.isNotEmpty() && wav.isNotEmpty() && output.isNotEmpty())
        val c=CancellationToken();val source=File(wav)
        assertEquals("46264e83cb99115c3d210260e029117566d9c64f20266d10daa78107759ede3e",VoiceIdentityVault.sha(source))
        val temp=Files.createTempDirectory("voice05-public-").toFile();val out=File(output).apply { mkdirs() }
        val text=JSONObject(File("../docs/mission-voice-04/proofs/PUBLIC-LONG.json").readText()).getString("publicText")
        try {
            val v=VoiceIdentityVault(File(temp,"v"));val id=v.create("Alba publique","CC BY4.0",true)
            val rid=v.addReference(id,IdentityWav.decode(source,0,5000,c),"WAV",VoiceIdentityVault.sha(source),0,ReferenceLabel.NEUTRE,"public","CC BY4.0",true,true,false,c)
            val pid=IdentityEncoding.official(v,id,rid,File(pack),c);val profile=v.list().single().profiles.single();val voice=v.binding().resolve(id,pid,c)
            val scheduler=FirstAudioScheduler();val frontend=scheduler.frontend(VoiceIdentityVault.sha(File(pack,"tokenizer.json"))) { FrenchFrontend(JSONObject(File(pack,"tokenizer.json").readText())) }
            val plan=SentencePreflight.prepare(LongFormSpeechRequestV1(text),frontend);val rows=JSONArray()
            PcmBufferPool().use { pool ->
                val queue=NarrativeQueue(plan)
                fun process(index: Int,segment: LongFormSegment) {
                    val policy=ExpressionPolicy.plan(CoreVoiceRequestV1(segment.text,id),ExpressionVoiceBinding.from(profile),QualitySampling(3),42)
                    val prosody=ExpressionRenderer.prepare(File(pack),policy)
                    var baseline: FloatArray?=null;var baseMs=0.0;var pooledMs=0.0;var pooled: FloatArray?=null
                    fun reference() { val begin=System.nanoTime();baseline=ExpressionRenderer.render(File(pack),voice,prosody,c).pcm;baseMs=(System.nanoTime()-begin)/1e6 }
                    fun pooled() {
                        val begin=System.nanoTime();val trace=SpeedTrace()
                        PooledPcmCapture(pool).use { capture ->
                            val metrics=ObservedQualityEngine(File(pack),voice,loadCancel=c,sampling=QualitySampling(3),trace=trace).use { it.generate(segment.text,c,42,true,capture::append) }
                            capture.present(metrics.eosReached) { data,count->pooled=data.copyOf(count) }
                        };pooledMs=(System.nanoTime()-begin)/1e6
                    }
                    if(index%2==0) { reference();pooled() } else { pooled();reference() }
                    val a=requireNotNull(baseline);val b=requireNotNull(pooled);assertArrayEquals(a,b,0f)
                    val file=File(out,"job-${index+1}.wav");writeWav(file,b)
                    rows.put(JSONObject().put("job",index+1).put("tokens",segment.tokens).put("baselineMs",baseMs).put("pooledMs",pooledMs)
                        .put("order",if(index%2==0) "AB" else "BA").put("samples",b.size).put("pcmBitIdentical",true).put("wav",file.name).put("wavSha256",VoiceIdentityVault.sha(file)))
                    a.fill(0f);b.fill(0f)
                }
                queue.run(c) { i,segment->process(i,segment);if(i==1) queue.requestPause() }
                assertEquals(2,queue.completedJobs);queue.run(c,true,::process);assertEquals(6,queue.completedJobs)
                File(out,"measurements.json").writeText(JSONObject().put("schema","voice05.public-demo/1").put("tokens",plan.tokens).put("jobs",rows)
                    .put("poolLargeArrayCreations",pool.arrayCreations).put("poolBorrows",pool.borrows).put("source","Linux CPU1, public Alba; not Android/Xiaomi")
                    .put("humanListening",false).put("attribution","Alba public Kyutai tts-voices, CC BY4.0; no personal data or weights").toString(2))
            }
        } finally { temp.deleteRecursively() }
    }
    private fun writeWav(file: File,pcm: FloatArray) {
        val b=ByteBuffer.allocate(44+pcm.size*2).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray());b.putInt(36+pcm.size*2);b.put("WAVEfmt ".toByteArray());b.putInt(16);b.putShort(1);b.putShort(1)
        b.putInt(24000);b.putInt(48000);b.putShort(2);b.putShort(16);b.put("data".toByteArray());b.putInt(pcm.size*2)
        pcm.forEach { b.putShort((it.coerceIn(-1f,1f)*32767).roundToInt().toShort()) };file.writeBytes(b.array())
    }
}
