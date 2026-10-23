package com.jarvis.voice.speed
import com.jarvis.voice.pocket.*
import org.junit.Test
import org.junit.Assert.*
class LongFormTests {
    private fun prepare(text: String,manual: Boolean=false)=LongFormPlanner.prepareWith(LongFormSpeechRequestV1(text,manual)) { raw ->
        PreparedText(raw,LongArray(raw.trim().split(Regex("\\s+")).size),3)
    }
    @Test fun offsetsPreserveEverySpaceAccentAndOrder() {
        val text="  Demain, je prendrai le train.\n\tNous irons à Paris !  Le voyage sera agréable.  "
        val plan=prepare(text);assertEquals(text,plan.segments.joinToString("") { it.text });assertEquals(3,plan.segments.size)
        assertEquals(plan.toString(),prepare(text).toString());assertFalse(plan.toString().contains("Paris"))
    }
    @Test fun quotedOrDecimalOrAbbreviationRequiresHumanCuts() {
        for(text in listOf("Bonjour M. Dupont.","Le coût est 3.14 euros.","Il dit « Bonjour ».")) {
            try { prepare(text);fail(text) } catch(e: LongFormRejected) { assertEquals(LongFormRefusal.AMBIGUOUS_BOUNDARY,e.reason) }
        }
    }
    @Test fun explicitNewlinePreservesQuotesAndDoesNotRewriteNames() {
        val text="Il dit « Bonjour ».\nLe prix est 3.14 euros."
        val plan=prepare(text,true);assertEquals(text,plan.segments.joinToString("") { it.text });assertEquals(2,plan.segments.size)
    }
    @Test fun overSixJobsRequiresSeparateConsentedBatches() {
        try { prepare(List(7) { "Le train arrive." }.joinToString(" "));fail() } catch(e: LongFormRejected) { assertEquals(LongFormRefusal.BATCH_REQUIRED,e.reason) }
    }
    @Test fun incompleteOrOversizedIsRefusedAsAWholeBeforeGeneration() {
        for(text in listOf("Bonjour à tous",List(51) { "Bonjour" }.joinToString(" ")+".")) { try { prepare(text);fail() } catch(_: LongFormRejected) {} }
    }
    @Test fun interruptAndExplicitResumeNeverReplaysPreviousCompletedJob() {
        val cursor=LongFormCursor(prepare("Bonjour à tous. Le train arrive. Nous sommes prêts."));val calls=mutableListOf<Int>()
        try { cursor.run(CancellationToken()) { i,_-> if(i==1) error("INTERRUPT");calls.add(i) };fail() } catch(_: IllegalStateException) {}
        assertEquals(1,cursor.completedJobs)
        try { cursor.run(CancellationToken()) { _,_->fail() };fail() } catch(_: IllegalStateException) {}
        cursor.run(CancellationToken(),true) { i,_-> calls.add(i) };assertEquals(listOf(0,1,2),calls)
    }
    @Test fun cancellationInJobNeverConsumesIt() {
        val c=CancellationToken();val cursor=LongFormCursor(prepare("Bonjour à tous. Le train arrive."))
        try { cursor.run(c) { _,_->c.cancel() };fail() } catch(_: java.util.concurrent.CancellationException) {}
        assertEquals(0,cursor.completedJobs)
    }
    @Test fun invalidUtf16AndReservedTokensRefused() {
        for(s in listOf("Bonjour \uD800 à tous.","Bonjour <s> à tous.")) { try { prepare(s);fail() } catch(e: LongFormRejected) { assertEquals(LongFormRefusal.INVALID_TEXT,e.reason) } }
    }
}
