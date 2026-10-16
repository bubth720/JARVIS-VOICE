package com.jarvis.voice.studio

/** Select a contiguous region of decoded audio frames, after codec gapless trimming.
 * MP3 output timestamps may retain encoder delay; they are not PCM array indices.
 * Never pads, repeats or joins files. An early EOS remains incomplete.
 */
class StudioPcmWindow(startMs: Long,durationMs: Long,rate: Int) {
    init { require(startMs in 0..175000 && durationMs in 5000..15000 && rate in 8000..192000) }
    private val start=startMs*rate/1000
    val expected=durationMs*rate/1000
    private val end=start+expected
    private var cursor=0L
    val complete get()=cursor>=end
    fun next(frames: Int): IntRange {
        require(frames>=0 && frames<=192000*17)
        val first=(start-cursor).coerceIn(0,frames.toLong()).toInt()
        val last=(end-cursor).coerceIn(first.toLong(),frames.toLong()).toInt()
        cursor+=frames
        return first until last
    }
}
