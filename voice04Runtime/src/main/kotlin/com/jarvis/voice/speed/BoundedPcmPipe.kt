package com.jarvis.voice.speed
import com.jarvis.voice.pocket.CancellationToken

/** Fixed-size private ring. No dropped frames, silence injection, unbounded executor or queue. */
class BoundedPcmPipe(val capacity: Int=96000) : AutoCloseable {
    private val lock=Object()
    private val data=FloatArray(capacity)
    private var read=0;private var write=0;private var count=0
    private var ended=false;private var aborted=false
    var peak=0;private set
    init { require(capacity in 24000..96000) }
    fun offer(pcm: FloatArray,cancel: CancellationToken) {
        require(pcm.isNotEmpty() && pcm.all { it.isFinite() })
        synchronized(lock) {
            check(!ended);var at=0
            while(at<pcm.size) {
                cancel.check();check(!aborted) { "STREAM_ABORTED" }
                if(count==capacity) { lock.wait(10);continue }
                val size=minOf(pcm.size-at,capacity-count,capacity-write)
                pcm.copyInto(data,write,at,at+size);write=(write+size)%capacity;at+=size;count+=size
                peak=maxOf(peak,count);lock.notifyAll()
            }
        }
    }
    fun take(max: Int,cancel: CancellationToken,health: ()->Unit={}): FloatArray? = synchronized(lock) {
        require(max in 1..capacity)
        while(count==0 && !ended && !aborted) { cancel.check();health();lock.wait(10) }
        cancel.check();check(!aborted) { "STREAM_ABORTED" }
        if(count==0) return@synchronized null
        val size=minOf(max,count);val out=FloatArray(size)
        for(i in 0 until size) { out[i]=data[read];data[read]=0f;read=(read+1)%capacity }
        count-=size;lock.notifyAll();out
    }
    fun finish()=synchronized(lock) { check(!aborted);ended=true;lock.notifyAll() }
    override fun close()=synchronized(lock) { aborted=true;data.fill(0f);count=0;lock.notifyAll() }
}
