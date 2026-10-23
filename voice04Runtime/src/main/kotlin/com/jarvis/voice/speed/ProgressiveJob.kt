package com.jarvis.voice.speed
import com.jarvis.voice.pocket.CancellationToken
import java.util.concurrent.atomic.AtomicReference

interface ProgressiveOutput : AutoCloseable {
    /** Write the full preload before play; throw before any sound if unavailable. */
    fun start(preload: FloatArray)
    fun write(pcm: FloatArray)
    fun health()
    fun finish(totalSamples: Int)
    fun stop()
}
enum class ProgressiveMode { UNDECIDED, DEFERRED, STREAM }
class ProgressiveFailure(val afterStart: Boolean,cause: Throwable): IllegalStateException(if(afterStart) "INCOMPLETE_STREAM" else "STREAM_FAILED",cause)

/** Experimental job <=20s. Gate measures producer throughput before the first sound, not a Xiaomi promise. */
class ProgressiveJob(private val cancel: CancellationToken,private val output: ()->ProgressiveOutput,
    val preload: Int=48000,private val clock: ()->Long=System::nanoTime) : AutoCloseable {
    private val ring=BoundedPcmPipe()
    private val pcm=FloatArray(480000)
    private var count=0
    private val began=clock();private var previous=began;private var maxGapNs=0L
    private val failure=AtomicReference<Throwable?>()
    private val active=AtomicReference<ProgressiveOutput?>()
    private var consumer: Thread?=null
    @Volatile var mode=ProgressiveMode.UNDECIDED;private set
    @Volatile var started=false;private set
    @Volatile var completed=false;private set
    @Volatile var closeUnknown=false;private set
    val peakQueuedSamples get()=ring.peak
    init { require(preload in setOf(24000,48000)) }
    fun offer(block: FloatArray) {
        cancel.check();failure.get()?.let { throw ProgressiveFailure(started,it) }
        require(block.isNotEmpty() && block.all { it.isFinite() }) { "INVALID_PCM" }
        require(block.size<=480000-count) { "JOB_PCM_LIMIT" }
        block.copyInto(pcm,count);count+=block.size
        val now=clock();maxGapNs=maxOf(maxGapNs,now-previous);previous=now
        if(mode==ProgressiveMode.UNDECIDED && count>=preload) {
            val audioNs=count*1000000000L/24000
            val canStream=count<=ring.capacity && now-began<=audioNs*65/100 && maxGapNs<=preload*1000000000L/24000/2
            if(!canStream) mode=ProgressiveMode.DEFERRED
            else {
                mode=ProgressiveMode.STREAM
                ring.offer(pcm.copyOf(count),cancel)
                consumer=Thread({ consume() },"voice04-audio").apply { start() }
            }
        } else if(mode==ProgressiveMode.STREAM) ring.offer(block,cancel)
    }
    private fun consume() {
        var sink: ProgressiveOutput?=null
        try {
            sink=output();active.set(sink)
            val head=requireNotNull(ring.take(preload,cancel));try { sink.start(head);started=true } finally { head.fill(0f) }
            while(true) {
                val block=ring.take(1920,cancel,sink::health) ?: break
                try { sink.write(block) } finally { block.fill(0f) }
            }
            sink.finish(count);cancel.check();completed=true
        } catch(t: Throwable) { failure.compareAndSet(null,t);cancel.cancel();ring.close() }
        finally {
            try { sink?.close() } catch(t: Throwable) { closeUnknown=true;failure.compareAndSet(null,t);completed=false;cancel.cancel() }
            active.set(null)
        }
    }
    /** Call only after reliable EOS and native engine closure. Never falls back after stream selection. */
    fun finish(eos: Boolean): FloatArray {
        require(eos && count>0) { "UNRELIABLE_EOS" }
        if(mode==ProgressiveMode.UNDECIDED) mode=ProgressiveMode.DEFERRED
        if(mode==ProgressiveMode.STREAM) {
            ring.finish();awaitConsumer()
            failure.get()?.let { throw ProgressiveFailure(started,it) }
            cancel.check();check(completed) { "STREAM_NOT_COMPLETED" }
        }
        cancel.check();return pcm.copyOf(count)
    }
    private fun awaitConsumer() {
        val thread=consumer ?: return
        val until=System.nanoTime()+26000000000L
        while(thread.isAlive && System.nanoTime()<until) {
            if(cancel.isCancelled()) { ring.close();active.get()?.stop() }
            thread.join(20)
        }
        if(thread.isAlive) { closeUnknown=true;stop();throw IllegalStateException("AUDIO_CLOSE_UNKNOWN") }
    }
    fun stop() { cancel.cancel();ring.close();active.get()?.stop() }
    override fun close() {
        ring.close()
        try { if(consumer?.isAlive==true) { stop();awaitConsumer() } } finally { pcm.fill(0f) }
    }
}
