package com.jarvis.voice.voicestudio
import com.jarvis.voice.speed.*
import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.jarvis.voice.identity.*
import com.jarvis.voice.identity.VoiceProfile
import com.jarvis.voice.expression.*
import com.jarvis.voice.pocket.*
import com.jarvis.voice.studio.*
import com.jarvis.voice.sdk.*
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/** Application-scoped queue, not an Activity. One heavy model, one ORT thread, no remote transport. */
class StudioController(private val context: Context,private val backend: StudioBackend=OfficialStudioBackend,private val documents: StudioDocuments=LocalStudioDocuments(context)) {
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    private val lock=Any()
    private val root=context.noBackupFilesDir
    private val store=StudioStateStore(File(root,"studio-state.json"))
    val vault=VoiceIdentityVault(File(root,"identity-vault"))
    val library=StudioLibrary(vault,StudioProfileChoice(File(root,"profile-choice.json")))
    @Volatile var state=store.load();private set
    @Volatile var busy=false;private set
    @Volatile var message="Tout reste sur votre téléphone. Aucune émotion native simulée.";private set
    @Volatile var diagnostics="";private set
    @Volatile var metrics="";private set
    @Volatile var audition: Pair<String,JSONObject>?=null;private set
    private var observer: ((Boolean)->Unit)?=null
    private var observerOwner: Any?=null
    private var cancel: CancellationToken?=null
    private val player=AtomicReference<PcmPlayer?>()
    // Transient opt-in only. Existing admission/worker and PcmPlayer remain the resource owners.
    @Volatile var sdkEnabled=false; private set
    private val sdkSession=AtomicReference<SpeechSession?>()
    private var styleSelection: ExpressionStyleProfile?=null
    private var lastStylePlan: LocalProsodyPlan?=null
    @Volatile var styleTimeline: ExpressionTimeline?=null; private set
    private val styleEngine by lazy {
        ExpressionSpeechAdapter(ExpressionRenderPort { request,c ->
            val style=requireNotNull(styleSelection)
            if(ProsodyPlannerAdapter.unsupported(request.plan).isNotEmpty()) throw SpeechRejected(SpeechRefusal.STYLE_UNSUPPORTED)
            val voice=VoicePackResolver(vault,pack).resolve(request,c)
            val planning=try { ProsodyPlannerAdapter.prepare(request.plan,style,{ backend.prepare(pack,it) }) }
                catch(_: IllegalArgumentException) { throw SpeechRejected(SpeechRefusal.INVALID_REQUEST) }
            val prosody=when(planning) {
                is ProsodyPlanningResult.Ready -> planning.plan
                is ProsodyPlanningResult.Unsupported -> throw SpeechRejected(SpeechRefusal.STYLE_UNSUPPORTED)
            }
            val result=backend.render(pack,voice,prosody,c);lastStylePlan=prosody
            PlannedExpressionRender(prosody,result)
        },AvatarTimingCuePort { styleTimeline=it })
    }
    private val sdkSessions=SpeechSessionRepository(LocalSpeechEnginePort { request,c ->
        if(styleSelection!=null) return@LocalSpeechEnginePort styleEngine.render(request,c)
        val voice=VoicePackResolver(vault,pack).resolve(request,c)
        val prosody=try { backend.prepare(pack,request.plan) }
            catch(_: IllegalArgumentException) { throw SpeechRejected(SpeechRefusal.INVALID_REQUEST) }
        val result=backend.render(pack,voice,prosody,c)
        RenderedSpeech(result.pcm,result.metrics.all { it.eosReached })
    })
    private var measure: OperationMetrics?=null
    private var speedTrace: SpeedTrace?=null
    @Volatile private var speedInterrupted=false
    @Volatile var progressiveEnabled=false;private set
    @Volatile var progressivePreload=48000;private set
    private val progressiveJob=AtomicReference<ProgressiveJob?>()
    fun progressive(enabled: Boolean,preload: Int=48000) {
        check(!busy);require(preload in setOf(24000,48000));progressiveEnabled=enabled;progressivePreload=preload
        notice(if(enabled && android.os.Build.VERSION.SDK_INT<31) "Cette API reste en lecture différée : le streaming n’est pas qualifié après sous-alimentation observée en laboratoire." else if(enabled) "Progressif expérimental : prélecture ${preload/24000} s, repli avant lecture si débit insuffisant. Un flux interrompu n’est jamais complet." else "Lecture différée de référence.")
    }
    @Volatile var speedReport: JSONObject?=null;private set
    @Volatile private var speedQuarantined=false
    fun exportSpeed(uri: Uri) {
        requireLocal(uri);val report=requireNotNull(speedReport).toString(2)
        documents.output(uri).use { it.write(report.toByteArray(Charsets.UTF_8)) }
        notice("Rapport numérique expurgé exporté volontairement. Aucun texte, UUID ni audio.")
    }
    fun speedDiagnostic(text: String) {
        val trace=SpeedTrace()
        val p=try { library.activeConfirmed() } catch(t: Throwable) { trace.finish("REFUSED");speedReport=trace.json();diagnostics=trace.summary();throw t }
        val settings=state.settings
        check(!speedQuarantined) { "Fermeture native inconnue : redémarrer le processus avant un nouvel essai." }
        val accepted=run("VOICE-04 : mesure locale, lecture selon l’option expérimentale…",true) { c ->
            speedTrace=trace
            try {
                trace.first(SpeedPoint.ADMITTED);require(library.activeConfirmed()==p)
                speedRender(text,p,trace,c,settings)
                "Mesure terminée. Vérifier l’audibilité à l’oreille ; aucune vitesse Xiaomi présumée."
            } catch(t: Throwable) {
                if(trace.has(SpeedPoint.NATIVE_OPEN_STARTED) && !trace.has(SpeedPoint.CLOSE_RETURNED)) speedQuarantined=true
                speedInterrupted=trace.has(SpeedPoint.PRESENTATION_ESTIMATED)
                trace.finish(if(trace.has(SpeedPoint.PRESENTATION_ESTIMATED)) "INCOMPLETE" else if(c.isCancelled()) "CANCELLED" else "FAILED");throw t
            } finally {
                speedReport=trace.json().put("samplingSteps",settings.steps).put("temperature",settings.temperature).put("deviceCounters",measure?.json() ?: JSONObject.NULL)
                diagnostics=trace.summary()+"\n"+diagnostics;speedTrace=null
            }
        }
        if(!accepted) { trace.finish("REFUSED");speedReport=trace.json();diagnostics=trace.summary() }
    }
    private fun speedRender(text: String,p: VoiceProfile,trace: SpeedTrace,c: CancellationToken,settings: StudioSettings) {
        require(library.activeConfirmed()==p)
        val plan=ExpressionPolicy.plan(CoreVoiceRequestV1(text,p.identityId),ExpressionVoiceBinding.from(p),settings.sampling(),settings.seed)
        val voice=vault.binding().resolve(p.identityId,p.id,c)
        val prosody=backend.prepare(pack,plan);prosody.tokenCounts.forEach(trace::tokens)
        val pcm=if(progressiveEnabled) {
            var job: ProgressiveJob?=null
            try {
                val m=ObservedQualityEngine(pack,voice,loadCancel=c,sampling=settings.sampling(),trace=trace).use { engine ->
                    job=ProgressiveJob(c,{ Voice04StreamOutput(context,c,trace) { detail -> diagnostics=detail;emit() } },progressivePreload,devicePermitted=android.os.Build.VERSION.SDK_INT>=31)
                    progressiveJob.set(job)
                    engine.generate(text,c,settings.seed,true) { requireNotNull(job).offer(it) }
                }
                val complete=requireNotNull(job).finish(m.eosReached)
                diagnostics="Politique ${job!!.mode} ; pic file ${job!!.peakQueuedSamples} échantillons.";emit()
                if(job!!.mode==ProgressiveMode.DEFERRED) play(complete,c)
                complete
            } catch(t: Throwable) { job?.throwIfFailed();throw t } finally {
                try { job?.close() } finally { if(job?.closeUnknown==true) speedQuarantined=true;progressiveJob.set(null) }
            }
        } else {
            val complete=ObservedQualityEngine(pack,voice,loadCancel=c,sampling=settings.sampling(),trace=trace).use { engine ->
                ExpressionRenderer.renderWith(prosody,c) { raw,seed,sink -> engine.generate(raw,c,seed,true,sink) }.pcm
            }
            try { play(complete,c);complete } catch(t: Throwable) { complete.fill(0f);throw t }
        }
        trace.pcm(pcm);diagnostics+= "\n"+PcmStatistics.measure(pcm).summary();pcm.fill(0f);trace.finish("COMPLETED")
    }
    @Volatile var longDraft="";private set
    @Volatile var longManual=false;private set
    fun editLong(text: String,manual: Boolean=longManual) {
        require(!busy);require(text.length<=1800)
        if(text!=longDraft || manual!=longManual) { longDraft=text;longManual=manual;longCursor=null;longProfile=null;longSettings=null;longPreview="Texte modifié : prévisualiser tous les segments avant lecture." }
    }
    @Volatile var longCursor: LongFormCursor?=null;private set
    private var longProfile: VoiceProfile?=null
    private var longSettings: StudioSettings?=null
    @Volatile var longPreview="Aucun texte long préparé.";private set
    fun prepareLong(text: String,manualCuts: Boolean) {
        editLong(text,manualCuts)
        run("Préparation des segments au tokenizer officiel…") { c ->
            longCursor=null;longProfile=null;longSettings=null
            ExperimentalModelPack.verify(pack,c::isCancelled)
            val frontend=FrenchFrontend(JSONObject(File(pack,"tokenizer.json").readText()))
            val plan=LongFormPlanner.prepare(LongFormSpeechRequestV1(text,manualCuts),frontend)
            longCursor=LongFormCursor(plan);longProfile=library.activeConfirmed();longSettings=state.settings
            longPreview="${plan.segments.size} jobs / ${plan.tokens} tokens ; "+plan.segments.mapIndexed { i,seg -> "${i+1}: ${seg.tokens} tokens [${seg.start},${seg.end}[" }.joinToString(" ; ")+
                "\nTexte exact conservé. Une seule phrase complète par job ; 20 s max par job ; aucun PCM de narration entière. Chargement et garde répétés entre jobs."
            longPreview
        }
    }
    fun speakLong(resume: Boolean=false) {
        val cursor=requireNotNull(longCursor);val p=requireNotNull(longProfile);val settings=requireNotNull(longSettings)
        val all=org.json.JSONArray();val pressedNs=System.nanoTime();val initialCompleted=cursor.completedJobs
        run(if(resume) "Reprise explicite au premier job incomplet…" else "Texte long : jobs séquentiels bornés…",true) { c ->
            try {
                cursor.run(c,resume) { index,segment ->
                    admission();require(library.activeConfirmed()==p)
                    val jobOffsetMs=(System.nanoTime()-pressedNs)/1e6
                    val trace=SpeedTrace();trace.first(SpeedPoint.ADMITTED);speedTrace=trace
                    diagnostics="Job ${index+1}/${cursor.plan.segments.size}. Les jobs déjà terminés ne seront pas rejoués.";emit()
                    try { speedRender(segment.text,p,trace,c,settings) }
                    catch(t: Throwable) {
                        if(trace.has(SpeedPoint.NATIVE_OPEN_STARTED) && !trace.has(SpeedPoint.CLOSE_RETURNED)) speedQuarantined=true
                        speedInterrupted=trace.has(SpeedPoint.PRESENTATION_ESTIMATED)
                        trace.finish(if(trace.has(SpeedPoint.PRESENTATION_ESTIMATED)) "INCOMPLETE" else if(c.isCancelled()) "CANCELLED" else "FAILED");throw t
                    } finally { all.put(trace.json().put("jobStartAfterPressMs",jobOffsetMs));speedTrace=null }
                }
                "Texte long terminé : ${cursor.completedJobs}/${cursor.plan.segments.size} jobs présentés. Audibilité à confirmer humainement."
            } finally {
                speedReport=JSONObject().put("schema","voice04.longform/1").put("completedJobs",cursor.completedJobs).put("plannedJobs",cursor.plan.segments.size)
                    .put("attemptStartCompletedJobs",initialCompleted).put("elapsedAfterPressMs",(System.nanoTime()-pressedNs)/1e6).put("jobs",all).put("deviceCounters",measure?.json() ?: JSONObject.NULL).put("samplingSteps",settings.steps).put("temperature",settings.temperature)
                diagnostics=longPreview+"\nJobs terminés : ${cursor.completedJobs}. Reprise uniquement explicite ; le job interrompu sera repris depuis son début."
            }
        }
    }
    val pack get()=File(root,"models/fp32")
    val source get()=File(root,"wizard-source.private")
    var pendingExport: VoiceIdentityId?=null
    private fun audioRoot(id: VoiceIdentityId)=File(root,"identity-audio/${id.value}")
    fun expressions(id: VoiceIdentityId)=ExpressionComparisonStore(File(audioRoot(id),"expression"))
    fun single(id: VoiceIdentityId)=ExpressionComparisonStore(File(audioRoot(id),"single"),comparison=false)
    fun profiles(id: VoiceIdentityId)=IdentityComparisonStore(File(audioRoot(id),"profiles"))
    private fun styleComparisons(id: VoiceIdentityId)=AudibleComparisonStore(File(audioRoot(id),"style-comparison"))
    private val archive get()=VoiceIdentityArchive(File(root,"archive-work"))
    init { worker.execute { File(root,"incoming.candidate").delete();File(root,"archive-work").listFiles()?.forEach { it.deleteRecursively() } } }
    fun observe(owner: Any,listener: (Boolean)->Unit) { observerOwner=owner;observer=listener }
    fun unobserve(owner: Any) { if(observerOwner===owner) { observerOwner=null;observer=null } }
    private fun emit(rebuild: Boolean=false) { main.post { observer?.invoke(rebuild) } }
    fun update(change: (StudioState)->StudioState) { synchronized(lock) { state=change(state);store.save(state) } }
    fun text(value: String) { synchronized(lock) { state=state.copy(text=value) };main.removeCallbacks(persist);main.postDelayed(persist,300) }
    private val persist=Runnable { synchronized(lock) { store.save(state) } }
    fun flush() { main.removeCallbacks(persist);synchronized(lock) { store.save(state) } }
    fun navigate(screen: StudioScreen) { update { it.copy(screen=screen) };emit(true) }
    fun draft(change: (WizardDraft)->WizardDraft) { update { it.copy(wizard=change(it.wizard)) } }
    fun error(t: Throwable) { message=safeError(t);emit() }
    fun notice(text: String) { message=text;emit() }
    fun safeError(t: Throwable): String=when(t) {
        is SpeechRejected -> when(t.reason) {
            SpeechRefusal.MODEL_UNAVAILABLE -> "Pack vocal local absent. Importer le pack existant ; aucune autre voix n’a été choisie."
            SpeechRefusal.PROFILE_UNAVAILABLE -> "Profil vocal absent, désactivé ou incompatible. Choisir explicitement votre profil dans Mes voix."
            SpeechRefusal.BUSY -> "Le SDK est occupé. Attendre le retour de l’opération en cours."
            SpeechRefusal.RELEASED -> "Session SDK fermée. Démarrer explicitement une nouvelle session."
            SpeechRefusal.DUPLICATE_REQUEST -> "Cette demande a déjà été consommée ; aucun rejeu automatique."
            SpeechRefusal.CANCELLED -> "Lecture SDK annulée. Vos profils sont conservés."
            SpeechRefusal.INVALID_REQUEST -> "Phrase ou pauses incompatibles. Garder des phrases complètes, au plus 50 jetons chacune ; aucune troncature automatique."
            SpeechRefusal.STYLE_UNSUPPORTED -> "STYLE_UNSUPPORTED : ce moteur ne permet pas de régler l’intensité, le rythme ou une émotion native."
            SpeechRefusal.NON_VERIFIED -> "Lecture locale non vérifiée. Aucun remplacement automatique par un TTS Android."
            SpeechRefusal.AUDIO_UNAVAILABLE -> "Lecture audio incomplète ou focus indisponible. Aucun succès d’écoute déclaré."
            SpeechRefusal.ENGINE_FAILED -> "SDK bloqué après un échec moteur ou de nettoyage. Aucun nouveau chargement automatique."
            SpeechRefusal.LIMIT_EXCEEDED -> "La file dépasse les limites vocales existantes. Réduire le lot sans modifier les protections."
        }
        is java.util.concurrent.CancellationException -> "Annulé. Vos profils et références déjà validés sont conservés."
        is PlaybackFailure -> "Lecture interrompue : ${t.reason}. Consulter les compteurs ; aucune lecture complète revendiquée."
        is ProgressiveFailure -> "Flux interrompu/incomplet (${if(t.afterStart) "après démarrage" else "démarrage non confirmé"}). Aucun repli avec double lecture."
        is LongFormRejected -> "Texte refusé : ${t.reason}. Vérifier les phrases complètes, les coupes manuelles et les limites avant de préparer à nouveau."
        is FP32ImportException -> t.message.orEmpty().take(250)
        is ReferenceFailure -> "Audio non décodé (${t.diagnostic()}). Choisir un MP3 ou WAV local valide."
        is IllegalArgumentException,is IllegalStateException -> t.message.orEmpty().take(220).takeIf { !it.contains('/') && !it.contains("content:") } ?: "Données incompatibles. Réessayer avec un document local valide ; rien n’a été effacé."
        else -> "Opération impossible (${t.javaClass.simpleName}). Données conservées ; réessayer depuis cet écran."
    }
    private fun admission() {
        check(!speedQuarantined) { "Fermeture native inconnue : aucun nouveau modèle avant redémarrage." }
        if(sdkSessions.isQuarantined) throw SpeechRejected(SpeechRefusal.ENGINE_FAILED)
        val m=ActivityManager.MemoryInfo();(context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(m)
        IdentityAdmission.requireHeavy(m.availMem,m.lowMemory,DeviceMetrics.thermal(context))
    }
    fun run(title: String,heavy: Boolean=false,cleanup: ()->Unit={},action: (CancellationToken)->String): Boolean {
        if(busy) { cleanup();notice("Une opération est déjà en cours. Arrêter avant d’en lancer une autre.");return false }
        try { require(!DeviceMetrics.isThermallySevere(context)) { "Protection thermique : laisser refroidir le téléphone." };if(heavy) admission() } catch(t: Exception) { cleanup();error(t);return false }
        val token=CancellationToken();cancel=token;speedInterrupted=false;busy=true;message=title;emit(true)
        worker.execute {
            val m=OperationMetrics(context);measure=m
            try { m.start { token.cancel();progressiveJob.get()?.stop();player.get()?.stop() };token.check();if(heavy) admission();message=action(token);token.check() }
            catch(t: Throwable) { message=if(speedInterrupted) "Lecture interrompue/incomplète. Consulter les mesures conservées ; aucun succès de lecture annoncé." else if(t is ProgressiveFailure) safeError(t) else if(token.isCancelled()) "Annulé ou protection mémoire/thermique déclenchée. Les éléments validés restent conservés." else safeError(t) }
            finally { cleanup();m.close();metrics=m.summary();measure=null;cancel=null;busy=false;emit(true) }
        }
        return true
    }
    fun stop() { progressiveJob.get()?.stop();sdkSession.get()?.requestStop();cancel?.cancel();player.get()?.stop();notice("Arrêt demandé. Lecture coupée ; annulation native en cours.") }
    fun enableSdk(enabled: Boolean) {
        check(!busy) { "Attendre la fin de l’opération avant de changer de lecteur." }
        sdkEnabled=enabled
        notice(if(enabled) "SDK local activé pour cette exécution de Studio. Aucun changement de voix, de pack ou de droits." else "Lecteur historique rétabli.")
    }
    fun generateSdk() {
        val p=library.activeConfirmed()
        require(hasModel()) { "Modèle absent. Importer le ZIP FP32 existant dans Réglages avancés." }
        val plan=StudioRendering.plan(state,p)
        run("SDK : préparation locale de la phrase…",true) { c ->
            require(library.activeConfirmed()==p)
            val output=object: OutputAudioPort {
                override val focusController=AudioFocusController()
                override fun play(pcm: FloatArray,cancel: CancellationToken): AudioReceipt {
                    return try { this@StudioController.play(pcm,cancel,focusController) }
                        catch(_: PlaybackFailure) { throw SpeechRejected(SpeechRefusal.AUDIO_UNAVAILABLE) }
                }
                override fun requestStop() { player.get()?.stop() }
                override fun close() { /* The existing play() closes its AudioTrack in finally. */ }
            }
            val session=sdkSessions.open(output) { phase -> diagnostics="SDK ${phase.name} ; PCM différé borné ; aucune attestation mémoire Android.";emit() }
            sdkSession.set(session)
            try {
                when(val result=session.speak(SpeechQueue(listOf(SpeechRequest(java.util.UUID.randomUUID().toString(),plan))),c)) {
                    is SpeechOutcome.Completed -> "SDK : lecture terminée (${result.metrics.presentedSamples} échantillons présentés). Aucun nouvel audio enregistré."
                    is SpeechOutcome.Refused -> throw SpeechRejected(result.reason)
                }
            } finally { session.close();sdkSession.compareAndSet(session,null) }
        }
    }
    fun currentIdentity()=vault.list().singleOrNull { it.id==vault.selected() }
    fun wizardIdentity()=state.wizard.identityId?.let { value -> vault.list().singleOrNull { it.id.value==value } }
    fun confirmedProfile()=runCatching { library.activeConfirmed() }.getOrNull()
    fun hasModel()=backend.modelAvailable(pack)
    fun hasReplay()=runCatching { val p=library.activeConfirmed();single(p.identityId).load("A",vault,CancellationToken());true }.getOrDefault(false)
    fun requireLocal(uri: Uri) { require(uri.scheme=="content" && uri.authority in setOf("com.android.externalstorage.documents","com.android.providers.downloads.documents","com.android.providers.media.documents")) { "Choisir Téléchargements ou le stockage interne. Fournisseur distant ou inconnu refusé." } }
    fun importPack(uri: Uri) {
        requireLocal(uri)
        run("Vérification et installation du pack FP32…") { c ->
            require(root.usableSpace>=1200L*1048576) { "Libérer au moins 1,2 Gio de stockage pour ce pack." }
            documents.input(uri).use { ExperimentalModelPack.install(it,File(root,"models"),c::isCancelled) }
            "Modèle FP32 vérifié. Les voix et leurs profils restent inchangés."
        }
    }
    fun createIdentity(legal: Boolean) {
        val d=state.wizard
        run("Création de l’identité privée…") { c -> val next=library.create(d,legal,c);draft { next };"Identité prête. Ajouter les fichiers un par un ; la voix active précédente reste conservée." }
    }
    fun addSource(uri: Uri) {
        requireLocal(uri);require(state.wizard.identityId!=null) { "Créer l’identité et confirmer son consentement d’abord." }
        run("Lecture de la durée réelle de l’audio…") { c ->
            val tmp=File(root,"incoming.candidate")
            try {
                documents.input(uri).use { StudioAudio.copy(it,tmp,c) }
                val info=StudioAudio.inspect(tmp);c.check();check(tmp.renameTo(source))
                draft { it.copy(step=VoiceStep.REVIEW,referenceId=null,startMs=0,durationMs=minOf(10000,info.durationMs),sourceMs=info.durationMs,sourceFormat=info.format,comment="") }
                "Audio ${info.format} · ${info.durationMs/1000.0} s. Choisir et écouter une portion contiguë avant de la valider."
            } finally { tmp.delete() }
        }
    }
    private fun region(d: WizardDraft,c: CancellationToken): FloatArray {
        WizardPolicy.requireRegion(requireNotNull(d.sourceMs),d.startMs,d.durationMs)
        require(source.isFile) { "Source temporaire absente. Ajouter de nouveau ce fichier local ; les références validées restent présentes." }
        return StudioAudio.decode(source,requireNotNull(d.sourceFormat),d.startMs,d.durationMs,c)
    }
    fun previewReference() {
        val d=state.wizard
        run("Écoute de l’extrait sélectionné…") { c -> val pcm=if(d.referenceId!=null) vault.readReference(VoiceIdentityId(requireNotNull(d.identityId)),requireNotNull(d.referenceId),c) else region(d,c)
            diagnostics=ReferenceSelection.assess(pcm).summary();play(pcm,c);"Extrait écouté. Vérifier une parole claire, sans musique et avec un seul locuteur." }
    }
    fun saveReference(legal: Boolean,same: Boolean) {
        val d=state.wizard;require(d.referenceId==null) { "Cet extrait est déjà validé. Continuer ou ajouter un autre fichier." }
        run("Vérification et ajout de l’extrait privé…") { c ->
            val pcm=region(d,c);val next=library.append(d,pcm,VoiceIdentityVault.sha(source,c),legal,same,c)
            diagnostics=ReferenceSelection.assess(pcm).summary();draft { next };source.delete()
            "Extrait ajouté à la même identité. L’original MP3/WAV n’a pas été modifié."
        }
    }
    fun encodeReference() {
        val d=state.wizard;val id=VoiceIdentityId(requireNotNull(d.identityId));val rid=requireNotNull(d.referenceId)
        require(hasModel()) { "Modèle absent. Importer le ZIP FP32 existant dans Réglages avancés." }
        run("Création du profil vocal individuel…",true) { c ->
            if(vault.list().single { it.id==id }.profiles.none { it.referenceId==rid }) backend.encode(vault,id,rid,pack,c)
            draft { it.copy(step=VoiceStep.TRY) };"Profil prêt. L’écouter puis choisir explicitement la voix principale. Aucun profil précédent remplacé."
        }
    }
    fun activate(id: VoiceIdentityId,pid: String,neutral: Boolean=false) {
        run("Confirmation du profil choisi…") { c -> library.activate(id,pid,c,neutral);audition=null;"Voix activée explicitement. Elle reste identique pour tous les essais d’intention." }
    }
    fun restore(uri: Uri,password: CharArray,legal: Boolean) {
        requireLocal(uri)
        if(!legal) { password.fill('\u0000');error(IllegalArgumentException("Confirmer le droit de cloner les voix de cette archive."));return }
        run("Authentification de l’archive privée…",true,cleanup={password.fill('\u0000')}) { c ->
            val previous=vault.selected()
            documents.input(uri).use { input ->
                val id=archive.restore(vault,password,input,c)
                // The archive service selects its imported identity. Preparing a new
                // voice in Studio must keep the previous speaking voice until the
                // owner explicitly activates a profile of the restored identity.
                if(previous!=null) vault.select(previous,CancellationToken())
                val row=vault.list().single { it.id==id }
                val active=row.profiles.singleOrNull { it.id==row.activeProfiles[PocketIdentity.bindingKey] }
                val ref=row.references.singleOrNull { it.id==active?.referenceId }
                draft { WizardDraft(step=VoiceStep.TRY,identityId=id.value,name=row.name,note=row.consentNote,
                    referenceId=ref?.id,startMs=ref?.startMs ?: 0,durationMs=ref?.durationMs ?: 5000,
                    sourceMs=ref?.sourceDurationMs,sourceFormat=ref?.sourceFormat,label=ref?.label ?: ReferenceLabel.NEUTRE,
                    keepWav=ref?.keepAfterEncoding ?: false,comment=ref?.comment.orEmpty()) }
            }
            audition=null;"Archive restaurée sans réencodage. Comparer UUID/SHA256 puis confirmer le profil choisi dans Mes voix."
        }
    }
    fun export(uri: Uri,password: CharArray,includeWav: Boolean,id: VoiceIdentityId) {
        requireLocal(uri)
        run("Sauvegarde chiffrée locale…",true,cleanup={password.fill('\u0000')}) { c ->
            try { documents.output(uri).use { archive.export(vault,id,password,includeWav,it,c) };"Sauvegarde chiffrée créée localement. Conserver le mot de passe séparément." }
            catch(t: Throwable) { runCatching { documents.remove(uri) };throw t }
        }
    }
    fun generate(slot: String?=null) {
        val p=library.activeConfirmed();require(hasModel()) { "Modèle absent. Importer le ZIP FP32 existant dans Réglages avancés." }
        val s=state.settings;val text=state.text;val plan=StudioRendering.plan(state,p,slot=="A")
        run("JARVIS prépare la phrase complète…",true) { c ->
            audition=null;require(library.activeConfirmed()==p)
            val target=if(slot==null) single(p.identityId) else expressions(p.identityId);val side=slot ?: "A"
            if(slot=="B") target.requireB(p.identityId,p,target.context(p.identityId,text,s.seed,s.sampling()),c)
            val voice=vault.binding().resolve(p.identityId,p.id,c)
            val prosody=backend.prepare(pack,plan)
            diagnostics=prosody.summary();emit()
            val rendered=backend.render(pack,voice,prosody,c);c.check()
            target.save(side,p.identityId,p,text,s.seed,s.sampling(),rendered.pcm,c,plan.intent.name,prosody.pauseMs)
            measure?.summary()?.let { target.performance(side,it,c) }
            diagnostics="Génération réussie ; EOS ${rendered.metrics.all { it.eosReached }} ; rendu ${rendered.wallMs.toLong()} ms ; phrases ${rendered.metrics.size}\n"+prosody.summary()+"\n"+PcmStatistics.measure(rendered.pcm).summary();message="Génération réussie. Audio conservé ; lecture en préparation.";emit()
            play(rendered.pcm,c);audition=(slot ?: "main") to target.metadata(side)
            "Lecture terminée. Réécouter est disponible sans recalcul du modèle."
        }
    }
    fun generateStyleComparison(slot: String) {
        require(slot in setOf("A","B"))
        val p=library.activeConfirmed();require(hasModel()) { "Importer le pack FP32 existant." }
        val settings=state.settings
        // Public fixture only: no implicit persistence of the user's conversation or persona settings.
        val text="Bonjour à vous.\nLe test est prêt."
        val policy=ExpressionPolicy.plan(CoreVoiceRequestV1(text,p.identityId,allowSentencePauses=slot=="B"),
            ExpressionVoiceBinding.from(p),settings.sampling(),settings.seed)
        val style=if(slot=="A") ExpressionStyleProfiles.neutral() else ExpressionStyleProfiles.athena()
        run("Comparaison de styles $slot : pauses logicielles, même voix…",true) { c ->
            require(library.activeConfirmed()==p)
            val target=styleComparisons(p.identityId)
            if(slot=="B") target.requireB(p,text,settings.seed,settings.sampling(),c)
            styleSelection=style;lastStylePlan=null;styleTimeline=null
            val output=object: OutputAudioPort {
                override val focusController=AudioFocusController()
                override fun play(pcm: FloatArray,cancel: CancellationToken): AudioReceipt {
                    target.save(slot,p,requireNotNull(lastStylePlan),pcm,cancel)
                    return try { this@StudioController.play(pcm,cancel,focusController) }
                        catch(_: PlaybackFailure) { throw SpeechRejected(SpeechRefusal.AUDIO_UNAVAILABLE) }
                }
                override fun requestStop() { player.get()?.stop() }
                override fun close() {}
            }
            val session=sdkSessions.open(output);sdkSession.set(session)
            try {
                when(val outcome=session.speak(SpeechQueue(listOf(SpeechRequest(java.util.UUID.randomUUID().toString(),policy))),c)) {
                    is SpeechOutcome.Refused -> throw SpeechRejected(outcome.reason)
                    is SpeechOutcome.Completed -> {
                        val timeline=requireNotNull(styleTimeline)
                        diagnostics="Style ${style.id}, ${timeline.cues.size} marqueurs relatifs PCM, ${timeline.samples} échantillons. Pas de phonèmes, d’émotion native ni d’ancre d’horloge AVATAR."
                        "Style $slot présenté. PCM32 privé conservé pour la comparaison manuelle ; aucune qualité vocale attribuée automatiquement."
                    }
                }
            } finally { session.close();sdkSession.compareAndSet(session,null);styleSelection=null;lastStylePlan=null }
        }
    }
    fun replayStyle(slot: String) {
        val p=library.activeConfirmed()
        run("Réécoute du style $slot sans modèle…") { c ->
            val pcm=styleComparisons(p.identityId).load(slot,vault,c)
            try { play(pcm,c);"Style $slot présenté ; évaluation manuelle uniquement." } finally { pcm.fill(0f) }
        }
    }
    fun replay(slot: String="main") {
        val p=library.activeConfirmed();val target=if(slot=="main") single(p.identityId) else expressions(p.identityId);val side=if(slot=="main") "A" else slot
        run("Réécoute locale sans modèle…") { c ->
            val meta=target.metadata(side)
            diagnostics="Réécoute ${meta.getString("intent")} ; profil ${meta.getString("profileId").take(8)} ; pause ${meta.getInt("pauseMs")} ms\nMesures de génération avant lecture :\n${meta.optString("generationMetrics","indisponibles")}"
            play(target.load(side,vault,c),c);audition=slot to meta;"Réécoute terminée, même profil vérifié."
        }
    }
    fun compareProfile(slot: String,pid: String,confirmed: Boolean) {
        require(confirmed);val current=library.activeConfirmed();require(hasModel()) { "Importer le pack FP32 existant." }
        val i=vault.list().single { it.id==current.identityId };val p=i.profiles.single { it.id==pid };require(p.status==ProfileStatus.VALID)
        val s=state.settings;val text=state.text
        run("Comparaison de profils : ${slot}…",true) { c ->
            val target=profiles(i.id);if(slot=="B") target.requireB(i.id,p,target.context(i.id,text,s.seed,s.sampling()),c)
            val voice=vault.binding().resolve(i.id,p.id,c)
            val neutralState=state.copy(settings=s.copy(pauses=false,intent=VoiceExpressionIntent.NEUTRAL),text=text)
            val pcm=backend.render(pack,voice,backend.prepare(pack,StudioRendering.plan(neutralState,p)),c).pcm
            target.save(slot,i.id,p,text,s.seed,s.sampling(),pcm,c)
            play(pcm,c);audition=("profile$slot") to target.metadata(slot);"Profil $slot écouté, voix active inchangée. Aucun meilleur profil choisi automatiquement."
        }
    }
    fun replayProfile(slot: String) { val id=library.activeConfirmed().identityId;run("Réécoute du profil $slot…") { c -> val t=profiles(id);play(t.load(slot,vault,c),c);audition=("profile$slot") to t.metadata(slot);"Profil $slot réécouté sans modèle." } }
    private fun play(pcm: FloatArray,c: CancellationToken,focus: AudioFocusController?=null): AudioReceipt {
        check(!speedQuarantined) { "Fermeture native inconnue : redémarrer avant lecture." }
        if(sdkSessions.isQuarantined) throw SpeechRejected(SpeechRefusal.ENGINE_FAILED)
        audition=null;val stats=PcmStatistics.measure(pcm);require(stats.samples>0 && stats.nonFinite==0)
        val prefix=diagnostics
        val audio=PcmPlayer(context,c,{ detail -> diagnostics=prefix+"\n"+stats.summary()+"\n"+detail;emit() },{focus?.granted();message="Lecture commencée — progression mesurée.";emit()},speed=speedTrace)
        player.set(audio);try { audio.write(pcm);audio.drain();check(audio.completed);return AudioReceipt(pcm.size,audio.underruns) }
        catch(t: PlaybackFailure) { if(t.reason=="FOCUS_PERDU") focus?.lost();throw t }
        finally { audio.close();player.compareAndSet(audio,null) }
    }
    fun testSignal() { run("Test synthétique du lecteur…") { c -> play(FloatArray(24000) { (kotlin.math.sin(it*2*kotlin.math.PI*440/24000)*.1).toFloat() },c);"Signal de test terminé ; vérifier son audibilité réelle." } }
    fun note(scores: List<Int>,heard: Boolean) {
        val (slot,m)=requireNotNull(audition) { "Écouter complètement un audio avant de le noter." };val id=VoiceIdentityId(m.getString("identityId"))
        run("Notes privées…") { c ->
            if(slot.startsWith("profile")) { val side=slot.takeLast(1);val t=profiles(id);t.load(side,vault,c);require(t.metadata(side).getString("pcmHash")==m.getString("pcmHash"));vault.noteProfile(id,m.getString("profileId"),scores,heard,c,m) }
            else { val side=if(slot=="main") "A" else slot;val t=if(slot=="main") single(id) else expressions(id);t.note(side,vault,scores,heard,m,c) }
            "Notes d’écoute conservées localement ; aucune qualité inventée."
        }
    }
    fun removeProfile(id: VoiceIdentityId,pid: String) { run("Suppression du seul profil confirmé…") { c -> expressions(id).deleteProfile(id,pid,c);single(id).deleteProfile(id,pid,c);profiles(id).deleteProfile(id,pid,c);styleComparisons(id).deleteProfile(id,pid,c);library.choice.forget(id,pid);vault.removeProfile(id,pid,true,c);audition=null;"Profil supprimé dans VOICE-04 seulement." } }
    fun clearAudio(id: VoiceIdentityId) { run("Effacement des seules réécoutes de cette voix…") { expressions(id).delete();single(id).delete();profiles(id).delete();styleComparisons(id).delete();audition=null;"Audios effacés ; profils et références conservés." } }
    fun removeIdentity(id: VoiceIdentityId) { run("Suppression de cette voix VOICE-04…") { c -> check(audioRoot(id).deleteRecursively());library.choice.forget(id);vault.removeIdentity(id,true,c);audition=null;"Voix supprimée de VOICE-04 seulement. Les anciennes applications restent intactes." } }
}
