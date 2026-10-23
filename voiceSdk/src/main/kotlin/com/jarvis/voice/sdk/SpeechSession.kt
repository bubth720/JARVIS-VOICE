package com.jarvis.voice.sdk

import com.jarvis.voice.pocket.CancellationToken
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

/** Exclusive SDK dispatch, NOT native admission: host retains its existing resource authority.
 * An uncertain engine error blocks subsequent dispatches through this repository. No reset API.
 */
class SpeechSessionRepository(private val engine: LocalSpeechEnginePort) {
    private val running = AtomicBoolean(false)
    @Volatile private var engineUncertain = false
    /** Host reads this in its existing admission; no native resource authority is transferred. */
    val isQuarantined: Boolean get() = engineUncertain
    fun open(output: OutputAudioPort, observer: (SpeechState) -> Unit = {}): SpeechSession =
        SpeechSession(UUID.randomUUID().toString(), engine, output, observer, this)
    @Synchronized internal fun enter(): SpeechRefusal? = when {
        engineUncertain -> SpeechRefusal.ENGINE_FAILED
        !running.compareAndSet(false, true) -> SpeechRefusal.BUSY
        else -> null
    }
    @Synchronized internal fun leave() { running.set(false) }
    @Synchronized internal fun quarantine() { engineUncertain = true }
}

/** Synchronous calls, no added worker/model. RELEASED describes this session, not Android memory. */
class SpeechSession internal constructor(val sessionId: String, private val engine: LocalSpeechEnginePort,
    private val output: OutputAudioPort, private val observer: (SpeechState) -> Unit,
    private val repository: SpeechSessionRepository) : AutoCloseable {
    private val lock = Any()
    @Volatile var state = SpeechState.IDLE; private set
    private var active = false
    private var closed = false
    private var token: CancellationToken? = null
    private val usedRequests = mutableSetOf<String>()
    private fun transition(next: SpeechState) = synchronized(lock) {
        // A concurrent stop remains visible until the synchronous operation actually returns.
        if (active && state == SpeechState.STOPPING && next != SpeechState.RELEASED && next != SpeechState.IDLE)
            return@synchronized
        state = next; runCatching { observer(next) }; Unit
    }
    private fun closeOutput() { try { output.close() } catch (_: Throwable) { repository.quarantine() } }
    private fun checkSuppressedCleanup(error: Throwable) {
        // Kotlin use() preserves a primary cancellation/refusal and suppresses a failing close().
        if (error.suppressed.isNotEmpty()) repository.quarantine()
    }

    fun speak(queue: SpeechQueue, cancel: CancellationToken = CancellationToken()): SpeechOutcome {
        synchronized(lock) {
            if (closed) return SpeechOutcome.Refused(SpeechRefusal.RELEASED)
            if (active) return SpeechOutcome.Refused(SpeechRefusal.BUSY)
            if (usedRequests.size + queue.requests.size > 128) return SpeechOutcome.Refused(SpeechRefusal.LIMIT_EXCEEDED)
            if (queue.requests.any { it.requestId in usedRequests }) return SpeechOutcome.Refused(SpeechRefusal.DUPLICATE_REQUEST)
            repository.enter()?.let { return SpeechOutcome.Refused(it) }
            // Requests remain consumed after cancellation/error: no automatic retry of an ambiguous operation.
            usedRequests.addAll(queue.requests.map { it.requestId })
            active = true; token = cancel
        }
        val started = System.nanoTime()
        var generated = 0; var presented = 0; var completed = 0; var peak = 0; var underruns = 0
        try {
            for (request in queue.requests) {
                cancel.check(); transition(SpeechState.PREPARING)
                val rendered = try { engine.render(request, cancel) }
                catch (e: SpeechRejected) { checkSuppressedCleanup(e); throw e }
                catch (e: CancellationException) { checkSuppressedCleanup(e); throw e }
                catch (_: Throwable) { repository.quarantine(); throw SpeechRejected(SpeechRefusal.ENGINE_FAILED) }
                try {
                    cancel.check()
                    require(rendered.eosReached) { "EOS requis" }
                    if (rendered.pcm.size > 480_000 - generated) throw SpeechRejected(SpeechRefusal.LIMIT_EXCEEDED)
                    generated += rendered.pcm.size
                    PcmFrameQueue().use { frames ->
                        frames.offer(rendered.pcm); peak = maxOf(peak, frames.peakSamples)
                        val pcm = frames.takeComplete()
                        try {
                            cancel.check(); transition(SpeechState.PLAYING)
                            output.focusController.begin()
                            val receipt = output.play(pcm, cancel); cancel.check(); output.focusController.check()
                            if (output.focusController.state != AudioFocusController.State.GRANTED)
                                throw SpeechRejected(SpeechRefusal.NON_VERIFIED)
                            if (receipt.presentedSamples != pcm.size) throw SpeechRejected(SpeechRefusal.AUDIO_UNAVAILABLE)
                            presented += receipt.presentedSamples; underruns += receipt.underruns; completed++
                        } finally { pcm.fill(0f) }
                    }
                } finally { rendered.pcm.fill(0f) }
            }
            return SpeechOutcome.Completed(PlaybackMetrics(completed, generated, presented, peak, underruns,
                (System.nanoTime() - started) / 1_000_000))
        } catch (e: CancellationException) {
            checkSuppressedCleanup(e)
            return SpeechOutcome.Refused(SpeechRefusal.CANCELLED)
        } catch (e: SpeechRejected) {
            checkSuppressedCleanup(e)
            return SpeechOutcome.Refused(e.reason)
        } catch (_: Throwable) {
            repository.quarantine()
            return SpeechOutcome.Refused(SpeechRefusal.AUDIO_UNAVAILABLE)
        } finally {
            synchronized(lock) {
                active = false; token = null
                if (closed) { closeOutput(); transition(SpeechState.RELEASED) }
                else transition(SpeechState.IDLE)
                repository.leave()
            }
        }
    }
    fun requestStop() = synchronized(lock) {
        if (active) {
            transition(SpeechState.STOPPING)
            runCatching { token?.cancel() }.onFailure { repository.quarantine() }
            runCatching { output.requestStop() }.onFailure { repository.quarantine() }
        }
    }
    override fun close() = synchronized(lock) {
        if (closed) return@synchronized
        closed = true
        if (active) requestStop()
        else { closeOutput(); transition(SpeechState.RELEASED) }
    }
}
