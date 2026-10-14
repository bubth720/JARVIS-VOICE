package com.jarvis.voice.pocket

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeTrue
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PocketTests {
    private fun model(): File {
        val path = System.getenv("POCKET_MODEL_DIR").orEmpty()
        assumeTrue("Set POCKET_MODEL_DIR to run pinned-model checks", path.isNotBlank())
        return File(path)
    }
    private fun resource(name: String) = requireNotNull(javaClass.getResourceAsStream("/$name")).bufferedReader().use { it.readText() }

    @Test fun frontendMatchesOfficialFrenchCorpus() {
        val frontend = FrenchFrontend(JSONObject(File(model(), "tokenizer.json").readText()))
        val corpus = JSONArray(resource("french-corpus.json"))
        for (i in 0 until corpus.length()) {
            val sample = corpus.getJSONObject(i)
            val actual = frontend.prepare(sample.getString("raw"))
            assertEquals(sample.getString("raw"), sample.getString("prepared"), actual.text)
            val expected = sample.getJSONArray("tokens")
            assertArrayEquals(sample.getString("raw"), LongArray(expected.length()) { expected.getLong(it) }, actual.tokens)
            assertEquals(sample.getInt("framesAfterEos"), actual.framesAfterEos)
        }
    }
    @Test fun frontendMatchesPublishedExportFixtures() {
        val frontend = FrenchFrontend(JSONObject(File(model(), "tokenizer.json").readText()))
        val samples = JSONObject(resource("frontend-fixtures.json")).getJSONArray("fixtures")
        for (i in 0 until samples.length()) {
            val chunks = samples.getJSONObject(i).getJSONArray("chunks")
            for (j in 0 until chunks.length()) {
                val s = chunks.getJSONObject(j)
                val expected = s.getJSONArray("tokenIds")
                assertArrayEquals(LongArray(expected.length()) { expected.getLong(it) }, frontend.encode(s.getString("text")))
            }
        }
    }
    @Test fun refusesEmptyReservedAndOversizedText() {
        val frontend = FrenchFrontend(JSONObject(File(model(), "tokenizer.json").readText()))
        for (s in listOf("", "    ", "<s>Bonjour", "bonjour ".repeat(80))) {
            assertThrows(IllegalArgumentException::class.java) { frontend.prepare(s) }
        }
    }
    @Test fun pinnedPackHasSixFrenchLayersAndNoCloningEncoder() {
        val bundle = ModelPack.verify(model())
        assertEquals(18, bundle.getJSONArray("flow_lm_state_manifest").length())
        assertFalse(CloningCapability.available)
        assertFalse(ModelPack.files.any { "encoder" in it })
        assertFalse(ModelPack.lock.getBoolean("encoderIncluded"))
    }
    @Test fun voicePresetHasMatchingLayerCachesAndOffsets() {
        val state = VoiceState.read(File(model(), "voices.bin"), "alba")
        assertEquals(18, state.size)
        for (i in 0..5) {
            val prefix = "transformer.layers.$i.self_attn/"
            val cache = state.getValue(prefix + "cache")
            assertEquals(0, cache.dtype)
            assertArrayEquals(longArrayOf(2, 1, 126, 16, 64), cache.shape)
            assertEquals(126L, ByteBuffer.wrap(state.getValue(prefix + "offset").bytes).order(ByteOrder.LITTLE_ENDIAN).long)
        }
        assertThrows(IllegalStateException::class.java) { VoiceState.read(File(model(), "voices.bin"), "private-voice") }
    }
    @Test fun unsafeZipPreservesExistingPackAndDoesNotEscape() {
        val root = Files.createTempDirectory("pocket-pack-test").toFile()
        try {
            File(root, "fr").mkdirs()
            File(root, "fr/sentinel").writeText("existing")
            for (name in listOf("../escape", "/escape", "models/unknown.onnx", "bundle.json")) {
                val out = ByteArrayOutputStream()
                ZipOutputStream(out).use { z -> z.putNextEntry(ZipEntry(name)); z.write(byteArrayOf(1)); z.closeEntry() }
                assertThrows(IllegalArgumentException::class.java) { ModelPack.install(ByteArrayInputStream(out.toByteArray()), root) }
                assertEquals("existing", File(root, "fr/sentinel").readText())
                assertFalse(root.listFiles().orEmpty().any { it.name.startsWith("staging-") })
            }
        } finally { root.deleteRecursively() }
    }
    @Test fun cancellationIsStickyAndPreventsInference() {
        val cancel = CancellationToken()
        cancel.cancel()
        assertTrue(cancel.isCancelled())
        assertThrows(CancellationException::class.java) { cancel.check() }
    }
    @Test fun metricsSeparateComputeAndPlayback() {
        val m = GenerationMetrics(100.0, 500.0, 1500.0, 2.0, 25, 10, true)
        assertEquals(0.25, m.rtf, 0.0)
        assertEquals(4.0, m.speed, 0.0)
    }
    @Test fun nativeRequestCancellationFromAnotherThreadAndRecovery() {
        PocketEngine(model(), 1).use { engine ->
            val executor = Executors.newSingleThreadExecutor()
            val cancel = CancellationToken()
            try {
                val running = executor.submit {
                    engine.generate("Cette phrase française doit être interrompue pendant son calcul.", cancel) { }
                }
                Thread.sleep(100)
                val before = System.nanoTime()
                cancel.cancel()
                val failure = assertThrows(ExecutionException::class.java) { running.get(10, TimeUnit.SECONDS) }
                assertTrue(failure.cause is CancellationException || failure.cause is ai.onnxruntime.OrtException)
                assertTrue("Cancellation took more than 5 seconds", System.nanoTime() - before < 5_000_000_000L)
                var recovered = false
                engine.generate("Le calcul reprend normalement.", CancellationToken()) { recovered = true }
                assertTrue(recovered)
            } finally { executor.shutdownNow() }
        }
    }
    @Test fun installRealPackAndRejectCorruptedReplacement() {
        val source = model()
        val root = Files.createTempDirectory("pocket-install-test").toFile()
        val zip = File(root, "test.zip")
        fun packageFiles(corrupt: Boolean) {
            ZipOutputStream(zip.outputStream()).use { z ->
                for (name in ModelPack.files) {
                    z.putNextEntry(ZipEntry(name))
                    if (corrupt && name == "bundle.json") {
                        val b = File(source, name).readBytes(); b[0] = 'X'.code.toByte(); z.write(b)
                    } else File(source, name).inputStream().use { it.copyTo(z) }
                    z.closeEntry()
                }
            }
        }
        try {
            packageFiles(false)
            val installed = ModelPack.install(zip.inputStream(), root)
            assertEquals("fr", ModelPack.verify(installed).getString("language"))
            val original = File(installed, "bundle.json").readText()
            packageFiles(true)
            assertThrows(IllegalArgumentException::class.java) { ModelPack.install(zip.inputStream(), root) }
            assertEquals(original, File(installed, "bundle.json").readText())
            ModelPack.verify(installed)
        } finally { root.deleteRecursively() }
    }
    @Test fun realFrenchInferenceAndCancellation() {
        val root = model()
        val out = System.getenv("POCKET_SMOKE_DIR").orEmpty().takeIf { it.isNotBlank() }?.let { File(it).apply { mkdirs() } }
        PocketEngine(root, 2).use { engine ->
            val corpus = listOf("Bonjour, je suis JARVIS. Cette voix fonctionne sur ce téléphone.", "Oui.", "Le café est prêt, vous pouvez arrêter la lecture.")
            for ((i, text) in corpus.withIndex()) {
                val pcm = ByteArrayOutputStream()
                var nonSilent = false
                val metrics = engine.generate(text, CancellationToken(), 42L + i) { samples ->
                    if (samples.any { kotlin.math.abs(it) > 0.01f }) nonSilent = true
                    val b = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                    samples.forEach { b.putShort((it.coerceIn(-1f, 1f) * 32767f).toInt().toShort()) }
                    pcm.write(b.array())
                }
                assertTrue("No audible-level signal", nonSilent)
                assertTrue(metrics.eosReached)
                assertTrue(metrics.audioSeconds in 0.3..20.0)
                if (out != null) {
                    writeWav(File(out, "french-$i.wav"), pcm.toByteArray())
                    File(out, "french-$i.metrics.json").writeText(JSONObject(mapOf("modelLoadMs" to engine.modelLoadMs,
                        "firstPcmMs" to metrics.firstPcmMs, "computeMs" to metrics.computeMs, "audioSeconds" to metrics.audioSeconds,
                        "rtf" to metrics.rtf, "frames" to metrics.frames, "physicalAndroidDevice" to false)).toString(2))
                }
                println("JVM French case $i: ${metrics.audioSeconds}s audio, ${metrics.firstPcmMs}ms first PCM, RTF=${metrics.rtf}")
            }
            val cancelled = CancellationToken()
            var emitted = false
            assertThrows(CancellationException::class.java) {
                engine.generate("Ceci est une phrase pour tester l'arrêt du moteur vocal.", cancelled) { emitted = true; cancelled.cancel() }
            }
            assertTrue(emitted)
            var recovered = false
            engine.generate("Le moteur fonctionne après l'arrêt.", CancellationToken()) { recovered = true }
            assertTrue(recovered)
        }
    }

    private fun writeWav(file: File, pcm: ByteArray) {
        val b = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + pcm.size).put("WAVEfmt ".toByteArray()).putInt(16)
            .putShort(1).putShort(1).putInt(24000).putInt(48000).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(pcm.size)
        file.outputStream().use { it.write(b.array()); it.write(pcm) }
    }
}
