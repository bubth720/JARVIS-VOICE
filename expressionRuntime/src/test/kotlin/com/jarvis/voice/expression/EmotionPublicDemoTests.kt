package com.jarvis.voice.expression
import com.jarvis.voice.pocket.CancellationToken
import org.json.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.roundToInt
class EmotionPublicDemoTests {
    @Test fun fivePinnedPublicPhrasesProduceRealWaveformsAndExactNeutral() {
        val source=System.getenv("VOICE06_PUBLIC_SOURCE")?.let(::File) ?: File("../docs/mission-voice-06/public-generated")
        assumeTrue("Pinned public neural demo only",source.isDirectory)
        val receipt=JSONObject(File("../docs/mission-voice-05/proofs/PUBLIC-DEMO.json").readText()).getJSONArray("jobs")
        val out=File(System.getenv("VOICE06_DEMO_OUT") ?: "build/public-emotion-demo").apply { mkdirs() };val rows=JSONArray()
        for(index in 0..4) {
            val expected=receipt.getJSONObject(index);val candidate=File(source,"neutral-${index+1}.wav")
            val file=if(candidate.isFile) candidate else File(source,expected.getString("wav"))
            assertEquals(expected.getString("wavSha256"),sha(file.readBytes()))
            val bytes=file.readBytes();assertEquals("RIFF",bytes.copyOfRange(0,4).toString(Charsets.US_ASCII))
            val b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);assertEquals(24000,b.getInt(24));assertEquals(1,b.getShort(22).toInt())
            val pcm=FloatArray((bytes.size-44)/2) { b.getShort(44+it*2)/32767f }
            for(style in listOf(VoiceEmotionIntent.NEUTRAL,VoiceEmotionIntent.JOY,VoiceEmotionIntent.SURPRISE,VoiceEmotionIntent.CALM)) {
                val plan=(VoiceEmotionController().plan(style,2,true) as EmotionPlanning.Ready).plan
                val start=System.nanoTime();val result=BoundedPcmProsody.render(pcm,plan,CancellationToken());val elapsed=(System.nanoTime()-start)/1e6
                assertEquals(pcm.size,result.pcm.size)
                if(style==VoiceEmotionIntent.NEUTRAL) assertEquals(0,result.changedSamples) else assertTrue(result.changedSamples>0)
                val output=File(out,if(style==VoiceEmotionIntent.NEUTRAL) "neutral-${index+1}.wav" else "${style.name.lowercase()}-${index+1}.wav")
                if(style==VoiceEmotionIntent.NEUTRAL) output.writeBytes(bytes) else writeWav(output,result.pcm)
                rows.put(JSONObject().put("phrase",index+1).put("intent",style.name).put("samples",pcm.size).put("changedSamples",result.changedSamples)
                    .put("maxAbsDifference",result.maxAbsDifference).put("inputRms",result.inputRms).put("outputRms",result.outputRms).put("dspMs",elapsed)
                    .put("wav",output.name).put("sha256",sha(output.readBytes())).put("nativeEmotion",false).put("humanValidated",false))
            }
        }
        File(out,"measurements.json").writeText(JSONObject().put("schema","voice06.public-dsp/1").put("rows",rows).put("humanListening",false)
            .put("source","Pinned public Alba neural outputs from VOICE05; not owner B; no model download or new neural engine").toString(2))
    }
    private fun sha(a: ByteArray)=MessageDigest.getInstance("SHA-256").digest(a).joinToString("") { "%02x".format(it) }
    private fun writeWav(f: File,a: FloatArray) {
        val b=ByteBuffer.allocate(44+a.size*2).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray());b.putInt(36+a.size*2);b.put("WAVEfmt ".toByteArray());b.putInt(16);b.putShort(1);b.putShort(1)
        b.putInt(24000);b.putInt(48000);b.putShort(2);b.putShort(16);b.put("data".toByteArray());b.putInt(a.size*2)
        a.forEach { b.putShort((it*32767).roundToInt().toShort()) };f.writeBytes(b.array())
    }
}
