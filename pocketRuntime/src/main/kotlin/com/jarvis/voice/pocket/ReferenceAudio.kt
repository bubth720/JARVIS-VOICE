package com.jarvis.voice.pocket

import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import org.json.JSONObject
import kotlin.math.*

data class ReferenceInfo(val durationMs: Long, val sampleRate: Int, val channels: Int) {
    fun validate() {
        require(durationMs in 5000..180000) { "MP3 attendu : 5 secondes à 3 minutes" }
        require(sampleRate in 8000..192000 && channels in 1..2) { "Format audio non pris en charge" }
    }
}
data class AudioQuality(val seconds: Double, val rmsDb: Double, val clippedFraction: Double, val quietFraction: Double) {
    val warning get() = when {
        clippedFraction > 0.01 -> "Écrêtage important : choisir un autre extrait."
        rmsDb < -40 -> "Signal très faible : choisir un extrait plus audible."
        quietFraction > 0.6 -> "Beaucoup de silence : choisir un extrait parlé."
        else -> "Niveau exploitable ; vérifier à l'écoute bruit, musique et voix multiples."
    }
}
object ReferenceAudio {
    fun mono(interleaved: FloatArray, channels: Int): FloatArray {
        require(channels in 1..2 && interleaved.size % channels == 0)
        require(interleaved.all { it.isFinite() })
        return FloatArray(interleaved.size / channels) { i ->
            var sum = 0f; for (c in 0 until channels) sum += interleaved[i * channels + c]
            sum / channels
        }
    }
    /** 64-tap windowed sinc, anti-alias filtering before decimation. No peak normalization. */
    fun resample24k(input: FloatArray, rate: Int, cancelled: () -> Boolean = { false }): FloatArray {
        require(rate in 8000..192000 && input.isNotEmpty() && input.all { it.isFinite() })
        require(input.size <= rate * 17) { "Extrait trop long" }
        if (cancelled()) throw java.util.concurrent.CancellationException()
        if (rate == 24000) return input.copyOf()
        val cutoff = min(1.0, 24000.0 / rate) * 0.94
        val length = (input.size.toLong() * 24000 / rate).toInt()
        return FloatArray(length) { i ->
            if (i % 2048 == 0 && cancelled()) throw java.util.concurrent.CancellationException()
            val pos = i.toDouble() * rate / 24000.0; val base = floor(pos).toInt()
            var value = 0.0; var weight = 0.0
            for (k in base - 31..base + 32) {
                val d = pos - k
                val sinc = if (abs(d) < 1e-12) cutoff else sin(PI * cutoff * d) / (PI * d)
                val w = sinc * (0.5 + 0.5 * cos(PI * d / 32)).coerceAtLeast(0.0)
                if (k in input.indices) { value += input[k] * w; weight += w }
            }
            check(weight > 0 && weight.isFinite()) { "Invalid resampling kernel" }
            (value / weight).toFloat().also { check(it.isFinite()) { "Invalid resampled PCM" } }
        }
    }
    fun quality(pcm: FloatArray): AudioQuality {
        require(pcm.isNotEmpty() && pcm.all { it.isFinite() })
        val rms = sqrt(pcm.sumOf { it.toDouble() * it } / pcm.size)
        return AudioQuality(pcm.size / 24000.0, 20 * log10(max(rms, 1e-12)),
            pcm.count { abs(it) >= 0.999f }.toDouble() / pcm.size,
            pcm.count { abs(it) < 0.005f }.toDouble() / pcm.size)
    }
    fun writeWav(file: File, pcm: FloatArray, cancelled: () -> Boolean = { false }) {
        require(pcm.isNotEmpty() && pcm.size <= 360000 && pcm.all { it.isFinite() })
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()); header.putInt(36 + pcm.size * 2); header.put("WAVEfmt ".toByteArray())
        header.putInt(16); header.putShort(1); header.putShort(1); header.putInt(24000)
        header.putInt(48000); header.putShort(2); header.putShort(16)
        header.put("data".toByteArray()); header.putInt(pcm.size * 2)
        file.outputStream().use { out ->
            out.write(header.array())
            val bytes = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            pcm.forEachIndexed { i, sample ->
                if (i % 2048 == 0 && cancelled()) throw java.util.concurrent.CancellationException()
                bytes.putShort((sample.coerceIn(-1f, 1f) * 32767).roundToInt().toShort())
            }
            out.write(bytes.array())
        }
    }
}

/** Single private reference; atomic manifest replacement keeps the previous import on failure. */
class ReferenceRepository(private val dir: File) {
    init { dir.mkdirs() }
    private val manifest get() = File(dir, "reference.json")
    fun current(): Pair<File, ReferenceInfo>? {
        if (!manifest.exists()) return null
        val json = JSONObject(manifest.readText())
        require(json.getInt("schema") == 1)
        val id = json.getString("id"); require(id.matches(Regex("[a-f0-9-]{36}")))
        val file = File(dir, "$id.mp3"); require(file.isFile && file.length() in 1..33554432)
        val info = ReferenceInfo(json.getLong("durationMs"), json.getInt("sampleRate"), json.getInt("channels")); info.validate()
        return file to info
    }
    fun import(input: InputStream, cancelled: () -> Boolean, inspect: (File) -> ReferenceInfo): ReferenceInfo {
        val file = File(dir, "${UUID.randomUUID()}.mp3")
        try {
            input.use { source -> file.outputStream().use { out ->
                val bytes = ByteArray(32768); var total = 0L
                while (true) {
                    if (cancelled()) throw java.util.concurrent.CancellationException()
                    val count = source.read(bytes); if (count < 0) break
                    total += count; require(total <= 33554432) { "MP3 limité à 32 Mio" }; out.write(bytes, 0, count)
                }
            } }
            require(file.length() > 0) { "Fichier vide" }
            val info = inspect(file); info.validate()
            val previous = current()?.first
            val json = JSONObject().put("schema", 1).put("id", file.nameWithoutExtension)
                .put("durationMs", info.durationMs).put("sampleRate", info.sampleRate).put("channels", info.channels)
            val temp = File(dir, "reference.pending"); temp.writeText(json.toString())
            if (cancelled()) { temp.delete(); throw java.util.concurrent.CancellationException() }
            require(temp.renameTo(manifest)) { "Impossible d'enregistrer la référence" }
            previous?.delete(); File(dir, "excerpt.wav").delete()
            return info
        } catch (t: Throwable) { file.delete(); File(dir, "reference.pending").delete(); throw t }
    }
    fun saveExcerpt(pcm: FloatArray, cancelled: () -> Boolean, stage: (String) -> Unit = {}) {
        val temp = File(dir, "excerpt.pending")
        try {
            stage("MP10")
            ReferenceAudio.writeWav(temp, pcm, cancelled)
            require(temp.length() == 44L + pcm.size * 2)
            if (cancelled()) throw java.util.concurrent.CancellationException()
            stage("MP11")
            check(temp.renameTo(excerpt))
        } finally { temp.delete() }
    }
    fun delete() { dir.listFiles()?.forEach { it.delete() } }
    val excerpt get() = File(dir, "excerpt.wav")
}
