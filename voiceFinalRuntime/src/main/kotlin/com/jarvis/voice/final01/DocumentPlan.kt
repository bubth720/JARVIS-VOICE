package com.jarvis.voice.final01

import com.jarvis.voice.pocket.FrenchFrontend
import com.jarvis.voice.pocket.PreparedText
import com.jarvis.voice.pocket.CancellationToken

enum class Boundary { SENTENCE, CLAUSE, WORD_LIMIT, END }
class DocumentSegment(val start: Int,val end: Int,val raw: String,val normalized: String,val tokens: Int,val boundary: Boundary) {
    override fun toString()="DocumentSegment(tokens=$tokens,boundary=$boundary)"
}
class DocumentPlan internal constructor(val text: String,segments: List<DocumentSegment>) {
    val segments: List<DocumentSegment> = java.util.Collections.unmodifiableList(segments.toList())
    init {
        require(segments.isNotEmpty() && segments.size<=4000 && segments.first().start==0 && segments.last().end==text.length)
        require(segments.zipWithNext().all { (a,b)->a.end==b.start })
        require(segments.all { it.raw==text.substring(it.start,it.end) && it.tokens in 1..50 && it.raw.length<=300 })
    }
    override fun toString()="DocumentPlan(segments=${segments.size})"
}
/** Raw ranges are lossless. Normalization is explicitly the existing official French frontend. */
object DocumentPlanner {
    const val MAX_UNITS=200000
    fun prepare(text: String,frontend: FrenchFrontend,cancel: CancellationToken)=prepareWith(text,frontend::prepare,cancel)
    fun prepareWith(text: String,tokenize: (String)->PreparedText,cancel: CancellationToken=CancellationToken()): DocumentPlan {
        require(text.length in 1..MAX_UNITS && text.isNotBlank()) { "DOCUMENT_LIMIT" }
        require(!Regex("<(?:unk|s|/s|pad)>").containsMatchIn(text)) { "RESERVED_TOKEN" }
        var i=0
        while(i<text.length) {
            val c=text[i];require(!c.isISOControl() || c in "\n\r\t") { "CONTROL_CHARACTER" }
            if(Character.isHighSurrogate(c)) { require(i+1<text.length && Character.isLowSurrogate(text[i+1]));i+=2 }
            else { require(!Character.isLowSurrogate(c));i++ }
        }
        val rows=mutableListOf<DocumentSegment>();var start=0
        while(start<text.length) {
            cancel.check();require(rows.size<4000) { "DOCUMENT_SEGMENT_LIMIT" }
            val limit=minOf(start+300,text.length)
            val candidates=mutableListOf<Pair<Int,Boundary>>()
            // Only whitespace boundaries: never cut inside a word, decimal, date or surrogate pair.
            var p=start
            while(p<limit) {
                if(text[p].isWhitespace()) {
                    var end=p+1;while(end<text.length && text[end].isWhitespace()) end++
                    // Closing quotation marks separated by a space belong to this sentence.
                    var closure=end
                    while(closure<text.length && text[closure] in "»”\"')]") closure++
                    if(closure>end && (closure==text.length || text[closure].isWhitespace())) {
                        while(closure<text.length && text[closure].isWhitespace()) closure++
                        end=closure
                    }
                    if(end<=limit && p>start) {
                        val prefix=text.substring(start,p).trimEnd('"','»','”',')',']')
                        val last=prefix.lastOrNull()
                        val abbreviation=Regex("(?:^|\\s)(?:M|Mme|Mlle|Dr|Pr|St|etc|[A-ZÀ-Ý])\\.$",RegexOption.IGNORE_CASE).containsMatchIn(prefix)
                        val kind=when { last in listOf('!','?','…')->Boundary.SENTENCE;last=='.' && !abbreviation->Boundary.SENTENCE;last in listOf(',', ';', ':')->Boundary.CLAUSE;else->Boundary.WORD_LIMIT }
                        candidates.add(end to kind)
                    };p=end
                } else p++
            }
            if(limit==text.length) candidates.add(text.length to Boundary.END)
            val ranked=candidates.filter { it.second==Boundary.SENTENCE }+
                candidates.filter { it.second==Boundary.END }.asReversed()+
                candidates.filter { it.second==Boundary.CLAUSE }.asReversed()+
                candidates.filter { it.second==Boundary.WORD_LIMIT }.asReversed()
            var selected: Triple<Int,Boundary,PreparedText>?=null
            for((end,kind) in ranked) {
                cancel.check();val raw=text.substring(start,end)
                if(raw.isBlank()) continue
                val prepared=try { tokenize(raw) } catch(_: IllegalArgumentException) { continue }
                if(prepared.tokens.size in 1..50) { selected=Triple(end,kind,prepared);break }
            }
            require(selected!=null) { "UNSPLITTABLE_WORD_OR_TOKEN_LIMIT" }
            // First complete sentence minimizes first-job latency; otherwise prefer the last safe clause.
            val (end,kind,prepared)=selected
            rows.add(DocumentSegment(start,end,text.substring(start,end),prepared.text,prepared.tokens.size,kind));start=end
        }
        return DocumentPlan(text,rows)
    }
}
