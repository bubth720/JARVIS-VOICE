package com.jarvis.voice.voicequality

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputFilter
import android.widget.*
import android.view.View
import com.jarvis.voice.pocket.*
import java.io.File
import kotlin.math.roundToInt
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/** Independent experimental application; all personal data stays in noBackupFilesDir. */
class MainActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val player = AtomicReference<PcmPlayer?>()
    private var token: CancellationToken? = null
    private var busy = false
    @Volatile private var destroyed = false
    @Volatile private var stopAt = 0L
    private lateinit var status: TextView
    private lateinit var metrics: TextView
    private lateinit var generationStatus: TextView
    private lateinit var playbackStatus: TextView
    private lateinit var audioDiagnostics: TextView
    private lateinit var replay: Button
    private lateinit var stepsB: Spinner
    private lateinit var temperatureB: Spinner
    private lateinit var eosB: Spinner
    private lateinit var tailB: Spinner
    private lateinit var outputFormat: Spinner
    private lateinit var measurementsA: TextView
    private lateinit var measurementsB: TextView
    private lateinit var heard: CheckBox
    private lateinit var ratingCaption: TextView
    private lateinit var naturalRating: Spinner
    private lateinit var metalRating: Spinner
    private lateinit var intelligibilityRating: Spinner
    private lateinit var prosodyRating: Spinner
    private var lastAudition: org.json.JSONObject?=null
    private val abStore get()=QualityABStore(File(noBackupFilesDir,"ab-comparison"))
    private var lastPcmSummary="PCM : aucune génération mesurée"
    private var lastTrackSummary="AudioTrack : pas encore créé"
    private var generationState="Génération : non lancée"
    private val pcmStore get()=PrivatePcmStore(File(noBackupFilesDir,"last-pcm"))
    private val diagnosticsFile get()=File(noBackupFilesDir,"last-playback-diagnostics.txt")
    private lateinit var phrase: EditText
    private lateinit var start: EditText
    private lateinit var seconds: EditText
    private lateinit var seed: EditText
    private lateinit var consent: CheckBox
    private lateinit var choices: RadioGroup
    private lateinit var publicVoice: RadioButton
    private lateinit var personalVoice: RadioButton
    private lateinit var progress: ProgressBar
    private lateinit var stop: Button
    private val controls = mutableListOf<View>()
    private val modelRoot get() = File(noBackupFilesDir, "models")
    private val pack get() = File(modelRoot, "fp32")
    private val excerpt get() = File(noBackupFilesDir, "reference.wav")
    private val repository get() = PersonalVoiceRepository(File(noBackupFilesDir, "profiles"))
    private val supported get() = "arm64-v8a" in Build.SUPPORTED_ABIS

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,24,24,24) }
        fun label(value: String) = TextView(this).apply { text=value; textSize=16f; layout.addView(this) }
        fun button(value: String, action: () -> Unit) = Button(this).apply { text=value; layout.addView(this); controls.add(this); setOnClickListener { action() } }
        fun spinner(title: String,options: List<String>,selected: Int): Spinner {
            label(title)
            return Spinner(this).apply { adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,options);setSelection(selected);layout.addView(this);controls.add(this) }
        }
        fun field(hintText: String, initial: String) = EditText(this).apply { hint=hintText; setText(initial); layout.addView(this); controls.add(this) }
        label("JARVIS VOIX 005G — EXPÉRIMENTAL").textSize=24f
        label("Ressemblance, intelligibilité et performances sur votre Xiaomi à évaluer. Synthèse artificielle, pas un véritable enregistrement.")
        label("Application indépendante : 005F stable et Pocket Probe 005D restent installées et conservent leurs données. Tout calcul est local ; aucun envoi réseau.")
        button("1. Importer le pack privé FP32 (.zip)") {
            android.app.AlertDialog.Builder(this).setTitle("Pack FP32 expérimental")
                .setMessage("Utilisez le pack officiel préparé dans votre espace privé, téléchargé puis sélectionné depuis le stockage local. Les poids nécessitent attribution et respect des conditions Kyutai : consentement explicite et légal, aucun usage trompeur ou illicite. Le pack INT8 de 005D n'est pas accepté.")
                .setPositiveButton("J'accepte et je sélectionne") { _, _ -> select("application/zip", 200) }.setNegativeButton("Annuler",null).show()
        }
        start=field("Début de l'extrait, secondes", "0")
        seconds=field("Durée de l'extrait, 5 à 15 secondes", "10")
        button("2. Importer ma référence locale (MP3 ou WAV mono 24 kHz)") { select("audio/*", 201) }
        consent=CheckBox(this).apply { text="C'est ma voix, ou j'ai son consentement explicite et légal. J'accepte le statut expérimental."; layout.addView(this); controls.add(this) }
        button("3. Créer / remplacer mon vrai profil vocal") { createProfile() }
        button("Importer un profil FP32 déjà créé — sans réencodage") {
            if(!consent.isChecked) status.text="Confirmer le consentement avant d'importer un profil local."
            else select("application/octet-stream",202)
        }
        button("Écouter mon extrait privé") { preview() }
        button("Supprimer mon profil, référence et audios A/B") { task("Suppression privée",false) { repository.delete(); pcmStore.delete(); abStore.delete(); File(noBackupFilesDir,"human-ratings.json").delete(); diagnosticsFile.delete(); check(!excerpt.exists() || excerpt.delete()); ui { choices.check(publicVoice.id) }; "Profil et référence supprimés." } }
        choices=RadioGroup(this).apply { layout.addView(this) }
        publicVoice=RadioButton(this).apply { id=View.generateViewId(); text="Référence publique Alba — recalculée FP32"; choices.addView(this) }
        personalVoice=RadioButton(this).apply { id=View.generateViewId(); text="Mon profil personnel — EXPÉRIMENTAL"; choices.addView(this) }
        choices.check(if (getPreferences(0).getBoolean("personal",false) && repository.exists()) personalVoice.id else publicVoice.id)
        choices.setOnCheckedChangeListener { _, id -> getPreferences(0).edit().putBoolean("personal",id==personalVoice.id).apply() }
        phrase=field("Nouvelle phrase française (courte)",state?.getString("text") ?: "Bonjour, cette nouvelle phrase est générée entièrement hors ligne.").apply { filters=arrayOf(InputFilter.LengthFilter(300)) }
        seed=field("Graine aléatoire, par exemple 42", "42")
        label("CPU : 1 thread. La phrase entière est générée avant le lancement de la lecture. Aucun remplacement automatique de voix.")
        label("Comparaison A/B : A reproduit 005F (LSD 1 étape, température 0,3). B est expérimental. Texte, voix et graine doivent rester identiques.")
        stepsB=spinner("B — étapes LSD",listOf("1","2","3","5"),2)
        temperatureB=spinner("B — température",listOf("0.2","0.3","0.5"),1)
        eosB=spinner("B — seuil EOS",listOf("-5","-4","-3"),1)
        tailB=spinner("B — fin de phrase",listOf("Officielle selon texte","3 frames","5 frames"),0)
        outputFormat=spinner("Sortie audio — mêmes samples, aucun filtre",listOf("PCM16 — comme 005F","FLOAT32 — comparaison expérimentale"),0)
        button("Générer A — réglages 005F, puis écouter") { speak("A") }
        button("Générer B — réglages expérimentaux, puis écouter") { speak("B") }
        replay=button("Réécouter A — sans modèle") { replayVariant("A") }
        button("Réécouter B — sans modèle") { replayVariant("B") }
        button("Effacer uniquement les audios A/B") { task("Suppression A/B",false) { abStore.delete();"Audios A/B supprimés ; profil et pack conservés." } }
        button("Analyser la référence locale") { task("Analyse locale de l'énergie et des silences…",false) {
            ReferenceSelection.assess(PrivateWav.read(excerpt)).summary()
        } }
        button("Préparer une portion contiguë de 5 s — profil conservé") { task("Sélection locale sans filtre ni gain…",false) { cancel ->
            val pcm=PrivateWav.read(excerpt,cancelled=cancel::isCancelled);val region=ReferenceSelection.suggestFiveSeconds(pcm)
            val selected=pcm.copyOfRange(region.first,region.last+1)
            val pending=File(noBackupFilesDir,"reference.selection.pending")
            try { ReferenceAudio.writeWav(pending,selected,cancel::isCancelled);cancel.check();check(pending.renameTo(excerpt)) } finally { pending.delete() }
            "Extrait contigu proposé depuis ${region.first/24000.0} s ; profil actuel inchangé. Écouter l'extrait avant toute création de nouveau profil.\n"+ReferenceSelection.assess(selected).summary()
        } }
        button("Tester le lecteur : signal synthétique 1 seconde") { testAudio() }
        label("Évaluation humaine locale — après écoute, aucun score automatique")
        ratingCaption=label("Écouter A ou B avant de noter.")
        naturalRating=spinner("Naturel : 0 très robotique → 5 naturel",listOf("Non noté","0","1","2","3","4","5"),0)
        metalRating=spinner("Artefacts métalliques : 0 aucun → 5 prononcés",listOf("Non noté","0","1","2","3","4","5"),0)
        intelligibilityRating=spinner("Intelligibilité : 0 faible → 5 excellente",listOf("Non noté","0","1","2","3","4","5"),0)
        prosodyRating=spinner("Prosodie : 0 monotone → 5 naturelle",listOf("Non noté","0","1","2","3","4","5"),0)
        heard=CheckBox(this).apply { text="J'ai réellement entendu cet audio sur ce téléphone.";layout.addView(this);controls.add(this) }
        button("Enregistrer cette évaluation uniquement sur le téléphone") { recordRating() }
        button("Effacer mes évaluations locales") { task("Suppression des évaluations",false) {
            val file=File(noBackupFilesDir,"human-ratings.json");check(!file.exists() || file.delete());"Évaluations locales supprimées."
        } }
        stop=Button(this).apply { text="ARRÊT"; layout.addView(this); setOnClickListener { requestStop() } }
        progress=ProgressBar(this).apply { visibility=View.GONE; layout.addView(this) }
        status=label(if(supported) "Importer le pack FP32 privé pour commencer." else "Ce prototype nécessite Android ARM64.")
        metrics=label(DeviceMetrics.summary(this)).apply { setTextIsSelectable(true) }
        measurementsA=label("A : pas encore généré").apply { setTextIsSelectable(true) }
        measurementsB=label("B : pas encore généré").apply { setTextIsSelectable(true) }
        generationStatus=label(generationState)
        playbackStatus=label("Lecture : non lancée")
        audioDiagnostics=label(if(diagnosticsFile.isFile && diagnosticsFile.length()<=8192) diagnosticsFile.readText() else lastPcmSummary).apply { setTextIsSelectable(true) }
        button("Origine, licences et limites") { android.app.AlertDialog.Builder(this).setTitle("Prototype 005G")
            .setMessage(assets.open("THIRD_PARTY_NOTICES.txt").bufferedReader().use { it.readText() }).setPositiveButton("Fermer",null).show() }
        setContentView(ScrollView(this).apply { fitsSystemWindows=true; addView(layout) })
        refresh()
    }
    private fun select(type: String, code: Int) = startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { this.type=type; addCategory(Intent.CATEGORY_OPENABLE) },code)
    private fun ui(action: () -> Unit) { main.post { if(!destroyed) action() } }
    private fun refresh() {
        controls.forEach { it.isEnabled=!busy && supported }
        replay.isEnabled=!busy && supported && abStore.exists("A")
        publicVoice.isEnabled=!busy && supported
        personalVoice.isEnabled=!busy && supported && repository.exists()
        progress.visibility=if(busy) View.VISIBLE else View.GONE
        stop.isEnabled=busy
    }
    private fun requestStop() { if(::playbackStatus.isInitialized) playbackStatus.text="Lecture : annulée (ARRÊT)"; stopAt=SystemClock.elapsedRealtime(); token?.cancel(); player.get()?.stop(); if(!destroyed) status.text="Arrêt demandé : audio interrompu ; calcul ou chargement natif en cours d'arrêt." }
    private fun requireMemory() {
        val memory=ActivityManager.MemoryInfo(); (getSystemService(ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
        require(!memory.lowMemory && memory.availMem>=2048L*1048576) { "FP32 : au moins 2 Gio de RAM disponible requis pour ce prototype. Fermer les autres applications." }
    }
    private fun task(title: String, models: Boolean, tag: String?=null, operation: (CancellationToken) -> String) {
        if(busy) return
        if(DeviceMetrics.isThermallySevere(this)) { status.text="Téléphone trop chaud : attendre son refroidissement."; return }
        if(models) try { requireMemory() } catch(error: Exception) { status.text=error.message; return }
        val current=CancellationToken(); token=current; stopAt=0; busy=true; status.text=title; refresh()
        worker.execute {
            val measure=OperationMetrics(this); val thermal=java.util.concurrent.atomic.AtomicBoolean(false)
            measure.start { thermal.set(true); current.cancel(); player.get()?.stop() }
            try {
                current.check(); val result=operation(current); current.check()
                ui { status.text=result; metrics.text=measure.summary();if(tag!=null) {
                    val label=if(tag=="A") measurementsA else measurementsB
                    label.text="$tag : $generationState\n$lastPcmSummary\n${measure.summary()}"
                    runCatching { File(noBackupFilesDir,"ab-$tag-metrics.txt").writeText(label.text.toString().take(8192)) }
                } }
            } catch(error: Throwable) {
                val stopped=if(stopAt!=0L) "\nArrêt du calcul observé après ${SystemClock.elapsedRealtime()-stopAt} ms." else ""
                ui { status.text=if(current.isCancelled()) (if(thermal.get()) "Arrêt thermique ou mémoire : laisser refroidir et libérer de la RAM." else "Opération annulée.") + stopped else "Échec expérimental : ${error.javaClass.simpleName} — ${error.message.orEmpty().take(250)}"; metrics.text=measure.summary() }
            } finally { measure.close(); ui {
                if(tag!=null) {
                    val label=if(tag=="A") measurementsA else measurementsB
                    label.text="$tag : $generationState\n$lastPcmSummary\n${measure.summary()}"
                    runCatching { File(noBackupFilesDir,"ab-$tag-metrics.txt").writeText(label.text.toString().take(8192)) }
                }
                busy=false;token=null;refresh()
            } }
        }
    }
    @Deprecated("Legacy activity result API retained for minSdk 26")
    override fun onActivityResult(code: Int, result: Int, data: Intent?) {
        super.onActivityResult(code,result,data)
        if(result!=RESULT_OK) return
        val uri=data?.data ?: return
        if(code==200) task("Import privé et contrôle SHA256 du pack FP32…",false) { cancel ->
            require(noBackupFilesDir.usableSpace>=1200L*1048576) { "Au moins 1,2 Gio de stockage disponible requis" }
            val source=try { contentResolver.openInputStream(uri) } catch(_: Exception) {
                throw FP32ImportException("DOCUMENT_INACCESSIBLE", "le fournisseur de fichiers ne permet pas la lecture ; télécharger le ZIP localement puis le sélectionner")
            } ?: throw FP32ImportException("DOCUMENT_INACCESSIBLE", "aucun flux fourni par le sélecteur")
            try { source.use { ExperimentalModelPack.install(it, modelRoot, cancel::isCancelled) } }
            catch(_: java.io.IOException) {
                throw FP32ImportException("LECTURE", "lecture du document ou stockage local indisponible")
            }
            "Pack FP32 vérifié. Prototype expérimental prêt à créer un profil local."
        }
        if(code==201) {
            val begin=start.text.toString().toIntOrNull(); val duration=seconds.text.toString().toIntOrNull()
            if(begin==null || duration==null || begin !in 0..175 || duration !in 5..15) { status.text="Début ≥0 et durée de 5 à 15 secondes requis."; return }
            importReference(uri,begin,duration)
        }
        if(code==202 && consent.isChecked) task("Import du profil FP32 existant, sans modèle ni réencodage…",false) { cancel ->
            val pending=File(noBackupFilesDir,"incoming-profile.private")
            try {
                requireNotNull(contentResolver.openInputStream(uri)).use { input -> pending.outputStream().use { out ->
                    val buffer=ByteArray(65536); var count=0L
                    while(true) { cancel.check(); val n=input.read(buffer); if(n<0) break; count+=n; require(count<=10000000) { "Profil limité à 10 Mo" }; out.write(buffer,0,n) }
                } }
                val profile=PersonalVoiceCodec.read(pending)
                repository.save(profile,true,cancel)
                ui { choices.check(personalVoice.id) }
                "Profil FP32 existant importé et vérifié (pack et 18 états). Aucun réencodage ; profil dans l’application source conservé."
            } finally { pending.delete() }
        }
    }
    private fun importReference(uri: Uri, begin: Int, duration: Int) = task("Import et extraction privés…",false) { cancel ->
        val incoming=File(noBackupFilesDir,"incoming.private"); val pending=File(noBackupFilesDir,"reference.pending")
        try {
            requireNotNull(contentResolver.openInputStream(uri)).use { source -> incoming.outputStream().use { out ->
                val bytes=ByteArray(65536); var total=0L
                while(true) { cancel.check(); val count=source.read(bytes); if(count<0) break; total+=count; require(total<=33554432) { "Référence limitée à 32 Mio" }; out.write(bytes,0,count) }
            } }
            val wav=incoming.inputStream().use { input -> ByteArray(4).also { input.read(it) }.toString(Charsets.US_ASCII)=="RIFF" }
            val pcm=if(wav) PrivateWav.read(incoming,begin*24000,duration*24000,cancel::isCancelled) else LocalMp3.decode(incoming,begin*1000L,duration*1000L,cancel)
            ReferenceAudio.writeWav(pending,pcm,cancel::isCancelled); PrivateWav.read(pending,cancelled=cancel::isCancelled)
            cancel.check(); check(pending.renameTo(excerpt))
            "Extrait WAV mono 24 kHz enregistré dans l'espace privé. ${ReferenceAudio.quality(pcm).warning} Le profil existant n'est pas modifié avant une nouvelle création."
        } finally { incoming.delete(); pending.delete() }
    }
    private fun createProfile() {
        if(!consent.isChecked) { status.text="Confirmer le consentement et le statut expérimental."; return }
        task("Encodage Mimi+BOS et création des 18 états FP32…",true) { cancel ->
            require(excerpt.isFile && pack.isDirectory) { "Importer le pack et l'extrait avant de créer le profil." }
            val pcm=PrivateWav.read(excerpt,cancelled=cancel::isCancelled)
            val profile=FP32VoiceEncoder(pack,cancel).use { it.create(pcm,cancel) }
            repository.save(profile,true,cancel)
            ui { choices.check(personalVoice.id) }
            "Vrai profil FP32 créé et conservé localement. Ressemblance vocale encore à évaluer sur votre appareil."
        }
    }
    private fun diagnostics() { ui { audioDiagnostics.text="$lastPcmSummary\n$lastTrackSummary" } }
    private fun withPlayer(cancel: CancellationToken, action: (PcmPlayer) -> Unit) {
        val audio=PcmPlayer(this,cancel,{ snapshot -> lastTrackSummary=snapshot; diagnostics() },{
            ui { playbackStatus.text="Lecture : commencée — progression mesurée" }
        },floatOutput=outputFormat.selectedItemPosition==1); player.set(audio)
        try { action(audio) } finally {
            runCatching { diagnosticsFile.writeText(("$generationState\n$lastPcmSummary\n${audio.lastSnapshot}").take(8192)) }
            audio.close(); player.compareAndSet(audio,null)
        }
    }
    private fun playPcm(pcm: FloatArray, origin: String, cancel: CancellationToken) {
        lastAudition=null
        ui { heard.isChecked=false;listOf(naturalRating,metalRating,intelligibilityRating,prosodyRating).forEach { it.setSelection(0) };ratingCaption.text="Écoute en cours ; aucune note validée automatiquement." }
        val measured=PcmStatistics.measure(pcm)
        var errorPower=0.0;var signalPower=0.0;var maxError=0.0
        for(value in pcm) if(value.isFinite()) {
            val clipped=value.coerceIn(-1f,1f)
            val quantized=(clipped*32767).roundToInt().toFloat()/32768
            val error=(quantized-clipped).toDouble();errorPower+=error*error;signalPower+=clipped.toDouble()*clipped;maxError=maxOf(maxError,kotlin.math.abs(error))
        }
        val snr=10*kotlin.math.log10(maxOf(signalPower,1e-30)/maxOf(errorPower,1e-30))
        lastPcmSummary="$origin\n${measured.summary()}\nPCM16 vs FLOAT écrêté : erreur max $maxError ; SNR ${"%.1f".format(snr)} dB. Aucun filtre artificiel."
        lastTrackSummary="AudioTrack : pas encore créé"
        diagnostics()
        ui { playbackStatus.text="Lecture : PCM mesuré, chargement complet…" }
        require(measured.samples>0 && measured.nonFinite==0) { "Lecture refusée : PCM vide ou non fini" }
        try {
            withPlayer(cancel) { audio ->
                audio.write(pcm)
                ui { playbackStatus.text="Lecture : démarrage demandé, attente de progression réelle…" }
                audio.drain()
                check(audio.completed)
            }
            ui { playbackStatus.text="Lecture : terminée — toutes les frames présentées" }
        } catch(error: Throwable) {
            ui { playbackStatus.text=when {
                cancel.isCancelled() -> "Lecture : annulée"
                error is PlaybackFailure && error.reason in setOf("DEMARRAGE_BLOQUE","COMPTEUR_STAGNANT","DELAI_GLOBAL_DEPASSE") -> "Lecture : délai dépassé [${error.reason}] — PCM conservé si génération complète"
                else -> "Lecture : échec AudioTrack — consulter les diagnostics"
            } }
            throw error
        }
    }
    private fun preview() = task("Lecture de l'extrait privé…",false) { cancel ->
        playPcm(PrivateWav.read(excerpt,cancelled=cancel::isCancelled),"Extrait privé",cancel)
        "Lecture de l'extrait terminée (cela ne valide pas la synthèse)."
    }
    private fun testAudio() = task("Test du lecteur indépendant du modèle…",false) { cancel ->
        val pcm=FloatArray(24000) { (kotlin.math.sin(it*2*kotlin.math.PI*440/24000)*0.1).toFloat() }
        playPcm(pcm,"Signal synthétique 440 Hz ; aucun modèle ni profil utilisé",cancel)
        "Lecture du signal synthétique terminée. Confirmer l'audibilité réelle sur le téléphone."
    }
    private fun replayVariant(slot: String) = task("Réécoute $slot sans modèle ni recalcul…",false) { cancel ->
        val info=abStore.metadata(slot)
        playPcm(abStore.loadComparable(slot,cancel),"$slot sauvegardé : ${info.getString("settings")} ; texte : ${info.getString("text")}",cancel)
        rememberAudition(slot)
        "Réécoute $slot terminée. Évaluer à l'oreille, sans conclusion automatique de qualité."
    }
    private fun speak(slot: String) {
        val text=phrase.text.toString().trim(); val personal=choices.checkedRadioButtonId==personalVoice.id
        val randomSeed=seed.text.toString().toLongOrNull()
        if(text.isEmpty() || randomSeed==null) { status.text="Saisir une courte phrase et une graine entière."; return }
        val settings=if(slot=="A") QualitySampling() else QualitySampling(steps=stepsB.selectedItem.toString().toInt(),temperature=temperatureB.selectedItem.toString().toDouble(),eosThreshold=eosB.selectedItem.toString().toDouble(),framesAfterEos=when(tailB.selectedItemPosition) { 1 -> 3;2 -> 5;else -> null })
        task("$slot : chargement FP32 et ${settings.label()}…",true,tag=slot) { cancel ->
            generationState="Génération : en cours"; lastPcmSummary="PCM $slot : en attente de génération complète"; ui { generationStatus.text=generationState; playbackStatus.text="Lecture : non lancée" }
            val chunks=mutableListOf<FloatArray>(); val began=SystemClock.elapsedRealtime(); var count=0
            var context=""
            val result=try {
                val voice=if(personal) repository.load() else PersonalVoiceCodec.read(File(pack,"public-alba-fp32.bin"))
                context=abStore.context(text,randomSeed,voice)
                if(slot=="B") abStore.requireSameAsA(context,cancel)
                QualityEngine(pack,voice,loadCancel=cancel,sampling=settings).use { engine -> engine.generate(text,cancel,randomSeed,wholeSentence=true) { audio ->
                    count+=audio.size; require(count<=480000) { "PCM limité à 20 secondes" }; chunks.add(audio)
                } }
            } catch(error: Throwable) {
                generationState=if(cancel.isCancelled()) "Génération : annulée" else "Génération : échec ; aucune nouvelle synthèse complète"
                ui { generationStatus.text=generationState }; throw error
            }
            cancel.check(); val pcm=FloatArray(count); var offset=0
            chunks.forEach { it.copyInto(pcm,offset); offset+=it.size }; chunks.clear()
            val generated=SystemClock.elapsedRealtime()-began
            lastPcmSummary=PcmStatistics.measure(pcm).summary(); diagnostics()
            generationState="Génération : réussie, EOS ${result.eosReached} ; ${if(personal) "profil personnel" else "Alba FP32"} ; chargement + génération $generated ms ; RTF ${"%.2f".format(result.rtf)}."
            ui { generationStatus.text=generationState }
            abStore.save(slot,context,text,randomSeed,settings,pcm,cancel)
            playPcm(pcm,"$slot : ${settings.label()} ; même profil/texte/graine contrôlés ; PCM32 privé conservé",cancel)
            rememberAudition(slot)
            "Génération réussie et lecture terminée. Comparer naturel, artefacts, intelligibilité et prosodie ; qualité des réglages 005G encore à évaluer sur Xiaomi."
        }
    }
    private fun rememberAudition(slot: String) {
        val meta=abStore.metadata(slot)
        lastAudition=org.json.JSONObject().put("slot",slot).put("comparison",meta).put("output",outputFormat.selectedItem.toString())
        ui { ratingCaption.text="Dernière écoute complète : $slot ; ${meta.getString("settings")} ; ${outputFormat.selectedItem}" }
    }
    private fun recordRating() {
        val audition=lastAudition
        val scores=listOf(naturalRating,metalRating,intelligibilityRating,prosodyRating)
        if(audition==null || !heard.isChecked || scores.any { it.selectedItemPosition==0 }) { status.text="Écouter A/B, confirmer l'audibilité réelle et renseigner les quatre notes.";return }
        val values=scores.map { it.selectedItem.toString().toInt() }
        task("Enregistrement local de l'évaluation…",false) { cancel ->
            val slot=audition.getString("slot")
            abStore.loadComparable(slot,cancel)
            require(abStore.metadata(slot).getString("pcmHash")==audition.getJSONObject("comparison").getString("pcmHash")) { "Audio remplacé depuis l'écoute : réécouter avant de noter." }
            val file=File(noBackupFilesDir,"human-ratings.json")
            require(!file.exists() || file.length()<=131072)
            val rows=if(file.exists()) org.json.JSONArray(file.readText()) else org.json.JSONArray()
            while(rows.length()>=50) rows.remove(0)
            rows.put(org.json.JSONObject(audition.toString()).put("timestampMs",System.currentTimeMillis())
                .put("naturalness",values[0]).put("metallicArtifacts",values[1]).put("intelligibility",values[2]).put("prosody",values[3]).put("heardOnDevice",true))
            val pending=File(noBackupFilesDir,"human-ratings.pending")
            try { val text=rows.toString();require(text.toByteArray().size<=131072);pending.writeText(text);cancel.check();check(pending.renameTo(file)) } finally { pending.delete() }
            ui { heard.isChecked=false;scores.forEach { it.setSelection(0) } }
            "Évaluation personnelle enregistrée localement (50 dernières maximum). Aucun jugement global de supériorité déclaré."
        }
    }
    override fun onSaveInstanceState(out: Bundle) { out.putString("text",phrase.text.toString()); super.onSaveInstanceState(out) }
    override fun onStop() { if(busy) requestStop(); super.onStop() }
    override fun onDestroy() { destroyed=true; token?.cancel(); player.get()?.stop(); worker.shutdown(); super.onDestroy() }
}
