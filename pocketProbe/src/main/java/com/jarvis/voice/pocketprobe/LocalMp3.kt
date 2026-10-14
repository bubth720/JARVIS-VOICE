package com.jarvis.voice.pocketprobe

import android.media.*
import com.jarvis.voice.pocket.*
import java.io.File
import java.nio.ByteOrder

/** MediaExtractor/MediaCodec execute on device only. No URI, audio or file name is logged. */
object LocalMp3 {
    private fun extractor(file: File): Pair<MediaExtractor, Int> {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(file.absolutePath)
            val index = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME) == "audio/mpeg" }
                ?: error("Fichier MP3 audio/mpeg valide requis")
            ex.selectTrack(index); return ex to index
        } catch (t: Throwable) { ex.release(); throw IllegalArgumentException("MP3 illisible ou corrompu") }
    }
    fun inspect(file: File): ReferenceInfo {
        val (ex, index) = extractor(file)
        try {
            val fmt = ex.getTrackFormat(index)
            return ReferenceInfo(fmt.getLong(MediaFormat.KEY_DURATION) / 1000,
                fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE), fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)).also { it.validate() }
        } finally { ex.release() }
    }
    fun decode(file: File, startMs: Long, durationMs: Long, cancel: CancellationToken): FloatArray {
        val info = inspect(file)
        require(durationMs in 5000..15000 && startMs >= 0 && startMs + durationMs <= info.durationMs) { "Choisir un extrait de 5 à 15 s à l'intérieur du MP3" }
        val (ex, index) = extractor(file)
        var codec: MediaCodec? = null
        try {
            val fmt = ex.getTrackFormat(index)
            val decoder = MediaCodec.createDecoderByType("audio/mpeg"); codec = decoder
            decoder.configure(fmt, null, null, 0); decoder.start()
            // Decode from the beginning so MP3 bit reservoirs and timestamp offsets remain consistent.
            // Only the selected 5–15 seconds are retained in memory.
            var rate = info.sampleRate; var channels = info.channels; var encoding = AudioFormat.ENCODING_PCM_16BIT
            val selected = ArrayList<FloatArray>(); var kept = 0
            var inputDone = false; var outputDone = false
            val bufferInfo = MediaCodec.BufferInfo()
            val deadline = System.nanoTime() + 120_000_000_000L
            while (!outputDone) {
                cancel.check(); check(System.nanoTime() < deadline) { "Décodage MP3 trop long" }
                if (!inputDone) {
                    val slot = decoder.dequeueInputBuffer(10000)
                    if (slot >= 0) {
                        val buf = requireNotNull(decoder.getInputBuffer(slot)); buf.clear()
                        val n = ex.readSampleData(buf, 0)
                        if (n < 0) { decoder.queueInputBuffer(slot, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                        else { decoder.queueInputBuffer(slot, 0, n, ex.sampleTime, 0); ex.advance() }
                    }
                }
                val slot = decoder.dequeueOutputBuffer(bufferInfo, 10000)
                if (slot == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val out = decoder.outputFormat
                    rate = out.getInteger(MediaFormat.KEY_SAMPLE_RATE); channels = out.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    encoding = if (out.containsKey(MediaFormat.KEY_PCM_ENCODING)) out.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                    require(rate == info.sampleRate && channels in 1..2)
                    require(encoding == AudioFormat.ENCODING_PCM_16BIT || encoding == AudioFormat.ENCODING_PCM_FLOAT)
                } else if (slot >= 0) {
                    try {
                        val bytes = requireNotNull(decoder.getOutputBuffer(slot)).duplicate().order(ByteOrder.LITTLE_ENDIAN)
                        bytes.position(bufferInfo.offset); bytes.limit(bufferInfo.offset + bufferInfo.size)
                        val width = if (encoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
                        val frames = bufferInfo.size / width / channels
                        val first = maxOf(0, ((startMs * 1000 - bufferInfo.presentationTimeUs) * rate / 1000000).toInt())
                        val end = minOf(frames, (((startMs + durationMs) * 1000 - bufferInfo.presentationTimeUs) * rate / 1000000).toInt())
                        if (end > first) {
                            bytes.position(bufferInfo.offset + first * channels * width)
                            val mono = FloatArray(end - first) {
                                var sum = 0f
                                repeat(channels) { sum += if (width == 4) bytes.float else bytes.short / 32768f }
                                sum / channels
                            }
                            kept += mono.size; require(kept <= rate * 16) { "Extrait trop long" }; selected.add(mono)
                        }
                        outputDone = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0 || bufferInfo.presentationTimeUs >= (startMs + durationMs) * 1000
                    } finally { decoder.releaseOutputBuffer(slot, false) }
                }
            }
            require(kept >= rate * (durationMs - 100) / 1000) { "MP3 tronqué ou extrait incomplet" }
            val mono = FloatArray(kept); var offset = 0
            for (block in selected) { block.copyInto(mono, offset); offset += block.size }
            cancel.check(); return ReferenceAudio.resample24k(mono, rate, cancel::isCancelled)
        } catch (t: Throwable) {
            if (cancel.isCancelled()) cancel.check()
            throw IllegalArgumentException("Décodage local refusé : audio invalide, tronqué ou format non pris en charge", t)
        } finally { codec?.let { runCatching { it.stop() }; it.release() }; ex.release() }
    }
}
