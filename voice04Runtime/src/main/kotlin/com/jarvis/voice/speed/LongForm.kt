package com.jarvis.voice.speed
import com.jarvis.voice.pocket.FrenchFrontend
import com.jarvis.voice.pocket.PreparedText
import com.jarvis.voice.pocket.CancellationToken

/** Internal local version only. No extension to CoreVoiceRequestV1 or Binder. */
class LongFormSpeechRequestV1(val text: String,val manualNewlineCuts: Boolean=false) {
    init { require(text.length in 1..1800) { "LONGFORM_INPUT_LIMIT" } }
    override fun toString()="LongFormSpeechRequestV1(units=${text.length}, manual=$manualNewlineCuts)"
}
enum class LongFormRefusal { AMBIGUOUS_BOUNDARY, INCOMPLETE_SENTENCE, SEGMENT_TOO_LONG, BATCH_REQUIRED, INVALID_TEXT }
class LongFormRejected(val reason: LongFormRefusal): IllegalArgumentException(reason.name)
class LongFormSegment internal constructor(val start: Int,val end: Int,val text: String,val tokens: Int) {
    override fun toString()="LongFormSegment(start=$start,end=$end,tokens=$tokens)"
}
class LongFormPlan internal constructor(val request: LongFormSpeechRequestV1,segments: List<LongFormSegment>) {
    val segments: List<LongFormSegment> = java.util.Collections.unmodifiableList(segments.toList())
    init {
        require(segments.size in 1..6 && segments.first().start==0 && segments.last().end==request.text.length)
        require(segments.zipWithNext().all { (a,b)->a.end==b.start })
        require(segments.all { it.text==request.text.substring(it.start,it.end) && it.tokens in 1..50 && it.text.length<=300 })
        require(segments.joinToString("") { it.text }==request.text)
    }
    val tokens get()=segments.sumOf { it.tokens }
    override fun toString()="LongFormPlan(jobs=${segments.size},tokens=$tokens)"
}
object LongFormPlanner {
    fun prepare(request: LongFormSpeechRequestV1,frontend: FrenchFrontend)=prepareWith(request,frontend::prepare)
    /** Synthetic seam; production always passes the pinned FrenchFrontend.prepare. */
    fun prepareWith(request: LongFormSpeechRequestV1,tokenizer: (String)->PreparedText): LongFormPlan {
        val text=request.text
        fun reject(reason: LongFormRefusal): Nothing=throw LongFormRejected(reason)
        if(text.isBlank() || text.any { it.isISOControl() && it !in "\n\t\r" } || Regex("<(?:unk|s|/s|pad)>").containsMatchIn(text)) reject(LongFormRefusal.INVALID_TEXT)
        var p=0
        while(p<text.length) {
            val c=text[p]
            if(Character.isHighSurrogate(c)) { if(p+1>=text.length || !Character.isLowSurrogate(text[p+1])) reject(LongFormRefusal.INVALID_TEXT);p+=2 }
            else { if(Character.isLowSurrogate(c)) reject(LongFormRefusal.INVALID_TEXT);p++ }
        }
        val cuts=mutableListOf<Int>()
        if(request.manualNewlineCuts) {
            text.forEachIndexed { i,c -> if(c=='\n') cuts.add(i+1) }
            if(cuts.lastOrNull()!=text.length) cuts.add(text.length)
        } else {
            if(text.any { it in "\"«»“”()[]" }) reject(LongFormRefusal.AMBIGUOUS_BOUNDARY)
            var i=0
            while(i<text.length) {
                if(text[i] in ".!?…") {
                    if(i+1<text.length && !text[i+1].isWhitespace()) reject(LongFormRefusal.AMBIGUOUS_BOUNDARY)
                    val prefix=text.substring(cuts.lastOrNull() ?: 0,i+1).trim()
                    if(Regex("(?:^|\\s)(?:[A-ZÀ-Ý]|M|Mme|Mlle|Dr|Pr|St|etc)\\.$",RegexOption.IGNORE_CASE).containsMatchIn(prefix)) reject(LongFormRefusal.AMBIGUOUS_BOUNDARY)
                    var end=i+1;while(end<text.length && text[end].isWhitespace()) end++
                    cuts.add(end);i=end
                } else i++
            }
            if(cuts.lastOrNull()!=text.length) reject(LongFormRefusal.INCOMPLETE_SENTENCE)
        }
        if(cuts.size>6) reject(LongFormRefusal.BATCH_REQUIRED)
        var start=0
        val segments=cuts.map { end ->
            val raw=text.substring(start,end);val trimmed=raw.trim()
            if(trimmed.isEmpty() || trimmed.trimEnd('"','»','”','\'','’',')',']').lastOrNull() !in listOf('.','!','?','…') || trimmed.split(Regex("\\s+")).size<2) reject(LongFormRefusal.INCOMPLETE_SENTENCE)
            if(raw.length>300) reject(LongFormRefusal.SEGMENT_TOO_LONG)
            val prepared=try { tokenizer(raw) } catch(_: IllegalArgumentException) { reject(LongFormRefusal.SEGMENT_TOO_LONG) }
            if(prepared.tokens.size !in 1..50) reject(LongFormRefusal.SEGMENT_TOO_LONG)
            LongFormSegment(start,end,raw,prepared.tokens.size).also { start=end }
        }
        return LongFormPlan(request,segments)
    }
}

/** Cursor is private RAM. A failed current job is never marked consumed; resumption is explicit. */
class LongFormCursor(val plan: LongFormPlan) {
    @Volatile var completedJobs=0;private set
    private var attempted=false;private var running=false
    @Synchronized fun run(cancel: CancellationToken,resumeExplicitly: Boolean=false,job: (Int,LongFormSegment)->Unit) {
        check(!running && completedJobs<plan.segments.size)
        check(!attempted || resumeExplicitly) { "EXPLICIT_RESUME_REQUIRED" }
        attempted=true;running=true
        try {
            while(completedJobs<plan.segments.size) {
                cancel.check();job(completedJobs,plan.segments[completedJobs]);cancel.check();completedJobs++
            }
        } finally { running=false }
    }
}
