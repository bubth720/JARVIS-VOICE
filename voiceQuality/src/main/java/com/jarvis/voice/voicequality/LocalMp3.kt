package com.jarvis.voice.voicequality

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
        } catch (t: Throwable) { ex.release(); throw ReferenceFailure("MP02", t.javaClass.simpleName) }
    }
    fun inspect(file: File): ReferenceInfo = referenceStep("MP02") {
        val (ex, index) = extractor(file)
        try {
            val fmt = ex.getTrackFormat(index)
            ReferenceInfo(fmt.getLong(MediaFormat.KEY_DURATION) / 1000,
                fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE), fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)).also { it.validate() }
        } finally { ex.release() }
    }
    fun decode(file: File, startMs: Long, durationMs: Long, cancel: CancellationToken): FloatArray {
        cancel.check()
        var stage = "MP03"
        var kept = 0
        val began = System.nanoTime()
        val info = inspect(file)
        referenceStep("MP01") {
            require(durationMs in 5000..15000 && startMs in 0..175000 && startMs + durationMs <= info.durationMs)
        }
        val (ex, index) = extractor(file)
        var codec: MediaCodec? = null
        try {
            val fmt = ex.getTrackFormat(index)
            val decoder = MediaCodec.createDecoderByType("audio/mpeg"); codec = decoder
            decoder.configure(fmt, null, null, 0); decoder.start()
            // Decode from the beginning so MP3 bit reservoirs and timestamp offsets remain consistent.
            // Only the selected 5–15 seconds are retained in memory.
            var rate = info.sampleRate; var channels = info.channels; var encoding = AudioFormat.ENCODING_PCM_16BIT
            val selected = ArrayList<FloatArray>()
            var inputDone = false; var outputDone = false
            val bufferInfo = MediaCodec.BufferInfo()
            val deadline = System.nanoTime() + 120_000_000_000L
            while (!outputDone) {
                cancel.check(); check(System.nanoTime() < deadline) { "Décodage MP3 trop long" }
                stage = "MP04"
                if (!inputDone) {
                    val slot = decoder.dequeueInputBuffer(10000)
                    if (slot >= 0) {
                        val buf = requireNotNull(decoder.getInputBuffer(slot)); buf.clear()
                        val n = ex.readSampleData(buf, 0)
                        if (n < 0) { decoder.queueInputBuffer(slot, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                        else { decoder.queueInputBuffer(slot, 0, n, ex.sampleTime, 0); ex.advance() }
                    }
                }
                stage = "MP05"
                val slot = decoder.dequeueOutputBuffer(bufferInfo, 10000)
                if (slot == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val out = decoder.outputFormat
                    rate = out.getInteger(MediaFormat.KEY_SAMPLE_RATE); channels = out.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    encoding = if (out.containsKey(MediaFormat.KEY_PCM_ENCODING)) out.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                    require(rate == info.sampleRate && channels in 1..2)
                    require(encoding == AudioFormat.ENCODING_PCM_16BIT || encoding == AudioFormat.ENCODING_PCM_FLOAT)
                } else if (slot >= 0) {
                    try {
                        if (bufferInfo.size == 0) {
                            outputDone = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            continue
                        }
                        val bytes = requireNotNull(decoder.getOutputBuffer(slot)).duplicate().order(ByteOrder.LITTLE_ENDIAN)
                        require(bufferInfo.offset >= 0 && bufferInfo.size <= bytes.capacity() - bufferInfo.offset)
                        bytes.clear(); bytes.limit(bufferInfo.offset + bufferInfo.size); bytes.position(bufferInfo.offset)
                        val width = if (encoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
                        require(bufferInfo.size % (width * channels) == 0)
                        stage = "MP06"
                        val frames = bufferInfo.size / width / channels
                        val first = maxOf(0, ((startMs * 1000 - bufferInfo.presentationTimeUs) * rate / 1000000).toInt())
                        val end = minOf(first + maxOf(0, (rate.toLong() * durationMs / 1000).toInt() - kept), frames, (((startMs + durationMs) * 1000 - bufferInfo.presentationTimeUs) * rate / 1000000).toInt())
                        if (end > first) {
                            bytes.position(bufferInfo.offset + first * channels * width)
                            stage = "MP07"
                            val mono = FloatArray(end - first) {
                                var sum = 0f
                                repeat(channels) { sum += if (width == 4) bytes.float else bytes.short / 32768f }
                                sum / channels
                            }
                            require(mono.all { it.isFinite() })
                            kept += mono.size; require(kept <= rate * 16) { "Extrait trop long" }; selected.add(mono)
                        }
                        outputDone = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0 || bufferInfo.presentationTimeUs >= (startMs + durationMs) * 1000
                    } finally { decoder.releaseOutputBuffer(slot, false) }
                }
            }
            stage = "MP06"
            require(kept >= rate * (durationMs - 100) / 1000) { "MP3 tronqué ou extrait incomplet" }
            val mono = FloatArray(kept); var offset = 0
            for (block in selected) { block.copyInto(mono, offset); offset += block.size }
            stage = "MP08"
            cancel.check(); return ReferenceAudio.resample24k(mono, rate, cancel::isCancelled).also {
                require(it.isNotEmpty() && it.all { sample -> sample.isFinite() })
            }
        } catch (t: Throwable) {
            if (cancel.isCancelled()) cancel.check()
            throw ReferenceFailure(stage, t.javaClass.simpleName, info.sampleRate.toLong() * durationMs / 1000, kept.toLong(), (System.nanoTime() - began) / 1_000_000)
        } finally { codec?.let { runCatching { it.stop() }; it.release() }; ex.release() }
    }
}
