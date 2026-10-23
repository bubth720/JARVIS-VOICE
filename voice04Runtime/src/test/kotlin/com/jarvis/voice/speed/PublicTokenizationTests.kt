package com.jarvis.voice.speed
import com.jarvis.voice.pocket.*
import org.json.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import java.io.File
class PublicTokenizationTests {
    @Test fun exactOfficialTokenizer50_51_100_150_200LosslessAndDeterministic() {
        val pack=System.getenv("VOICE04_PACK").orEmpty();assumeTrue("Explicit local existing tokenizer required",pack.isNotBlank())
        val f=FrenchFrontend(JSONObject(File(pack,"tokenizer.json").readText()))
        fun phrase(target: Int): String {
            for(n in 1..48) for(word in listOf("le","train","Paris","bonjour")) {
                val s="Bonjour "+List(n) { word }.joinToString(" ")+"."
                val p=runCatching { f.prepare(s) }.getOrNull()
                if(p?.tokens?.size==target) return s
            }
            error("No public counting fixture for $target")
        }
        val rows=JSONArray()
        for(total in listOf(50,51,100,150,200)) {
            val jobs=(total+49)/50
            val counts=List(jobs) { total/jobs+if(it<total%jobs) 1 else 0 }
            val text=counts.joinToString("  ",transform=::phrase)
            val a=LongFormPlanner.prepare(LongFormSpeechRequestV1(text),f);val b=LongFormPlanner.prepare(LongFormSpeechRequestV1(text),f)
            assertEquals(total,a.tokens);assertEquals(counts,a.segments.map { it.tokens });assertEquals(text,a.segments.joinToString("") { it.text })
            assertEquals(a.segments.map { it.text },b.segments.map { it.text })
            rows.put(JSONObject().put("requestedTokens",total).put("actualTokens",a.tokens).put("jobs",a.segments.size).put("lossless",true).put("publicCountingFixture",text))
        }
        System.getenv("VOICE04_TOKEN_OUT")?.let { File(it).writeText(JSONObject().put("tokenizer","official pinned French Unigram").put("rows",rows).toString(2)) }
    }
}
