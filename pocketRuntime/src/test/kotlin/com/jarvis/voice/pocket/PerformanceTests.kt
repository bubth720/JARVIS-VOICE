package com.jarvis.voice.pocket

import org.junit.Test
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

class PerformanceTests {
    @Test fun compareOneAndTwoThreadsStreamingAndWholeSentence() {
        val root = System.getenv("POCKET_MODEL_DIR").orEmpty()
        assumeTrue("Pinned model required", root.isNotEmpty())
        val results = JSONArray()
        for (threads in 1..2) PocketEngine(File(root), threads).use { engine ->
            for (whole in listOf(false, true)) {
                val output = mutableListOf<FloatArray>()
                val m = engine.generate("Bonjour, je suis JARVIS. Cette voix fonctionne sur ce téléphone.", CancellationToken(), wholeSentence = whole) { output.add(it) }
                assertTrue(m.eosReached); assertTrue(m.audioSeconds > 1)
                assertTrue(output.all { block -> block.all { it.isFinite() } })
                if (whole) {
                    assertEquals(1, output.size)
                    val destination = System.getenv("POCKET_SMOKE_DIR").orEmpty()
                    if (destination.isNotEmpty()) ReferenceAudio.writeWav(File(destination, "mission005-whole-$threads.wav"), output.single())
                }
                results.put(JSONObject().put("threads", threads).put("wholeSentence", whole)
                    .put("rtf", m.rtf).put("generationMs", m.computeMs).put("firstPcmMs", m.firstPcmMs)
                    .put("audioSeconds", m.audioSeconds).put("mainMs", m.onnxMainMs).put("flowMs", m.onnxFlowMs)
                    .put("decoderMs", m.onnxDecoderMs).put("maxOnnxMs", m.maxOnnxMs).put("maxPcmGapMs", m.maxPcmGapMs))
            }
        }
        println("PUBLIC_CPU_BENCHMARK $results")
        val dest = System.getenv("POCKET_SMOKE_DIR").orEmpty()
        if (dest.isNotEmpty()) File(dest, "mission005-benchmark.json").writeText(results.toString(2))
    }
}
