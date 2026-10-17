package com.jarvis.voice.bridge.harness

import com.jarvis.voice.bridge.*
import com.jarvis.voice.identity.*
import com.jarvis.voice.expression.*
import com.jarvis.voice.pocket.*
import org.json.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.roundToInt

/** Linux-only, licensed public-reference harness. No Android endpoint or application integration. */
private const val PUBLIC_ALBA_SHA="46264e83cb99115c3d210260e029117566d9c64f20266d10daa78107759ede3e"
private const val CHECKPOINT="dd5fe98f5577ecfd56db9af8057cd05fe20cbc91560042aec8a56cfb01c34995"
private fun ms(begin: Long)=(System.nanoTime()-begin)/1e6
private fun sha(bytes: ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
private fun pcmBytes(pcm: FloatArray)=ByteBuffer.allocate(pcm.size*4).order(ByteOrder.LITTLE_ENDIAN).apply { pcm.forEach(::putFloat) }.array()
private fun pcm16(pcm: FloatArray)=ByteBuffer.allocate(pcm.size*2).order(ByteOrder.LITTLE_ENDIAN).apply { pcm.forEach { putShort((it.coerceIn(-1f,1f)*32767).roundToInt().toShort()) } }.array()
private fun processCpuMs()=ProcessHandle.current().info().totalCpuDuration().orElse(java.time.Duration.ZERO).toNanos()/1e6
private fun memoryKb(path: String,key: String): Long?=runCatching { File(path).useLines { lines->lines.first { it.startsWith("$key:") }.substringAfter(':').trim().substringBefore(' ').toLong() } }.getOrNull()
private class LinuxMonitor: AutoCloseable {
    @Volatile var peakPssKb: Long=0;private set
    private val monitor=Executors.newSingleThreadScheduledExecutor()
    init { monitor.scheduleWithFixedDelay({ memoryKb("/proc/self/smaps_rollup","Pss")?.let { peakPssKb=maxOf(peakPssKb,it) } },0,250,TimeUnit.MILLISECONDS) }
    override fun close() { monitor.shutdownNow() }
}
private fun linuxAdmission() {
    require(System.getProperty("os.name")=="Linux") { "Linux fixture harness only" }
    val host=(memoryKb("/proc/meminfo","MemAvailable") ?: 0)*1024
    val container=runCatching { File("/sys/fs/cgroup/memory.max").readText().trim().toLong()-File("/sys/fs/cgroup/memory.current").readText().trim().toLong() }.getOrNull()
    require(minOf(host,container ?: host)>=2L*1024*1048576) { "2 GiB available required; no forced benchmark" }
}
private data class Scenario(val id: String,val text: String,val pauses: Boolean=false,val pauseMs: Int=0)
private val scenarios=listOf(Scenario("short","Bonjour à tous."),Scenario("long","Demain, je prendrai le train pour Paris. Nous sommes prêts."),
    Scenario("three_sentences_off","Bonjour à tous.\nNous sommes prêts.\nLe train arrive."),Scenario("three_sentences_500ms","Bonjour à tous.\nNous sommes prêts.\nLe train arrive.",true,500))

/** This renderer requires a bound public fixture and is never packaged into Studio. */
class PublicFixtureRenderer internal constructor(private val pack: File,private val voice: PersonalVoiceProfile,private val expected: VoiceBinding): VoiceRendererPort {
    private var active: Pair<String,CancellationToken>?=null
    var lastPcm: FloatArray?=null;private set
    override fun render(request: SpeechJobRequest): SpeechJobResult {
        require(request.binding==expected) { "UNAUTHORIZED" }
        val token=CancellationToken();synchronized(this) { require(active==null) { "BUSY" };active=request.cancellationId to token };val begin=System.nanoTime()
        try {
            require(begin/1000000<request.deadlineLocalMs) { "EXPIRED" }
            val plan=CoreRequestAdapter.plan(request)
            val pcm=ExpressionRenderer.render(pack,voice,ExpressionRenderer.prepare(pack,plan),token).pcm
            token.check();require(System.nanoTime()/1000000<request.deadlineLocalMs) { "EXPIRED" };lastPcm=pcm
            return SpeechJobResult(request.correlationId,request.sessionId,GenerationOutcome.SUCCEEDED,PlaybackOutcome.NOT_STARTED,null,UUID.randomUUID().toString(),sha(pcmBytes(pcm)),ms(begin).toLong(),true)
        } finally { synchronized(this) { active=null } }
    }
    @Synchronized override fun cancel(cancellationId: String) { if(active?.first==cancellationId) active?.second?.cancel() }
}

fun main() {
    linuxAdmission()
    val pack=File(requireNotNull(System.getenv("POCKET_EXPERIMENTAL_MODEL_DIR")))
    val public=File(requireNotNull(System.getenv("IDENTITY_PUBLIC_ALBA_WAV")))
    val output=File(requireNotNull(System.getenv("BRIDGE_BENCHMARK_OUT")))
    require(VoiceIdentityVault.sha(public)==PUBLIC_ALBA_SHA) { "Only pinned licensed public Alba may be measured" }
    val modelLock=JSONObject(requireNotNull(QualityEngine::class.java.getResourceAsStream("/experimental-fp32-lock.json")).bufferedReader().use { it.readText() })
    require(modelLock.getString("checkpointSha256")==CHECKPOINT)
    val probe=System.nanoTime();ExperimentalModelPack.verify(pack);val verificationMs=ms(probe)
    val zip=System.getenv("BRIDGE_EXISTING_ZIP")?.let(::File)
    val zipProbe=System.nanoTime();if(zip!=null) require(zip.length()==234932686L && VoiceIdentityVault.sha(zip)=="9436555c6965e3480c06f51fedced0f512a8316e482a7e54df0f4ee83d0a01cd")
    val zipHashMs=if(zip==null) null else ms(zipProbe)
    val work=Files.createTempDirectory("bridge-public-fixture-").toFile();val cancel=CancellationToken();val rows=JSONArray();val groups=JSONArray();val queue=ReentrantLock()
    try {
        val vault=VoiceIdentityVault(File(work,"vault"));val id=vault.create("Public Alba fixture","CC BY 4.0 Kyutai public reference",true)
        val decoded=IdentityWav.decode(public,0,5000,cancel);val rid=vault.addReference(id,decoded,"WAV",PUBLIC_ALBA_SHA,0,ReferenceLabel.NEUTRE,"Public measurement only","licensed reference",true,true,false,cancel)
        val encodeStart=System.nanoTime();val pid=IdentityEncoding.official(vault,id,rid,pack,cancel);val encodingMs=ms(encodeStart)
        vault.activate(id,pid,cancel=cancel);vault.select(id,cancel) // Explicit public-fixture choice, not a cache bypass.
        val p=vault.list().single().profiles.single();val resolveStart=System.nanoTime();val voice=vault.binding().resolve(id,pid,cancel);val resolveMs=ms(resolveStart)
        val binding=VoiceBinding(id.value,p.id,p.sha256,p.conditioningSha256,p.engineId,CHECKPOINT,p.packId)
        for(s in scenarios) {
            val request=SpeechJobRequest(UUID.randomUUID().toString(),UUID.randomUUID().toString(),UUID.randomUUID().toString(),s.text,binding,SpeechOptions(steps=3,pauseMs=s.pauseMs,allowSentencePauses=s.pauses),System.nanoTime()/1000000+90000)
            val expression=CoreRequestAdapter.plan(request)
            // Independent unchanged 005I wrapper is the PCM reference, not the measured assembly helper.
            val reference=ExpressionRenderer.render(pack,voice,ExpressionRenderer.prepare(pack,expression),cancel).pcm
            val expectedHash=sha(pcmBytes(reference));val expected16=sha(pcm16(reference))
            fun renderRow(mode: String,index: Int,reused: QualityEngine?=null,sessionOpenedMs: Double?=null): JSONObject {
                linuxAdmission();val start=System.nanoTime();val cpuStart=processCpuMs();var firstPcm: Double?=null
                val lockBegin=System.nanoTime();queue.lock();val lockMs=ms(lockBegin)
                val monitor=LinuxMonitor();var engine: QualityEngine?=null
                try {
                    val prepStart=System.nanoTime();val plan=ExpressionRenderer.prepare(pack,expression);val prepMs=ms(prepStart)
                    val loadBegin=System.nanoTime();engine=reused ?: QualityEngine(pack,voice,sampling=expression.sampling)
                    val loadMs=if(reused==null) ms(loadBegin) else 0.0
                    val generationBegin=System.nanoTime()
                    val result=ExpressionRenderer.renderWith(plan,cancel) { text,seed,sink -> engine!!.generate(text,cancel,seed,true) { chunk->if(firstPcm==null) firstPcm=ms(start);sink(chunk) } }
                    val generationMs=ms(generationBegin)
                    val closeBegin=System.nanoTime();if(reused==null) engine!!.close();val closeMs=if(reused==null) ms(closeBegin) else 0.0
                    val completeMs=ms(start)
                    require(sha(pcmBytes(result.pcm))==expectedHash && sha(pcm16(result.pcm))==expected16) { "PCM parity failure; abort measurement" }
                    val cache=ExpressionComparisonStore(File(work,"cache"),comparison=false)
                    val saveStart=System.nanoTime();cache.save("A",id,p,s.text,42,expression.sampling,result.pcm,cancel,"NEUTRAL",plan.pauseMs);val saveMs=ms(saveStart)
                    val loadCacheStart=System.nanoTime();val replay=cache.load("A",vault,cancel);val replayMs=ms(loadCacheStart);require(sha(pcmBytes(replay))==expectedHash)
                    val conversionStart=System.nanoTime();val short=pcm16(result.pcm);val conversionMs=ms(conversionStart);require(sha(short)==expected16)
                    val stats=PcmStatistics.measure(result.pcm);require(stats.nonFinite==0 && result.metrics.all { it.eosReached })
                    val total=ms(start)
                    return JSONObject().put("scenario",s.id).put("mode",mode).put("repeat",index).put("seed",42).put("steps",3).put("temperature",.3).put("threads",1)
                        .put("pausesMs",s.pauseMs).put("queueLockMs",lockMs).put("frontendAndPausePlanMs",prepMs).put("modelOpenWithIntegrityMs",loadMs)
                        .put("retainedSessionOpenOutsideRequestMs",sessionOpenedMs ?: JSONObject.NULL).put("generationAndAssemblyMs",generationMs).put("modelCloseMs",closeMs)
                        .put("firstPcmAvailableFromRequestMs",firstPcm ?: JSONObject.NULL).put("completePcmReadyMs",completeMs).put("cacheWriteMs",saveMs).put("cacheReplayReadMs",replayMs)
                        .put("pcm16ConversionMs",conversionMs).put("allMeasuredWorkMs",total).put("cpuProcessMs",processCpuMs()-cpuStart)
                        .put("audioSeconds",result.pcm.size/24000.0).put("computeRtf",result.metrics.sumOf { it.computeMs }/(result.pcm.size/24.0)).put("requestReadyRtf",completeMs/(result.pcm.size/24.0))
                        .put("onnxMainMs",result.metrics.sumOf { it.onnxMainMs }).put("onnxFlowMs",result.metrics.sumOf { it.onnxFlowMs }).put("onnxDecoderMs",result.metrics.sumOf { it.onnxDecoderMs })
                        .put("sampledPeakPssKb",monitor.peakPssKb.takeIf { it>0 } ?: JSONObject.NULL).put("processLifetimeMaxRssKb",memoryKb("/proc/self/status","VmHWM") ?: JSONObject.NULL)
                        .put("audioTrackEntryMs",JSONObject.NULL).put("firstAudibleMs",JSONObject.NULL).put("audibleEndMs",JSONObject.NULL).put("thermal",JSONObject.NULL).put("battery",JSONObject.NULL)
                        .put("pcmSha256",expectedHash).put("pcm16Sha256",expected16).put("bitIdenticalTo005i",true).put("pcmStatistics",stats.summary())
                } finally { if(reused==null) engine?.close();monitor.close();queue.unlock() }
            }
            repeat(2) { rows.put(renderRow("fresh_sessions",it)) }
            val opening=System.nanoTime()
            val retained=QualityEngine(pack,voice,sampling=expression.sampling)
            val openMs=ms(opening);val groupCpu=processCpuMs();var cleanupMs=0.0
            try { repeat(2) { rows.put(renderRow(if(it==0) "retained_first" else "retained_warm",it,retained,openMs)) } }
            finally { val close=System.nanoTime();retained.close();cleanupMs=ms(close) }
            groups.put(JSONObject().put("scenario",s.id).put("sessionOpenMs",openMs).put("sessionCloseMs",cleanupMs).put("scopeWithTwoRequestsMs",ms(opening)).put("twoRequestsAndCleanupCpuMs",processCpuMs()-groupCpu).put("modelClosedAfterScope",true))
            // Verify the actual injectable port once per public scenario; never attach it to CORE.
            val port=PublicFixtureRenderer(pack,voice,binding)
            val outcome=port.render(request.copy(deadlineLocalMs=System.nanoTime()/1000000+90000))
            require(outcome.pcmSha256==expectedHash && outcome.playback==PlaybackOutcome.NOT_STARTED && !outcome.humanAudibilityVerified)
        }
        val result=JSONObject().put("sourceApplicationBaseline","005J cce9a4549c35e721ee37daff014d427ef3656b2a unchanged")
            .put("platform",System.getProperty("os.name")+" "+System.getProperty("os.arch")).put("javaVersion",System.getProperty("java.version")).put("availableProcessors",Runtime.getRuntime().availableProcessors()).put("publicReferenceSha256",PUBLIC_ALBA_SHA)
            .put("publicReferenceLicense","Kyutai tts-voices Alba, CC BY 4.0").put("checkpointSha256",CHECKPOINT).put("packIntegrityProbeMs",verificationMs)
            .put("existingZipHashCheckMs",zipHashMs ?: JSONObject.NULL).put("zipExtractionMs",JSONObject.NULL).put("profileEncodingSetupMs",encodingMs).put("profileResolveSetupMs",resolveMs)
            .put("coldMeaning","Fresh ONNX sessions. OS page cache not flushed; JVM/JIT/process not restarted per request.")
            .put("warmMeaning","Short public-fixture burst, same engine/binding, per-generation states reset; closed at end of scenario. Not enabled on phone.")
            .put("globalInterprocessArbiter",false).put("personalDataUsed",false).put("coreConnected",false).put("androidIpcTested",false).put("xiaomiMeasured",false)
            .put("perSessionOnnxOpenTimes",JSONObject.NULL).put("measurementNotes","Model open includes integrity verification; no backend timing hooks modified. Linux PSS sampled250ms; maxRSS is process high-water. No AudioTrack, thermal or battery measurements on this host.")
            .put("rows",rows).put("retainedSessionScopes",groups)
        output.parentFile?.mkdirs();output.writeText(result.toString(2)+"\n");println("Public fixture baseline complete: ${rows.length()} measured renders, PCM parity preserved; no CORE/Android playback.")
    } finally { work.deleteRecursively() }
}
