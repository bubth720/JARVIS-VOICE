package com.jarvis.voice.fusion02
import java.util.UUID
fun main() {
    val proxy=LocalProxy()
    val key=AudioKey(UUID.randomUUID().toString(),UUID.randomUUID().toString(),1)
    val job=proxy.prepare(key,2,ObservationOrigin.JVM_SIMULATION)
    proxy.execute(job,SegmentRenderer { _,stop,onPcm -> stop.check();onPcm(240);RenderedSegment(FloatArray(240),true) },
        SegmentPlayback { pcm,stop,write,playing -> stop.check();write(pcm.size);playing();PresentedSegment(pcm.size,true) })
    println("JVM_SIMULATION ONLY; CORE_CONNECTED=false; HUMAN_LISTENING=false; no native engine/audio device")
    job.timeline.snapshot().forEach { println("${it.sequence}: ${it.stage}, segment=${it.segment}") }
}
