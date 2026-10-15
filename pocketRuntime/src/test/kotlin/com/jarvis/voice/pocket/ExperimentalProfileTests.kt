package com.jarvis.voice.pocket

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.concurrent.CancellationException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExperimentalProfileTests {
    @Test fun originalPrivateZipInstallsWithEmbeddedManifest() {
        val path=System.getenv("POCKET_EXPERIMENTAL_ZIP").orEmpty()
        assumeTrue("Private ZIP required; never downloaded by public CI",path.isNotBlank())
        val zip=File(path)
        assertEquals(234932686L,zip.length())
        assertEquals("9436555c6965e3480c06f51fedced0f512a8316e482a7e54df0f4ee83d0a01cd",ExperimentalModelPack.sha256(zip))
        val root=Files.createTempDirectory("original-fp32-import").toFile()
        try {
            val installed=ExperimentalModelPack.install(zip.inputStream(),root) { false }
            assertEquals(ExperimentalModelPack.files,installed.listFiles()!!.map { it.name }.toSet())
            ExperimentalModelPack.verify(installed)
        } finally { root.deleteRecursively() }
    }
    private fun synthetic(value: Float = 0.01f): PersonalVoiceProfile {
        val tensors = linkedMapOf<String, VoiceTensor>()
        for (layer in 0 until 6) {
            val p = "transformer.layers.$layer.self_attn/"
            for ((key, number) in listOf("offset" to 64L, "pad" to 0L)) tensors[p+key] = VoiceTensor(1,longArrayOf(1),ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(number).array())
            val bytes=ByteBuffer.allocate(2*64*16*64*4).order(ByteOrder.LITTLE_ENDIAN)
            repeat(2*64*16*64) { bytes.putFloat(value) }
            tensors[p+"cache"] = VoiceTensor(0,longArrayOf(2,1,64,16,64),bytes.array())
        }
        return PersonalVoiceProfile(ExperimentalModelPack.id,tensors)
    }
    @Test fun profileRoundTripCancellationPreservesOldStateAndDeletionIsComplete() {
        val root=Files.createTempDirectory("private-profile-test").toFile()
        try {
            val repository=PersonalVoiceRepository(root)
            val first=synthetic(); repository.save(first,true,CancellationToken())
            assertEquals(18,repository.load().tensors.size)
            val cancelled=CancellationToken().also { it.cancel() }
            try { repository.save(synthetic(.2f),true,cancelled); fail("Cancelled replacement accepted") } catch(_: CancellationException) { }
            assertArrayEquals(first.tensors.values.last().bytes,repository.load().tensors.values.last().bytes)
            repository.save(synthetic(.3f),true,CancellationToken())
            assertArrayEquals(synthetic(.3f).tensors.values.first { it.dtype==0 }.bytes,repository.load().tensors.values.first { it.dtype==0 }.bytes)
            repository.delete(); assertFalse(repository.exists()); assertEquals(0,root.listFiles()!!.size)
        } finally { root.deleteRecursively() }
    }
    @Test fun profileRejectsWrongPackCorruptionAndMissingConsent() {
        val root=Files.createTempDirectory("profile-integrity-test").toFile()
        try {
            val repo=PersonalVoiceRepository(root)
            try { repo.save(synthetic(),false,CancellationToken()); fail("Consent missing") } catch(_: IllegalArgumentException) { }
            try { PersonalVoiceCodec.validate(synthetic().copy(packId="0".repeat(64))); fail("Wrong model accepted") } catch(_: IllegalArgumentException) { }
            repo.save(synthetic(),true,CancellationToken())
            val data=root.listFiles()!!.first { it.isDirectory }.resolve("state.bin")
            val bytes=data.readBytes(); bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte(); data.writeBytes(bytes)
            try { repo.load(); fail("Corruption accepted") } catch(_: IllegalArgumentException) { }
            val legacy=File(root,"legacy.bin"); legacy.writeBytes("PTVB1".toByteArray()+ByteArray(100))
            try { PersonalVoiceCodec.read(legacy); fail("INT8 preset format accepted") } catch(_: IllegalArgumentException) { }
        } finally { root.deleteRecursively() }
    }
    @Test fun wavParserExtractsExactlyFiveSecondsAndRejectsStereo() {
        val root=Files.createTempDirectory("wav-private-test").toFile()
        try {
            val file=File(root,"reference.wav"); val samples=FloatArray(144000) { (kotlin.math.sin(it*0.01)*.2).toFloat() }
            ReferenceAudio.writeWav(file,samples)
            val selected=PrivateWav.read(file,24000,120000)
            assertEquals(120000,selected.size)
            for(i in selected.indices step 1000) assertEquals(samples[i+24000],selected[i],4e-5f)
            val bytes=file.readBytes(); bytes[22]=2; file.writeBytes(bytes)
            try { PrivateWav.read(file); fail("Stereo accepted") } catch(_: IllegalArgumentException) { }
        } finally { root.deleteRecursively() }
    }
    @Test fun zipTraversalAndInt8InputAreRejectedBeforeActivation() {
        val root=Files.createTempDirectory("pack-private-test").toFile()
        try {
            for(name in listOf("../escape","voices.bin")) {
                val zip=File(root,"bad.zip")
                ZipOutputStream(zip.outputStream()).use { out -> out.putNextEntry(ZipEntry(name)); out.write(byteArrayOf(1)); out.closeEntry() }
                try { ExperimentalModelPack.install(zip.inputStream(),File(root,"models")) { false }; fail("Unsafe/INT8 pack accepted") } catch(_: IllegalArgumentException) { }
                assertFalse(File(root,"escape").exists()); assertFalse(File(root,"models/fp32").exists())
            }
        } finally { root.deleteRecursively() }
    }
    @Test fun realOfflineProfileIsEncodedPersistedSelectedAndUsedByFP32Generator() {
        val model=System.getenv("POCKET_EXPERIMENTAL_MODEL_DIR").orEmpty()
        assumeTrue("Private official FP32 pack required",model.isNotBlank())
        val root=Files.createTempDirectory("real-private-profile").toFile()
        try {
            val begin=System.nanoTime()
            val pcm=FloatArray(120000) { (kotlin.math.sin(it*2*kotlin.math.PI*220/24000)*.1).toFloat() }
            val profile=FP32VoiceEncoder(File(model),CancellationToken()).use { it.create(pcm,CancellationToken()) }
            val encodingMs=(System.nanoTime()-begin)/1e6
            val repo=PersonalVoiceRepository(root); repo.save(profile,true,CancellationToken())
            val selected=repo.load(); assertEquals(18,selected.tensors.size)
            var samples=0; var generated: GenerationMetrics?=null; var loadMs=0.0
            ExperimentalEngine(File(model),selected).use { engine ->
                val result=engine.generate("Bonjour, je suis une synthèse expérimentale.",CancellationToken(),42,wholeSentence=true) { audio -> assertTrue(audio.all { it.isFinite() }); samples+=audio.size }
                generated=result; loadMs=engine.modelLoadMs
                assertTrue(result.eosReached); assertTrue(result.frames<250); assertTrue(samples>0)
            }
            val highWater=File("/proc/self/status").takeIf { it.exists() }?.readLines()?.firstOrNull { it.startsWith("VmHWM:") }?.split(Regex("\\s+"))?.getOrNull(1)?.toLongOrNull()
            println("EXPERIMENTAL_SYNTHETIC_JVM_MEASURES {\"encodingWithLoadMs\":$encodingMs,\"generatorLoadMs\":$loadMs,\"generationMs\":${generated!!.computeMs},\"rtf\":${generated!!.rtf},\"audioSeconds\":${generated!!.audioSeconds},\"jvmProcessHistoricalPeakRssKiB\":${highWater ?: -1},\"deviceValidation\":false}")
            repo.delete(); assertFalse(repo.exists())
        } finally { root.deleteRecursively() }
    }
}
