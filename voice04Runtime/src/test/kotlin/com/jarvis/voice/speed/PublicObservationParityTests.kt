package com.jarvis.voice.speed
import com.jarvis.voice.pocket.*
import com.jarvis.voice.identity.*
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Test
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.file.Files

class PublicObservationParityTests {
    @Test fun observedEngineKeepsExactOfficialPcmAndMeasuresStages() {
        val pack=System.getenv("VOICE04_PACK").orEmpty();val audio=System.getenv("VOICE04_PUBLIC_WAV").orEmpty()
        assumeTrue("Explicit existing pack/public pinned reference required",pack.isNotBlank() && audio.isNotBlank())
        val c=CancellationToken();val source=File(audio)
        assertEquals("46264e83cb99115c3d210260e029117566d9c64f20266d10daa78107759ede3e",VoiceIdentityVault.sha(source))
        val tmp=Files.createTempDirectory("voice04-public-").toFile()
        try {
            val vault=VoiceIdentityVault(File(tmp,"vault"));val id=vault.create("Public Alba","CC BY 4.0 public fixture",true)
            val rid=vault.addReference(id,IdentityWav.decode(source,0,5000,c),"WAV",VoiceIdentityVault.sha(source),0,ReferenceLabel.NEUTRE,"public","CC BY 4.0",true,true,false,c)
            val pid=IdentityEncoding.official(vault,id,rid,File(pack),c);val voice=vault.binding().resolve(id,pid,c)
            val rows=JSONArray()
            for(steps in listOf(1,3,5)) {
                val sampling=QualitySampling(steps=steps);val text="Bonjour à vous."
                val ref=mutableListOf<Float>();QualityEngine(File(pack),voice,loadCancel=c,sampling=sampling).use { it.generate(text,c,42,true) { p -> p.forEach(ref::add) } }
                val actual=mutableListOf<Float>();val trace=SpeedTrace()
                ObservedQualityEngine(File(pack),voice,loadCancel=c,sampling=sampling,trace=trace).use { it.generate(text,c,42,true) { p -> p.forEach(actual::add) } }
                assertArrayEquals(ref.toFloatArray(),actual.toFloatArray(),0f)
                assertTrue(trace.has(SpeedPoint.FIRST_PCM));assertTrue(trace.has(SpeedPoint.CLOSE_RETURNED));assertFalse(trace.has(SpeedPoint.PRESENTATION_ESTIMATED))
                rows.put(JSONObject().put("steps",steps).put("seed",42).put("samples",actual.size).put("bitIdentical",true).put("trace",trace.json()))
            }
            System.getenv("VOICE04_PARITY_OUT")?.let { File(it).writeText(JSONObject().put("source","Linux CPU public Alba, not Xiaomi").put("rows",rows).toString(2)) }
        } finally { tmp.deleteRecursively() }
    }
}
