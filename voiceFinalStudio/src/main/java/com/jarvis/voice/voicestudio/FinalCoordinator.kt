package com.jarvis.voice.voicestudio

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import com.jarvis.voice.final01.*
import com.jarvis.voice.identity.*
import com.jarvis.voice.identity.VoiceProfile
import com.jarvis.voice.pocket.*
import com.jarvis.voice.speed.*
import org.json.*
import java.io.File
import java.util.concurrent.atomic.AtomicReference

object FinalAudioCleanup { var clear: ()->Unit={} }
object FinalSafety { @Volatile var quarantined=false }
class FinalApplication: StudioApplication() {
    val finalVoice by lazy { FinalCoordinator(this,controller) }
}
/** All heavy work enters the existing application worker/admission and native cancellation token. */
class FinalCoordinator(private val context: Context,val controller: StudioController): LocalVoiceControl {
    private val pool=PcmBufferPool()
    private val audio=AtomicReference<PcmPlayer?>()
    private val cache=LocalAudioCache(File(context.noBackupFilesDir,"final01-audio"))
    private val main=android.os.Handler(android.os.Looper.getMainLooper())
    var listener: (()->Unit)?=null
    @Volatile var status="Expérimental · aucun essai POCO ni émotion validée.";private set
    @Volatile var session: DocumentSession?=null;private set
    @Volatile var report=JSONObject();private set
    private var profile: VoiceProfile?=null
    private var settings: com.jarvis.voice.studio.StudioSettings?=null
    private var mode=ResourceMode.AUTO
    @Volatile private var renderOptions: PreparedRenderOptions?=null
    private var frontend: FrenchFrontend?=null
    private var frontendSha: String?=null
    private var lastBinding: String?=null
    init { FinalAudioCleanup.clear={ cache.clear();lastBinding=null } }
    private var pressedNs=0L
    private var firstPcmFromPress: Double?=null
    private var firstPresentationFromPress: Double?=null
    private fun notifyUi() { main.post { listener?.invoke() } }
    private fun event(e: VoiceEvent) { status="${e.type} · ${e.completed}/${e.total} segments lus, ${e.skipped} omis volontairement ; première restitution estimée ≠ audibilité humaine.";notifyUi() }
    private fun binding(p: VoiceProfile): String {
        val s=controller.state.settings
        val bytes=(p.id+":"+p.sha256+":"+p.conditioningSha256+":"+s.seed+":"+s.steps+":"+s.temperature+":"+session?.plan?.text.orEmpty()).toByteArray()
        return java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    private fun admission(): Long {
        check(!FinalSafety.quarantined) { "UNKNOWN_NATIVE" }
        val memory=ActivityManager.MemoryInfo();(context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
        IdentityAdmission.requireHeavy(memory.availMem,memory.lowMemory,DeviceMetrics.thermal(context))
        val battery=(context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 }
        return ResourcePolicy.cooldownMs(mode,battery,DeviceMetrics.thermal(context))
    }
    fun prepare(text: String,resource: ResourceMode,emotion: Emotion) {
        check(!controller.busy);PocketEmotionCapabilities.requireSupported(emotion)
        val preparePressed=System.nanoTime()
        controller.run("Préparation du document…",true) { c ->
            admission();ExperimentalModelPack.verify(controller.pack,c::isCancelled)
            val begin=System.nanoTime();val frontend=FrenchFrontend(JSONObject(File(controller.pack,"tokenizer.json").readText()))
            val plan=DocumentPlanner.prepare(text,frontend,c)
            this.frontend=frontend;frontendSha=VoiceIdentityVault.sha(File(controller.pack,"tokenizer.json"),c)
            profile=controller.library.activeConfirmed();settings=controller.state.settings;mode=resource
            renderOptions=PreparedRenderOptions(resource,controller.state.settings.steps,controller.state.settings.temperature)
            session=DocumentSession(plan,::event)
            report=JSONObject().put("schema","voice-final01/1").put("preflightMs",(System.nanoTime()-begin)/1e6).put("segments",plan.segments.size).put("prepareTotalMs",(System.nanoTime()-preparePressed)/1e6)
                .put("wordBoundaryCuts",plan.segments.count { it.boundary==Boundary.WORD_LIMIT }).put("humanAudibilityMs",JSONObject.NULL)
            status="${plan.segments.size} segments prévus, chacun ≤50 tokens. ${plan.segments.count { it.boundary==Boundary.WORD_LIMIT }} coupures de limite ; ponctuation normalisée par le frontend officiel. Profil figé jusqu’à nouvelle préparation."
            notifyUi();status
        }
    }
    fun requireUiOptions(resource: ResourceMode,emotion: Emotion,steps: Int,temperature: Double) {
        requireNotNull(renderOptions) { "PREPARE_FIRST" }.requireUi(resource,emotion,steps,temperature)
    }
    fun speak(resume: Boolean=false,recovery: Recovery?=null,comparison: String?=null) {
        val q=requireNotNull(session) { "PREPARE_FIRST" };val p=requireNotNull(profile);val s=requireNotNull(settings)
        check(!controller.busy);require(controller.library.activeConfirmed()==p && controller.state.settings==s) { "PROFILE_OR_SETTINGS_CHANGED" }
        if(comparison!=null) require(q.plan.segments.size==1 && comparison in setOf("A","B")) { "AB_REQUIRES_ONE_SEGMENT" }
        pressedNs=System.nanoTime();firstPcmFromPress=null;firstPresentationFromPress=null
        controller.run("Lecture du document…",true) { c ->
            val rows=JSONArray();val counters=OperationMetrics(context);counters.start { stop() }
            try {
                fun process(index: Int,segment: DocumentSegment) {
                    val delay=admission();require(controller.library.activeConfirmed()==p)
                    if(index>0) repeat((delay/100).toInt()) { c.check();Thread.sleep(100) }
                    val trace=SpeedTrace();val detail=StageProfiler();val begin=System.nanoTime();var closed=false
                    val jobCounters=OperationMetrics(context);jobCounters.start { stop() }
                    try {
                        require(VoiceIdentityVault.sha(File(controller.pack,"tokenizer.json"),c)==frontendSha) { "TOKENIZER_CHANGED_PREPARE_AGAIN" }
                        val voice=detail.timed(Stage.PROFILE_RESOLVE) { controller.vault.binding().resolve(p.identityId,p.id,c) }
                        PooledPcmCapture(pool).use { capture ->
                            val engine: ExperimentalSpeechBackend=if(comparison=="A") ObservedQualityEngine(controller.pack,voice,loadCancel=c,sampling=s.sampling(),trace=trace)
                                else FinalObservedQualityEngine(controller.pack,voice,loadCancel=c,sampling=s.sampling(),trace=trace,profiler=detail,frontendOverride=frontend)
                            val metrics=engine.use { it.generate(segment.raw,c,s.seed,true) { frame -> if(firstPcmFromPress==null) firstPcmFromPress=(System.nanoTime()-pressedNs)/1e6;capture.append(frame) } };closed=true
                            capture.present(metrics.eosReached) { data,count ->
                                val exact=data.copyOf(count)
                                try {
                                    val key=binding(p);cache.save(comparison ?: "B",key,exact,c);lastBinding=key
                                    status="Génération réussie ; PCM privé conservé avant lecture. ${PcmStatistics.measure(exact).summary()}";notifyUi()
                                    play(exact,c,trace,detail,q)
                                } finally { exact.fill(0f) }
                            }
                            val row=JSONObject().put("segment",index).put("samples",(metrics.audioSeconds*24000).toLong()).put("computeMs",metrics.computeMs)
                                .put("firstPcmMs",metrics.firstPcmMs).put("audioSeconds",metrics.audioSeconds).put("rtf",metrics.rtf).put("stagesMs",detail.json())
                                .put("trace",trace.json()).put("device",jobCounters.json()).put("elapsedMs",(System.nanoTime()-begin)/1e6)
                            rows.put(row)
                        }
                    } catch(t: Throwable) {
                        report.put("failedStageTrace",trace.json()).put("failedStageDurations",detail.json());notifyUi()
                        if(trace.has(SpeedPoint.NATIVE_OPEN_STARTED) && !closed && !trace.has(SpeedPoint.CLOSE_RETURNED)) { FinalSafety.quarantined=true;pool.close();throw NativeResourceUnknown() }
                        throw t
                    } finally { jobCounters.close() }
                }
                if(comparison==null) q.run(c,resume,recovery,::process)
                else { process(0,q.plan.segments.single());status="Comparaison $comparison terminée, même profil/texte/graine/sampler. Aucun meilleur rendu choisi." }
                status
            } finally {
                counters.close()
                report.put("resumed",resume).put("metricsCoverThisRunOnly",true).put("runElapsedFromPressMs",(System.nanoTime()-pressedNs)/1e6).put("jobs",rows).put("device",counters.json()).put("completed",q.completed).put("skipped",q.skipped).put("state",q.state.name)
                    .put("firstPcmMs",Quantiles.summarize((0 until rows.length()).map { rows.getJSONObject(it).getDouble("firstPcmMs") }))
                    .put("computeMs",Quantiles.summarize((0 until rows.length()).map { rows.getJSONObject(it).getDouble("computeMs") }))
                    .put("pressedToFirstPcmMs",firstPcmFromPress ?: JSONObject.NULL).put("pressedToPresentationEstimatedMs",firstPresentationFromPress ?: JSONObject.NULL)
                    .put("threads",1).put("humanListening",false).put("nativeUnloadVerified",false)
                val observed=(0 until rows.length()).map(rows::getJSONObject)
                val aggregate=JSONObject()
                listOf("elapsedMs","rtf").forEach { key->aggregate.put(key,Quantiles.summarize(observed.map { it.getDouble(key) })) }
                listOf("cpuProcessMs","pssPeakMiB","thermalPeak","energyDeltaNWh").forEach { key->aggregate.put(key,Quantiles.summarize(observed.mapNotNull { row->row.getJSONObject("device").let { if(it.isNull(key)) null else it.getDouble(key).takeIf { v->v>=0 } } })) }
                aggregate.put("presentationEstimatedMs",Quantiles.summarize(observed.mapNotNull { row->row.getJSONObject("trace").getJSONObject("eventsMs").let { if(it.isNull("PRESENTATION_ESTIMATED")) null else it.getDouble("PRESENTATION_ESTIMATED") } }))
                aggregate.put("humanAudibilityMs",JSONObject.NULL);report.put("quantiles",aggregate)
                notifyUi()
            }
        }
    }
    private fun play(pcm: FloatArray,c: CancellationToken,trace: SpeedTrace=SpeedTrace(),detail: StageProfiler=StageProfiler(),q: DocumentSession?=null,floatOutput: Boolean=false) {
        check(!FinalSafety.quarantined) { "UNKNOWN_NATIVE" }
        val player=PcmPlayer(context,c,floatOutput=floatOutput,onStarted={ if(pressedNs>0 && firstPresentationFromPress==null) firstPresentationFromPress=(System.nanoTime()-pressedNs)/1e6;if(q?.state==DocumentState.RUNNING) q.presentation() },speed=trace)
        audio.set(player)
        try { detail.timed(Stage.AUDIO_TRANSFER) { player.write(pcm) };detail.timed(Stage.AUDIO_DRAIN) { player.drain() };check(player.completed) }
        finally { try { player.close() } catch(t: Throwable) { FinalSafety.quarantined=true;pool.close();throw NativeResourceUnknown() } finally { audio.compareAndSet(player,null) } }
    }
    override fun pauseAfterSegment()=pause()
    fun pause() { session?.requestPause();status="Pause demandée à la fin du segment courant. Arrêt coupe immédiatement.";notifyUi() }
    override fun stop() { audio.get()?.stop();controller.stop() }
    fun replay(side: String,floatOutput: Boolean=false) { controller.run("Réécoute locale sans modèle…") { c ->
        val p=controller.library.activeConfirmed();require(lastBinding==binding(p));val pcm=cache.load(side,binding(p),c)
        try { play(pcm,c,floatOutput=floatOutput) } finally { pcm.fill(0f) };"Réécoute terminée."
    } }
    fun export(side: String,uri: android.net.Uri) { controller.requireLocal(uri);controller.run("Export WAV volontaire…") { c ->
        val p=controller.library.activeConfirmed();require(lastBinding==binding(p))
        cache.export(side,binding(p),c) { bytes -> context.contentResolver.openOutputStream(uri,"wt")!!.use { it.write(bytes) } };"WAV exporté sur demande."
    } }
    fun clearAudio() { check(!controller.busy);cache.clear();lastBinding=null;status="Réécoutes A/B effacées ; profil intact.";notifyUi() }
    fun analyzeReferences() { controller.run("Analyse locale des références conservées…",true) { c ->
        val p=controller.library.activeConfirmed();val identity=controller.vault.list().single { it.id==p.identityId };val lines=mutableListOf<String>()
        identity.references.filter { it.retained }.take(8).forEachIndexed { i,r ->
            val pcm=controller.vault.readReference(identity.id,r.id,c)
            try { val f=ReferenceFeatures.analyze(pcm,c);lines.add("Extrait ${i+1} : RMS ${f.rms}, silence ${f.silencePercent} %, clipping ${f.clippingPercent} %, F0 estimée ${f.medianPitchHz ?: "indisponible"} Hz.") } finally { pcm.fill(0f) }
        }
        status=lines.joinToString("\n")+"\nMesures acoustiques descriptives, aucune preuve biométrique. Pas de moyenne des 18 caches ni de remplacement de profil. Ajouter les MP3 un par un dans le coffre, avec consentement et revue de chaque extrait.";notifyUi();status
    } }
}
