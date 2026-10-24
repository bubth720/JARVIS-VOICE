package com.jarvis.voice.speed

import org.json.JSONObject

enum class SpeedPoint { PRESSED, ADMITTED, NATIVE_OPEN_STARTED, PACK_VERIFIED, TOKENIZER_READY, TEXT_SESSION_READY, MAIN_SESSION_READY,
    FLOW_SESSION_READY, DECODER_SESSION_READY, FIRST_PCM, FIRST_WRITE, PRESENTATION_ESTIMATED, EOS, PLAYBACK_COMPLETED, CLOSE_RETURNED, FINISHED }

/** One operation, monotonic relative timestamps only. No text, hash, identity, path or audio accepted. */
class SpeedTrace(private val clock: () -> Long = System::nanoTime) {
    private val start=clock()
    private val points=linkedMapOf(SpeedPoint.PRESSED to 0.0)
    private val durations=linkedMapOf<SpeedPoint,Double>()
    private var last=0.0
    private var chunks=0
    private var samples=0L
    private var tokens=0
    private var outcome="PREPARING"
    private var pcmStats: JSONObject?=null
    private var audioCounters: JSONObject?=null
    private var peak=0.0;private var squares=0.0;private var silent=0L;private var clipped=0L
    @Synchronized fun audio(submitted: Long,head: Long,writes: Int,zeroWrites: Int,underruns: Int) {
        require(submitted>=0 && head>=0 && writes>=0 && zeroWrites>=0 && underruns>=0)
        audioCounters=JSONObject().put("submitted",submitted).put("playbackHead",head).put("writes",writes).put("zeroWrites",zeroWrites).put("underruns",underruns)
    }
    @Synchronized fun chunk(pcm: FloatArray) {
        require(pcm.isNotEmpty() && pcm.all { it.isFinite() })
        chunk(pcm.size)
        pcm.forEach { val v=kotlin.math.abs(it.toDouble());peak=maxOf(peak,v);squares+=v*v;if(v<.0001) silent++;if(v>=1) clipped++ }
        pcmStats=JSONObject().put("samples",samples).put("nonFinite",0).put("peak",peak).put("rms",kotlin.math.sqrt(squares/samples))
            .put("silencePercent",silent*100.0/samples).put("clippedPercent",clipped*100.0/samples)
    }
    @Synchronized fun pcm(pcm: FloatArray) {
        val s=com.jarvis.voice.pocket.PcmStatistics.measure(pcm)
        pcmStats=JSONObject().put("samples",s.samples).put("nonFinite",s.nonFinite).put("peak",s.peak).put("rms",s.rms).put("silencePercent",s.silencePercent).put("clippedPercent",s.clippedPercent)
    }
    @Synchronized fun first(point: SpeedPoint) {
        val now=(clock()-start)/1e6
        check(now.isFinite() && now>=last) { "Non-monotonic clock" }
        last=now
        points.putIfAbsent(point,now)
    }
    fun <T> timed(point: SpeedPoint, action: ()->T): T {
        val begin=clock()
        val value=action()
        val elapsed=(clock()-begin)/1e6
        require(elapsed>=0)
        synchronized(this) { durations[point]=(durations[point] ?: 0.0)+elapsed }
        first(point)
        return value
    }
    @Synchronized fun chunk(size: Int) { require(size>0);chunks++;samples+=size }
    @Synchronized fun tokens(count: Int) { require(count in 1..50);tokens+=count }
    @Synchronized fun finish(status: String) {
        require(status in setOf("COMPLETED","CANCELLED","REFUSED","FAILED","INCOMPLETE"))
        outcome=status;first(SpeedPoint.FINISHED)
    }
    @Synchronized fun has(point: SpeedPoint)=points.containsKey(point)
    @Synchronized fun json(): JSONObject = JSONObject().apply {
        put("schema","voice04.speed/1");put("clock","monotonic-relative-ms");put("outcome",outcome)
        put("engine","pocket-french-6l-fp32-observed");put("threads",1)
        put("chunks",chunks);put("samples",samples);put("pcmSeconds",samples/24000.0);put("tokens",tokens)
        put("pcmStats",pcmStats ?: JSONObject.NULL);put("audioCounters",audioCounters ?: JSONObject.NULL)
        put("audibilityHumanVerified",false);put("nativeMemoryReleaseVerified",false)
        put("eventsMs",JSONObject().apply { SpeedPoint.entries.forEach { put(it.name,points[it] ?: JSONObject.NULL) } })
        put("durationsMs",JSONObject().apply { SpeedPoint.entries.forEach { put(it.name,durations[it] ?: JSONObject.NULL) } })
    }
    @Synchronized fun summary()="VOICE-04 $outcome · $tokens tokens · $chunks blocs · ${samples/24000.0} s PCM\n"+
        points.entries.joinToString(" ; ") { "${it.key}=${it.value.toLong()} ms" }+"\nPrésentation estimée ≠ son entendu. Fermeture retournée ≠ mémoire native libérée."
}
