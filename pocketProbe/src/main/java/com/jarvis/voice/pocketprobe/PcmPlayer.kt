package com.jarvis.voice.pocketprobe

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.jarvis.voice.pocket.CancellationToken
import java.util.concurrent.atomic.AtomicReference

/** Worker owns release; UI stops playback immediately without waiting for an ONNX call. */
class PcmPlayer(private val token: CancellationToken) : AutoCloseable {
    private val active = AtomicReference<AudioTrack?>()
    private var submitted = 0L
    var launchNs: Long = 0
        private set
    fun write(pcm: FloatArray) {
        token.check()
        val safe = FloatArray(pcm.size) { pcm[it].coerceIn(-1f, 1f) }
        val track = active.get() ?: AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(24000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT).build())
            .setBufferSizeInBytes(maxOf(24000, AudioTrack.getMinBufferSize(24000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)))
            .setTransferMode(AudioTrack.MODE_STREAM).build().also {
                check(it.state == AudioTrack.STATE_INITIALIZED) { "AudioTrack indisponible" }
                active.set(it)
                token.check()
                it.play(); launchNs = System.nanoTime()
            }
        var offset = 0
        while (offset < pcm.size) {
            token.check()
            val n = track.write(safe, offset, safe.size - offset, AudioTrack.WRITE_NON_BLOCKING)
            check(n >= 0) { "Échec AudioTrack : $n" }
            offset += n; submitted += n
            if (n == 0) Thread.sleep(5)
        }
    }
    fun drain() {
        val track = active.get() ?: return
        val deadline = System.nanoTime() + 25_000_000_000L
        while (Integer.toUnsignedLong(track.playbackHeadPosition) < submitted) {
            token.check()
            check(System.nanoTime() < deadline) { "Délai de lecture audio dépassé" }
            Thread.sleep(10)
        }
    }
    fun stop() { active.get()?.let { runCatching { it.pause(); it.flush() } } }
    override fun close() { active.getAndSet(null)?.let { runCatching { it.stop() }; it.release() } }
}
