package com.jarvis.voice.pocketprobe

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.jarvis.voice.pocket.CancellationToken
import java.util.concurrent.atomic.AtomicReference

/** Playback thread owns writes and release; UI may pause/flush immediately. */
class PcmPlayer(private val token: CancellationToken) : AutoCloseable {
    private val active = AtomicReference<AudioTrack?>()
    private var submitted = 0L
    private var playing = false
    var launchNs = 0L; private set
    var firstFrameNs = 0L; private set
    var zeroWrites = 0; private set
    var maxWriteGapMs = 0.0; private set
    private var lastWriteNs = 0L
    private var observedUnderruns = 0
    val underrunObservations = mutableListOf<String>()
    val underruns get() = active.get()?.underrunCount ?: 0
    val bufferFrames get() = active.get()?.bufferSizeInFrames ?: 0
    fun write(pcm: FloatArray) {
        token.check()
        val track = active.get() ?: AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(24000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT).build())
            .setBufferSizeInBytes(maxOf(192000, AudioTrack.getMinBufferSize(24000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)))
            .setTransferMode(AudioTrack.MODE_STREAM).build().also {
                check(it.state == AudioTrack.STATE_INITIALIZED) { "AudioTrack indisponible" }
                active.set(it); token.check()
            }
        // PCM is already owned by the queue; clamp in place rather than allocate a second block.
        for (i in pcm.indices) pcm[i] = pcm[i].coerceIn(-1f, 1f)
        var offset = 0
        while (offset < pcm.size) {
            token.check()
            val n = track.write(pcm, offset, pcm.size - offset, AudioTrack.WRITE_NON_BLOCKING)
            check(n >= 0) { "Échec AudioTrack : $n" }
            offset += n; submitted += n
            val now = System.nanoTime()
            if (n > 0) {
                if (lastWriteNs != 0L) maxWriteGapMs = maxOf(maxWriteGapMs, (now - lastWriteNs) / 1e6)
                lastWriteNs = now
            }
            // Preload one second before starting; for shorter phrases drain() starts after all data is queued.
            if (!playing && submitted - Integer.toUnsignedLong(track.playbackHeadPosition) >= 24000) start(track)
            observe(track)
            if (n == 0) { zeroWrites++; Thread.sleep(3) }
        }
    }
    @Synchronized private fun start(track: AudioTrack) { token.check(); track.play(); playing = true; if (launchNs == 0L) launchNs = System.nanoTime() }
    private fun observe(track: AudioTrack) {
        val head = Integer.toUnsignedLong(track.playbackHeadPosition)
        if (firstFrameNs == 0L && head != 0L) firstFrameNs = System.nanoTime()
        val count = track.underrunCount
        if (count > observedUnderruns) {
            // Counts are preserved, including terminal events; never suppress a failed check.
            underrunObservations.add("count=$count frames=$head submitted=$submitted")
            observedUnderruns = count
        }
    }
    @Synchronized fun rebuffer() { active.get()?.pause(); playing = false }
    fun drain() {
        val track = active.get() ?: return
        if (!playing) start(track)
        val deadline = System.nanoTime() + 25_000_000_000L
        while (Integer.toUnsignedLong(track.playbackHeadPosition) < submitted) {
            token.check(); observe(track)
            check(System.nanoTime() < deadline) { "Délai de lecture audio dépassé" }
            Thread.sleep(5)
        }
        observe(track)
    }
    @Synchronized fun stop() { active.get()?.let { runCatching { it.pause(); it.flush() } } }
    override fun close() { active.getAndSet(null)?.let { runCatching { it.stop() }; it.release() } }
}
