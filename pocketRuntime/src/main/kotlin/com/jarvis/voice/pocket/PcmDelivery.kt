package com.jarvis.voice.pocket

import java.util.ArrayDeque
import java.util.concurrent.CancellationException

/** Bounded, independent PCM consumer. Never inserts silence, repeats samples or drops blocks. */
class PcmDelivery(
    private val cancelled: () -> Boolean,
    private val adaptive: Boolean = false,
    private val sink: (FloatArray) -> Unit,
    private val onRebuffer: () -> Unit = {},
) : AutoCloseable {
    private val lock = Object()
    private val queue = ArrayDeque<FloatArray>()
    private var buffered = 0
    private var total = 0
    private var finished = false
    @Volatile private var closed = false
    private var error: Throwable? = null
    private val started = System.nanoTime()
    @Volatile var rebufferCount = 0; private set
    @Volatile var peakSamples = 0; private set
    @Volatile var firstDeliveryNs = 0L; private set
    private val consumer = Thread({ consume() }, "pocket-pcm-playback").apply { start() }
    fun offer(pcm: FloatArray) = synchronized(lock) {
        checkOpen()
        require(pcm.isNotEmpty() && pcm.all { it.isFinite() })
        require(total + pcm.size <= 480_000) { "Limite audio de 20 secondes dépassée" }
        queue.addLast(pcm); buffered += pcm.size; total += pcm.size
        peakSamples = maxOf(peakSamples, buffered); lock.notifyAll()
    }
    fun finish() { synchronized(lock) { checkOpen(); finished = true; lock.notifyAll() } }
    fun await() {
        while (consumer.isAlive) { checkCancelled(); consumer.join(20) }
        error?.let { throw it }; checkCancelled()
    }
    private fun checkCancelled() { if (cancelled() || closed) throw CancellationException("Lecture annulée") }
    private fun checkOpen() { checkCancelled(); error?.let { throw it }; check(!finished) }
    private fun ready(): Boolean {
        if (finished) return true
        // Experimental streaming only when production is demonstrably faster than playback.
        val rtf = (System.nanoTime() - started) / 1e9 / (total / 24000.0)
        return adaptive && buffered >= 48_000 && rtf <= 0.75
    }
    private fun consume() {
        try {
            synchronized(lock) { while (!ready()) { checkCancelled(); lock.wait(20) } }
            while (true) {
                val next = synchronized(lock) {
                    checkCancelled()
                    if (queue.isEmpty() && !finished) {
                        rebufferCount++; onRebuffer()
                        while (!ready()) { checkCancelled(); lock.wait(20) }
                    }
                    if (queue.isEmpty() && finished) null else queue.removeFirst().also { buffered -= it.size }
                } ?: break
                checkCancelled()
                if (firstDeliveryNs == 0L) firstDeliveryNs = System.nanoTime()
                sink(next)
            }
        } catch (t: Throwable) { synchronized(lock) { error = t; lock.notifyAll() } }
    }
    override fun close() {
        synchronized(lock) { closed = true; queue.clear(); buffered = 0; lock.notifyAll() }
        consumer.interrupt(); consumer.join(1000)
    }
}
