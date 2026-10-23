package com.jarvis.voice.sdk

import java.util.ArrayDeque

/** Private RAM only, no partial playback and no silence/drop/repetition to conceal underruns. */
class PcmFrameQueue(private val maxSamples: Int = 480_000) : AutoCloseable {
    private val frames = ArrayDeque<FloatArray>()
    var samples = 0; private set
    var peakSamples = 0; private set
    private var closed = false
    init { require(maxSamples in 1..480_000) }
    @Synchronized fun offer(pcm: FloatArray) {
        check(!closed)
        require(pcm.isNotEmpty() && pcm.all { it.isFinite() })
        if (pcm.size > maxSamples - samples) throw SpeechRejected(SpeechRefusal.LIMIT_EXCEEDED)
        frames.addLast(pcm.copyOf()); samples += pcm.size; peakSamples = maxOf(peakSamples, samples)
    }
    @Synchronized fun takeComplete(): FloatArray {
        check(!closed && samples > 0)
        val result = FloatArray(samples); var offset = 0
        while (frames.isNotEmpty()) {
            val frame = frames.removeFirst(); frame.copyInto(result, offset); offset += frame.size; frame.fill(0f)
        }
        samples = 0
        return result
    }
    @Synchronized override fun close() {
        frames.forEach { it.fill(0f) }; frames.clear(); samples = 0; closed = true
    }
}
