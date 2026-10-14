package com.jarvis.voice.voiceprototype

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
        fun field(hintText: String, initial: String) = EditText(this).apply { hint=hintText; setText(initial); layout.addView(this); controls.add(this) }
        label("JARVIS VOIX 005E — EXPÉRIMENTAL").textSize=24f
        label("Ressemblance, intelligibilité et performances sur votre Xiaomi à évaluer. Synthèse artificielle, pas un véritable enregistrement.")
        label("Application indépendante : Pocket Probe 005D reste installé et conserve ses données. Tout calcul est local ; aucun envoi réseau.")
        button("1. Importer le pack privé FP32 (.zip)") {
            android.app.AlertDialog.Builder(this).setTitle("Pack FP32 expérimental")
                .setMessage("Utilisez le pack officiel préparé dans votre espace privé, transféré par câble ou stockage local. Les poids nécessitent attribution et respect des conditions Kyutai : consentement explicite et légal, aucun usage trompeur ou illicite. Le pack INT8 de 005D n'est pas accepté.")
                .setPositiveButton("J'accepte et je sélectionne") { _, _ -> select("application/zip", 200) }.setNegativeButton("Annuler",null).show()
        }
        start=field("Début de l'extrait, secondes", "0")
        seconds=field("Durée de l'extrait, 5 à 15 secondes", "10")
        button("2. Importer ma référence locale (MP3 ou WAV mono 24 kHz)") { select("audio/*", 201) }
        consent=CheckBox(this).apply { text="C'est ma voix, ou j'ai son consentement explicite et légal. J'accepte le statut expérimental."; layout.addView(this); controls.add(this) }
        button("3. Créer / remplacer mon vrai profil vocal") { createProfile() }
        button("Écouter mon extrait privé") { preview() }
        button("Supprimer mon profil et ma référence") { task("Suppression privée",false) { repository.delete(); check(!excerpt.exists() || excerpt.delete()); ui { choices.check(publicVoice.id) }; "Profil et référence supprimés." } }
        choices=RadioGroup(this).apply { layout.addView(this) }
        publicVoice=RadioButton(this).apply { id=View.generateViewId(); text="Référence publique Alba — recalculée FP32"; choices.addView(this) }
        personalVoice=RadioButton(this).apply { id=View.generateViewId(); text="Mon profil personnel — EXPÉRIMENTAL"; choices.addView(this) }
        choices.check(if (getPreferences(0).getBoolean("personal",false) && repository.exists()) personalVoice.id else publicVoice.id)
        choices.setOnCheckedChangeListener { _, id -> getPreferences(0).edit().putBoolean("personal",id==personalVoice.id).apply() }
        phrase=field("Nouvelle phrase française (courte)",state?.getString("text") ?: "Bonjour, cette nouvelle phrase est générée entièrement hors ligne.").apply { filters=arrayOf(InputFilter.LengthFilter(300)) }
        seed=field("Graine aléatoire, par exemple 42", "42")
        label("CPU : 1 thread. La phrase entière est générée avant le lancement de la lecture. Aucun remplacement automatique de voix.")
        button("4. Générer puis écouter la voix sélectionnée") { speak() }
        stop=Button(this).apply { text="ARRÊT"; layout.addView(this); setOnClickListener { requestStop() } }
        progress=ProgressBar(this).apply { visibility=View.GONE; layout.addView(this) }
        status=label(if(supported) "Importer le pack FP32 privé pour commencer." else "Ce prototype nécessite Android ARM64.")
        metrics=label(DeviceMetrics.summary(this)).apply { setTextIsSelectable(true) }
        button("Origine, licences et limites") { android.app.AlertDialog.Builder(this).setTitle("Prototype 005E")
            .setMessage(assets.open("THIRD_PARTY_NOTICES.txt").bufferedReader().use { it.readText() }).setPositiveButton("Fermer",null).show() }
        setContentView(ScrollView(this).apply { fitsSystemWindows=true; addView(layout) })
        refresh()
    }
    private fun select(type: String, code: Int) = startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { this.type=type; addCategory(Intent.CATEGORY_OPENABLE) },code)
    private fun ui(action: () -> Unit) { main.post { if(!destroyed) action() } }
    private fun refresh() {
        controls.forEach { it.isEnabled=!busy && supported }
        publicVoice.isEnabled=!busy && supported
        personalVoice.isEnabled=!busy && supported && repository.exists()
        progress.visibility=if(busy) View.VISIBLE else View.GONE
        stop.isEnabled=busy
    }
    private fun requestStop() { stopAt=SystemClock.elapsedRealtime(); token?.cancel(); player.get()?.stop(); if(!destroyed) status.text="Arrêt demandé : audio interrompu ; calcul ou chargement natif en cours d'arrêt." }
    private fun requireMemory() {
        val memory=ActivityManager.MemoryInfo(); (getSystemService(ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
        require(!memory.lowMemory && memory.availMem>=2048L*1048576) { "FP32 : au moins 2 Gio de RAM disponible requis pour ce prototype. Fermer les autres applications." }
    }
    private fun task(title: String, models: Boolean, operation: (CancellationToken) -> String) {
        if(busy) return
        if(DeviceMetrics.isThermallySevere(this)) { status.text="Téléphone trop chaud : attendre son refroidissement."; return }
        if(models) try { requireMemory() } catch(error: Exception) { status.text=error.message; return }
        val current=CancellationToken(); token=current; stopAt=0; busy=true; status.text=title; refresh()
        worker.execute {
            val measure=OperationMetrics(this); val thermal=java.util.concurrent.atomic.AtomicBoolean(false)
            measure.start { thermal.set(true); current.cancel(); player.get()?.stop() }
            try {
                current.check(); val result=operation(current); current.check()
                ui { status.text=result; metrics.text=measure.summary() }
            } catch(error: Throwable) {
                val stopped=if(stopAt!=0L) "\nArrêt du calcul observé après ${SystemClock.elapsedRealtime()-stopAt} ms." else ""
                ui { status.text=if(current.isCancelled()) (if(thermal.get()) "Arrêt thermique ou mémoire : laisser refroidir et libérer de la RAM." else "Opération annulée.") + stopped else "Échec expérimental : ${error.javaClass.simpleName} — ${error.message.orEmpty().take(250)}"; metrics.text=measure.summary() }
            } finally { measure.close(); ui { busy=false; token=null; refresh() } }
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
    private fun withPlayer(cancel: CancellationToken, action: (PcmPlayer) -> Unit) {
        val audio=PcmPlayer(cancel); player.set(audio)
        try { action(audio) } finally { audio.close(); player.compareAndSet(audio,null) }
    }
    private fun preview() = task("Lecture de l'extrait privé…",false) { cancel -> withPlayer(cancel) { audio -> audio.write(PrivateWav.read(excerpt,cancelled=cancel::isCancelled)); audio.drain() }; "Extrait privé écouté." }
    private fun speak() {
        val text=phrase.text.toString().trim(); val personal=choices.checkedRadioButtonId==personalVoice.id
        val randomSeed=seed.text.toString().toLongOrNull()
        if(text.isEmpty() || randomSeed==null) { status.text="Saisir une courte phrase et une graine entière."; return }
        task("Chargement FP32, génération complète puis lecture…",true) { cancel ->
            val voice=if(personal) repository.load() else PersonalVoiceCodec.read(File(pack,"public-alba-fp32.bin"))
            val chunks=mutableListOf<FloatArray>(); val start=SystemClock.elapsedRealtime()
            var peak=0f; var clipped=0L; var count=0L
            val result=ExperimentalEngine(pack,voice,loadCancel=cancel).use { engine -> engine.generate(text,cancel,randomSeed,wholeSentence=true) { audio ->
                audio.forEach { value -> peak=maxOf(peak,kotlin.math.abs(value)); if(kotlin.math.abs(value)>=1f) clipped++; count++ }
                chunks.add(audio)
            } }
            cancel.check(); val pcm=FloatArray(chunks.sumOf { it.size }); var offset=0
            chunks.forEach { it.copyInto(pcm,offset); offset+=it.size }; chunks.clear()
            val generated=SystemClock.elapsedRealtime()-start
            var underruns=0
            withPlayer(cancel) { audio -> ui { status.text="Génération complète : lecture différée…" }; audio.write(pcm); audio.drain(); underruns=audio.underruns }
            "Synthèse EXPÉRIMENTALE terminée (${if(personal) "profil personnel" else "référence publique FP32"}).\nChargement + génération : $generated ms ; premier PCM calculé : ${result.firstPcmMs.toInt()} ms ; audio : ${"%.2f".format(result.audioSeconds)} s ; RTF CPU : ${"%.2f".format(result.rtf)}.\nEOS atteint : ${result.eosReached}. Pic brut : ${"%.3f".format(peak)} ; écrêtage : ${"%.2f".format(100.0*clipped/maxOf(count,1))} %. AudioTrack underruns bruts : $underruns.\nComparer intelligibilité et ressemblance ; aucun résultat perceptif automatique déclaré validé."
        }
    }
    override fun onSaveInstanceState(out: Bundle) { out.putString("text",phrase.text.toString()); super.onSaveInstanceState(out) }
    override fun onStop() { if(busy) requestStop(); super.onStop() }
    override fun onDestroy() { destroyed=true; token?.cancel(); player.get()?.stop(); worker.shutdown(); super.onDestroy() }
}
