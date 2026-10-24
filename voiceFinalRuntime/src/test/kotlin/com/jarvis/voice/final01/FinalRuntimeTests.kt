package com.jarvis.voice.final01
import com.jarvis.voice.pocket.*
import org.junit.Test
import org.junit.Assert.*
import java.nio.file.Files
import java.io.File
class FinalRuntimeTests {
    private fun plan(text: String)=DocumentPlanner.prepareWith(text,{ raw -> val count=raw.trim().split(Regex("\\s+")).size;require(count<=50);PreparedText(raw,LongArray(count),3) })
    @Test fun thousandsOfWordsAreLosslessAndEverySegmentRemainsBounded() {
        val text=List(800) { "Demain, le train arrivera à Paris.\n" }.joinToString("")
        val p=plan(text);assertEquals(800,p.segments.size);assertEquals(text,p.segments.joinToString("") { it.raw });assertTrue(p.segments.all { it.tokens<=50 && it.raw.length<=300 })
    }
    @Test fun longSentenceBacktracksAtClausesOrWordsWithoutDroppingPunctuationOrNumbers() {
        val text="Le 24 octobre 2026, M. Dupont lit 3,14 euros et 12.50 dollars, "+List(70) { "bonjour" }.joinToString(" ")+"."
        val p=plan(text);assertTrue(p.segments.size>1);assertEquals(text,p.segments.joinToString("") { it.raw });assertFalse(p.segments.any { it.raw.endsWith("12.") })
    }
    @Test fun spacedClosingQuoteStaysWithItsSentenceWithoutCreatingAnEmptyJob() {
        val text="Elle dit : « Bonjour le monde. »\n"
        val p=plan(text);assertEquals(1,p.segments.size);assertEquals(text,p.segments.single().raw)
    }
    @Test fun invalidInputAndUnsplitWordAreRefusedRatherThanTruncated() {
        for(s in listOf("", "a".repeat(200001),"Texte \uD800 invalide.","<unk>","a".repeat(301))) assertThrows(IllegalArgumentException::class.java) { plan(s) }
    }
    @Test fun pauseAndResumeNeverReplayCompletedSegments() {
        val p=plan("Bonjour Paris. Demain aussi. Encore demain.");lateinit var q: DocumentSession;val seen=mutableListOf<Int>();val events=mutableListOf<VoiceEvent>()
        q=DocumentSession(p,events::add);q.run(CancellationToken()) { i,_->seen.add(i);q.requestPause() };assertEquals(1,q.completed)
        q.run(CancellationToken(),true) { i,_->seen.add(i) };assertEquals(listOf(0,1,2),seen);assertEquals(DocumentState.COMPLETE,q.state);assertTrue(events.any { it.type==VoiceEventType.PAUSE })
    }
    @Test fun interruptedPartialSegmentRequiresExplicitDecisionAndNeverReplaysEarlierOne() {
        val q=DocumentSession(plan("Bonjour Paris. Demain aussi. Encore demain."));val seen=mutableListOf<Int>()
        assertThrows(IllegalStateException::class.java) { q.run(CancellationToken()) { i,_->if(i==1) error("playback failed");seen.add(i) } }
        assertThrows(IllegalArgumentException::class.java) { q.run(CancellationToken(),true) { _,_-> } }
        q.run(CancellationToken(),true,Recovery.SKIP_INTERRUPTED_SEGMENT) { i,_->seen.add(i) }
        assertEquals(listOf(0,2),seen);assertEquals(1,q.skipped);assertEquals(2,q.completed)
    }
    @Test fun stopAfterFinalFrameDoesNotMakeCompletedSegmentReplayable() {
        val q=DocumentSession(plan("Bonjour Paris."));val c=CancellationToken();q.run(c) { _,_->c.cancel() }
        assertEquals(DocumentState.COMPLETE,q.state);assertEquals(1,q.completed)
        assertThrows(IllegalStateException::class.java) { q.run(CancellationToken(),true,Recovery.RETRY_INTERRUPTED_SEGMENT) { _,_-> } }
    }
    @Test fun unknownNativeCannotResumeAndObserverCannotBreakResourceOwnership() {
        val q=DocumentSession(plan("Bonjour Paris.")) { error("untrusted observer") }
        assertThrows(NativeResourceUnknown::class.java) { q.run(CancellationToken()) { _,_->throw NativeResourceUnknown() } }
        assertEquals(DocumentState.UNKNOWN,q.state)
        assertThrows(IllegalStateException::class.java) { q.run(CancellationToken(),true,Recovery.RETRY_INTERRUPTED_SEGMENT) { _,_-> } }
    }
    @Test fun acousticFeaturesSeparateSilenceFromCleanVoicedSignalWithoutIdentityClaims() {
        val silent=ReferenceFeatures.analyze(FloatArray(24000));assertNull(silent.medianPitchHz);assertEquals(100.0,silent.silencePercent,0.0)
        val f=ReferenceFeatures.analyze(FloatArray(24000) { (.1*kotlin.math.sin(it*2*kotlin.math.PI*200/24000)).toFloat() })
        assertEquals(200.0,f.medianPitchHz!!,4.0);assertEquals(.0707,f.rms,.001);assertEquals(0.0,f.clippingPercent,0.0)
    }
    @Test fun cachePreservesRawFloatBitsAndRejectsCorruptionAndWrongProfileContext() {
        val dir=Files.createTempDirectory("final-cache").toFile()
        try { val cache=LocalAudioCache(dir);val pcm=floatArrayOf(-0f,.123456789f,-.5f,1f)
            cache.save("B","context",pcm,CancellationToken());assertEquals(pcm.map { it.toRawBits() },cache.load("B","context",CancellationToken()).map { it.toRawBits() })
            assertThrows(IllegalArgumentException::class.java) { cache.load("B","other",CancellationToken()) }
            val f=File(dir,"B.wav");val bytes=f.readBytes();bytes[45]=(bytes[45].toInt() xor 1).toByte();f.writeBytes(bytes)
            assertThrows(IllegalArgumentException::class.java) { cache.load("B","context",CancellationToken()) };cache.clear();assertTrue(dir.listFiles()!!.isEmpty())
        } finally { dir.deleteRecursively() }
    }
    @Test fun quantilesDoNotReplaceUnavailableMeasurementsWithZero() {
        assertEquals(0,Quantiles.summarize(emptyList()).getInt("n"));assertTrue(Quantiles.summarize(emptyList()).isNull("p95"))
        assertEquals(19.0,Quantiles.summarize((1..20).map(Int::toDouble)).getDouble("p95"),0.0)
    }
    @Test fun unsupportedEmotionAndResourceGuardsAreExplicit() {
        assertEquals(EmotionSupport.NATIVE_NEUTRAL,PocketEmotionCapabilities.support(Emotion.NEUTRAL))
        Emotion.entries.filter { it!=Emotion.NEUTRAL }.forEach { assertThrows(IllegalArgumentException::class.java) { PocketEmotionCapabilities.requireSupported(it) } }
        assertThrows(IllegalArgumentException::class.java) { ResourcePolicy.cooldownMs(ResourceMode.AUTO,50,3) }
        assertThrows(IllegalArgumentException::class.java) { ResourcePolicy.cooldownMs(ResourceMode.BALANCED,9,0) }
        assertEquals(1000,ResourcePolicy.cooldownMs(ResourceMode.AUTO,20,0));assertEquals(1,ResourcePolicy.THREADS)
    }
}
