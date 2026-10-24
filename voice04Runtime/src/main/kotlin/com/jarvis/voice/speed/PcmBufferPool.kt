package com.jarvis.voice.speed

/** Two exact-size private RAM slots, max 576000 floats. No native resource or profile retention. */
class PcmBufferPool : AutoCloseable {
    private val free=mutableMapOf<Int,FloatArray>()
    private val active=mutableSetOf<Int>()
    private var closed=false
    var arrayCreations=0;private set
    var borrows=0;private set
    @Synchronized fun borrow(samples: Int): Lease {
        require(samples in setOf(480000,96000))
        check(!closed && samples !in active) { "POOL_BUSY_OR_QUARANTINED" }
        val data=free.remove(samples) ?: FloatArray(samples).also { arrayCreations++ }
        active.add(samples);borrows++
        return Lease(this,data)
    }
    @Synchronized private fun release(data: FloatArray) {
        check(active.remove(data.size));data.fill(0f)
        if(!closed) free[data.size]=data
    }
    @Synchronized override fun close() { closed=true;free.values.forEach { it.fill(0f) };free.clear() }
    class Lease internal constructor(private val pool: PcmBufferPool,private val data: FloatArray): AutoCloseable {
        private var released=false
        /** Borrowed for synchronous local use only; never retain this reference past close. */
        @get:Synchronized val buffer get(): FloatArray { check(!released);return data }
        @Synchronized override fun close() { if(!released) { released=true;pool.release(data) } }
    }
}

/** One complete job; writes into a leased 20s buffer and presents only its populated prefix. */
class PooledPcmCapture(pool: PcmBufferPool) : AutoCloseable {
    private val lease=pool.borrow(480000)
    var samples=0;private set
    fun append(pcm: FloatArray) {
        require(pcm.isNotEmpty() && pcm.all { it.isFinite() } && pcm.size<=480000-samples) { "JOB_PCM_LIMIT_OR_INVALID" }
        pcm.copyInto(lease.buffer,samples);samples+=pcm.size
    }
    fun <T> present(eos: Boolean,action: (FloatArray,Int)->T): T {
        require(eos && samples>0) { "UNRELIABLE_EOS" };return action(lease.buffer,samples)
    }
    override fun close()=lease.close()
}
