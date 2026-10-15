package com.jarvis.voice.pocketprobe

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.widget.*
import android.view.View
import com.jarvis.voice.pocket.*
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

class MainActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val player = AtomicReference<PcmPlayer?>()
    private var engine: PocketEngine? = null // worker-thread ownership
    private var token: CancellationToken? = null
    private var busy = false
    private var androidSpeaking = false
    @Volatile private var destroyed = false
    @Volatile private var stopNs = 0L
    private lateinit var text: EditText
    private lateinit var status: TextView
    private lateinit var diagnostics: TextView
    private lateinit var fallbackInfo: TextView
    private lateinit var read: Button
    private lateinit var stop: Button
    private lateinit var import: Button
    private lateinit var economy: Switch
    private lateinit var streaming: Switch
    private lateinit var engines: RadioGroup
    private lateinit var androidChoice: RadioButton
    private lateinit var fallback: AndroidFallback
    private lateinit var mp3Import: Button
    private lateinit var extractReference: Button
    private lateinit var previewReference: Button
    private lateinit var deleteReference: Button
    private lateinit var referenceInfo: TextView
    private lateinit var excerptStart: EditText
    private lateinit var excerptDuration: EditText
    private lateinit var progress: ProgressBar
    private val references get() = ReferenceRepository(File(noBackupFilesDir, "voice-reference"))
    private val pack get() = File(filesDir, "models/fr")
    private val supported get() = "arm64-v8a" in Build.SUPPORTED_ABIS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        fun label(s: String) = TextView(this).apply { setText(s); textSize = 16f; layout.addView(this) }
        label("JARVIS Pocket Probe").textSize = 26f
        label("Diagnostic indépendant • français local • voix prédéfinie Alba")
        label("Votre APK JARVIS VOICE reste indépendant. Le clonage attend un encodeur compatible validé.")
        import = Button(this).apply { setText("Importer le pack français (.zip)"); layout.addView(this) }
        engines = RadioGroup(this).apply { layout.addView(this) }
        val pocket = RadioButton(this).apply { id = android.view.View.generateViewId(); setText("Pocket TTS français — CPU"); engines.addView(this) }
        androidChoice = RadioButton(this).apply { id = android.view.View.generateViewId(); setText("Android TTS — secours explicite"); engines.addView(this); isEnabled = false }
        engines.check(pocket.id)
        fallbackInfo = label("Initialisation du secours Android…")
        economy = Switch(this).apply { setText("Mode économique : 1 thread (sinon 2)"); isChecked = true; layout.addView(this) }
        streaming = Switch(this).apply { setText("Lecture progressive adaptative (expérimentale)"); isChecked = false; layout.addView(this) }
        label("Par défaut : phrase entièrement générée, puis lecture fluide. Aucun remplacement automatique.")
        text = EditText(this).apply {
            hint = "Saisir une courte phrase française"
            setText("Bonjour, je suis JARVIS. Cette voix fonctionne sur ce téléphone.")
            minLines = 3; maxLines = 6; filters = arrayOf(InputFilter.LengthFilter(300)); layout.addView(this)
        }
        read = Button(this).apply { setText("Lecture"); layout.addView(this) }
        stop = Button(this).apply { setText("Arrêt"); layout.addView(this) }
        status = label(if (pack.isDirectory) "Pack présent : il sera vérifié avant lecture." else "Importer le pack français avant de choisir Lecture.")
        diagnostics = label(DeviceMetrics.summary(this)).apply { setTextIsSelectable(true) }
        label("Voix Pocket")
        RadioGroup(this).apply {
            val alba = RadioButton(this@MainActivity).apply { id = android.view.View.generateViewId(); setText("Alba — disponible hors ligne") }
            addView(alba); check(alba.id)
            addView(RadioButton(this@MainActivity).apply { setText("Profil personnalisé — indisponible : encodeur non validé"); isEnabled = false })
            layout.addView(this)
        }
        referenceInfo = label(referenceSummary())
        mp3Import = Button(this).apply { setText("Importer un MP3 autorisé (local uniquement)"); layout.addView(this) }
        excerptStart = EditText(this).apply { hint = "Début de l'extrait, secondes"; inputType = android.text.InputType.TYPE_CLASS_NUMBER; setText(getPreferences(0).getString("excerptStart", "0")); layout.addView(this) }
        excerptDuration = EditText(this).apply { hint = "Durée, 5 à 15 secondes"; inputType = android.text.InputType.TYPE_CLASS_NUMBER; setText(getPreferences(0).getString("excerptDuration", "10")); layout.addView(this) }
        extractReference = Button(this).apply { setText("Convertir et analyser l'extrait WAV mono 24 kHz"); layout.addView(this) }
        previewReference = Button(this).apply { setText("Écouter l'extrait local"); layout.addView(this) }
        Button(this).apply { setText("Créer le profil vocal — encodeur non validé"); isEnabled = false; layout.addView(this) }
        label(CloningCapability.reason + ". L'extrait WAV n'est pas un profil vocal. Aucun envoi réseau.")
        deleteReference = Button(this).apply { setText("Supprimer ma référence et mon extrait privés"); layout.addView(this) }
        progress = ProgressBar(this).apply { isIndeterminate = true; visibility = android.view.View.GONE; layout.addView(this) }
        Button(this).apply {
            setText("Licences et origine du modèle"); layout.addView(this)
            setOnClickListener { android.app.AlertDialog.Builder(this@MainActivity).setTitle("Modèle et licences")
                .setMessage(assets.open("THIRD_PARTY_NOTICES.txt").bufferedReader().use { it.readText() })
                .setPositiveButton("Fermer", null).show() }
        }
        setContentView(ScrollView(this).apply {
            // Android 15 enforces edge-to-edge for target 35: keep controls outside system bars.
            fitsSystemWindows = true
            addView(layout)
        })
        text.setText(savedInstanceState?.getString("text") ?: text.text.toString())
        fallback = AndroidFallback(this, { ready, message ->
            if (!destroyed) { androidChoice.isEnabled = ready && !busy; fallbackInfo.text = message; refresh() }
        }, { if (!destroyed) { androidSpeaking = false; status.text = "Lecture Android terminée."; refresh() } })
        import.setOnClickListener {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "application/zip"; addCategory(Intent.CATEGORY_OPENABLE)
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
            }, 100)
        }
        mp3Import.setOnClickListener {
            android.app.AlertDialog.Builder(this).setTitle("Autorisation de la voix")
                .setMessage("Sélectionnez uniquement un MP3 dont la reproduction vocale est autorisée. Il sera copié dans l'espace privé du téléphone, sans serveur ni publication.")
                .setPositiveButton("Je confirme et je sélectionne") { _, _ ->
                    startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        type = "audio/mpeg"; addCategory(Intent.CATEGORY_OPENABLE)
                        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/mpeg", "audio/mp3", "application/octet-stream"))
                    }, 101)
                }.setNegativeButton("Annuler", null).show()
        }
        extractReference.setOnClickListener { makeExcerpt(false) }
        previewReference.setOnClickListener { makeExcerpt(true) }
        deleteReference.setOnClickListener {
            references.delete(); referenceInfo.text = referenceSummary(); status.text = "Référence privée supprimée. Aucun profil vocal n'avait été créé."; refresh()
        }
        engines.setOnCheckedChangeListener { _, _ -> refresh() }
        read.setOnClickListener { speak() }
        stop.setOnClickListener { requestStop() }
        refresh()
    }

    private fun refresh() {
        val android = engines.checkedRadioButtonId == androidChoice.id
        read.isEnabled = !busy && !androidSpeaking && if (android) fallback.ready else supported && pack.isDirectory
        import.isEnabled = !busy && !androidSpeaking && supported
        streaming.isEnabled = !busy && !androidSpeaking
        economy.isEnabled = !busy && !androidSpeaking && !android
        for (i in 0 until engines.childCount) engines.getChildAt(i).isEnabled = !busy && !androidSpeaking &&
            (engines.getChildAt(i) != androidChoice || fallback.ready)
        val unlocked = !busy && !androidSpeaking
        mp3Import.isEnabled = unlocked
        extractReference.isEnabled = unlocked && runCatching { references.current() != null }.getOrDefault(false)
        previewReference.isEnabled = extractReference.isEnabled
        deleteReference.isEnabled = unlocked
        excerptStart.isEnabled = unlocked; excerptDuration.isEnabled = unlocked
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        stop.isEnabled = busy || androidSpeaking
    }

    private fun speak() {
        val raw = text.text.toString().trim()
        if (raw.isEmpty()) { status.text = "Saisir un texte."; return }
        if (engines.checkedRadioButtonId == androidChoice.id) {
            androidSpeaking = fallback.speak(raw)
            status.text = if (androidSpeaking) "Lecture avec Android TTS (secours choisi)." else "Secours Android indisponible."
            diagnostics.text = "Mesures Pocket non applicables au secours Android.\n" + DeviceMetrics.summary(this)
            refresh(); return
        }
        if (DeviceMetrics.isThermallySevere(this)) { status.text = "Téléphone trop chaud : attendre ou choisir Android TTS."; return }
        val adaptive = streaming.isChecked
        val threads = if (economy.isChecked) 1 else 2
        val current = CancellationToken()
        token = current; stopNs = 0; busy = true
        val pressed = System.nanoTime()
        status.text = "Pocket TTS : vérification et chargement, puis génération…"
        refresh()
        worker.execute {
            val thermalPause = java.util.concurrent.atomic.AtomicBoolean(false)
            val memory = MemorySampler(this) { thermalPause.set(true); current.cancel() }
            val audio = PcmPlayer(current)
            player.set(audio)
            val delivery = PcmDelivery(current::isCancelled, adaptive, { pcm ->
                post { status.text = "Pocket TTS : lecture audio…" }; audio.write(pcm)
            }, audio::rebuffer)
            var firstMs = 0.0
            try {
                current.check()
                val cold = engine == null || engine?.threads != threads
                if (cold) { engine?.close(); engine = null; engine = PocketEngine(pack, threads) }
                current.check()
                val active = requireNotNull(engine)
                val metrics = active.generate(raw, current, wholeSentence = !adaptive) { pcm ->
                    if (DeviceMetrics.isThermallySevere(this)) { thermalPause.set(true); current.cancel(); current.check() }
                    if (firstMs == 0.0) firstMs = (System.nanoTime() - pressed) / 1e6
                    delivery.offer(pcm)
                }
                delivery.finish()
                post { status.text = "Génération terminée ; restitution audio…" }
                delivery.await(); audio.drain()
                val output = String.format(Locale.FRENCH,
                    "Moteur : Pocket TTS français, voix prédéfinie Alba\nCPU : %d thread(s)\nChargement : %s\nPremier PCM depuis Lecture : %.0f ms\nPremier PCM après chargement : %.0f ms\nLancement AudioTrack depuis Lecture : %.0f ms\nGénération totale hors dépôt PCM : %.0f ms\nAudio : %.2f s ; RTF : %.3f ; vitesse : %.2f×\nPSS maximal observé (250 ms) : %.1f Mio\nFin de phrase détectée : oui\n\n%s",
                    threads, if (cold) "%.0f ms".format(Locale.FRENCH, active.modelLoadMs) else "modèle réutilisé",
                    firstMs, metrics.firstPcmMs, (audio.launchNs - pressed) / 1e6,
                    metrics.computeMs, metrics.audioSeconds, metrics.rtf, metrics.speed, memory.peakPssMiB, DeviceMetrics.summary(this))
                val detail = "\nMode : ${if (adaptive) "progressif adaptatif" else "différé complet"}" +
                    "\nONNX main/flow/décodeur : %.0f / %.0f / %.0f ms".format(Locale.FRENCH, metrics.onnxMainMs, metrics.onnxFlowMs, metrics.onnxDecoderMs) +
                    "\nAppel ONNX maximal : %.0f ms ; intervalle PCM maximal : %.0f ms".format(Locale.FRENCH, metrics.maxOnnxMs, metrics.maxPcmGapMs) +
                    "\nSous-alimentations AudioTrack : ${audio.underruns} ; attentes de recharge : ${delivery.rebufferCount}" +
                    "\nBuffer matériel : ${audio.bufferFrames} échantillons ; file PCM max : ${delivery.peakSamples} ; réserve adaptative : ${delivery.requiredSamples}" +
                    "\nPremier avancement matériel depuis Lecture : %.0f ms (estimation, pas mesure acoustique)".format(Locale.FRENCH, (audio.firstFrameNs - pressed) / 1e6) +
                    "\nÉtat thermique max/final : ${memory.peakThermal}/${DeviceMetrics.thermal(this)} (0 normal, 1 léger, 2 modéré, ≥3 arrêt)"
                post { status.text = "Lecture Pocket terminée."; diagnostics.text = output + detail }
            } catch (t: Throwable) {
                val computeStoppedNs = System.nanoTime()
                post {
                    if (current.isCancelled()) {
                        val stopped = if (stopNs != 0L) "\nCalcul arrêté après %.0f ms.".format(Locale.FRENCH, (computeStoppedNs - stopNs) / 1e6) else ""
                        status.text = if (thermalPause.get()) "Pocket arrêté : état thermique sévère. Attendre ou choisir Android TTS." else "Pocket TTS arrêté.$stopped"
                    } else status.text = "Échec Pocket TTS : ${t.javaClass.simpleName} — ${t.message.orEmpty().take(350)}\nAucun remplacement automatique. Vous pouvez choisir le secours Android."
                }
            } finally {
                delivery.close(); audio.close(); player.compareAndSet(audio, null); memory.close()
                post { busy = false; token = null; refresh() }
            }
        }
    }

    private fun requestStop() {
        stopNs = System.nanoTime()
        token?.cancel()
        player.get()?.stop()
        fallback.stop(); androidSpeaking = false
        status.text = if (busy) "Arrêt demandé : audio coupé, annulation du calcul…" else "Lecture Android arrêtée."
        refresh()
    }

    @Deprecated("Activity result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        if (requestCode == 101) { data?.data?.let { importMp3(it) }; return }
        if (requestCode != 100) return
        val uri = data?.data ?: return
        val current = CancellationToken(); token = current; stopNs = 0; busy = true
        status.text = "Import du pack et vérification des empreintes…"; refresh()
        worker.execute {
            try {
                engine?.close(); engine = null
                val input = contentResolver.openInputStream(uri) ?: error("Impossible d'ouvrir le ZIP")
                ModelPack.install(input, File(filesDir, "models"), current::isCancelled)
                post { status.text = "Pack français vérifié. Prêt pour Lecture Pocket." }
            } catch (t: Throwable) {
                post { status.text = if (current.isCancelled()) "Import annulé." else "Import refusé : ${t.message.orEmpty().take(250)}" }
            } finally { post { busy = false; token = null; refresh() } }
        }
    }

    private fun referenceSummary(): String = runCatching {
        references.current()?.second?.let {
            "MP3 privé rechargé : ${it.durationMs / 1000} s, ${it.sampleRate} Hz, ${it.channels} canal(aux). Aucun profil vocal créé."
        } ?: "Aucune référence privée importée."
    }.getOrDefault("Référence locale invalide : supprimer puis réimporter.")

    private fun importMp3(uri: android.net.Uri) {
        val current = CancellationToken(); token = current; stopNs = 0; busy = true
        status.text = "Import privé et vérification locale du MP3…"; refresh()
        worker.execute {
            try {
                references.import(contentResolver.openInputStream(uri) ?: error("Fichier inaccessible"), current::isCancelled) { file ->
                    val info = LocalMp3.inspect(file)
                    LocalMp3.decode(file, 0, 5000, current) // reject malformed bitstreams before replacing prior reference
                    info
                }
                post { referenceInfo.text = referenceSummary(); status.text = "MP3 importé localement. Choisir un extrait propre, sans musique ni autres voix." }
            } catch (t: Throwable) {
                post { status.text = if (current.isCancelled()) "Import MP3 annulé." else {
                    val failure = t as? ReferenceFailure ?: ReferenceFailure("MP02", t.javaClass.simpleName)
                    diagnostics.text = failure.diagnostic()
                    "MP3 refusé (${failure.stage}) : contrôle local échoué. La référence précédente est conservée."
                } }
            } finally { post { busy = false; token = null; refresh() } }
        }
    }

    private fun makeExcerpt(preview: Boolean) {
        val start = excerptStart.text.toString().toLongOrNull()
        val duration = excerptDuration.text.toString().toLongOrNull()
        if (start == null || start !in 0..175 || duration == null || duration !in 5..15) {
            status.text = "Début entre 0 et 175 s ; durée entre 5 et 15 s."; return
        }
        getPreferences(0).edit().putString("excerptStart", start.toString()).putString("excerptDuration", duration.toString()).apply()
        val current = CancellationToken(); token = current; stopNs = 0; busy = true
        status.text = "Décodage MP3 et conversion locale de l'extrait…"; refresh()
        worker.execute {
            var audio: PcmPlayer? = null
            var stage = "MP02"
            val began = System.nanoTime()
            val thermalPause = java.util.concurrent.atomic.AtomicBoolean(false)
            val memory = MemorySampler(this) { thermalPause.set(true); current.cancel() }
            try {
                if (DeviceMetrics.isThermallySevere(this)) { thermalPause.set(true); current.cancel() }
                current.check()
                val source = requireNotNull(references.current()).first
                val pcm = LocalMp3.decode(source, start * 1000, duration * 1000, current)
                stage = "MP09"
                val quality = ReferenceAudio.quality(pcm)
                current.check()
                references.saveExcerpt(pcm, current::isCancelled) { stage = it }
                post { referenceInfo.text = referenceSummary() + "\nExtrait : %.2f s ; RMS %.1f dBFS ; écrêtage %.2f %% ; silence %.1f %%\n%s".format(Locale.FRENCH,
                    quality.seconds, quality.rmsDb, quality.clippedFraction * 100, quality.quietFraction * 100, quality.warning) }
                if (preview) {
                    stage = "MP12"
                    audio = PcmPlayer(current); player.set(audio)
                    post { status.text = "Écoute de l'extrait privé…" }
                    audio.write(pcm); audio.drain()
                }
                post { diagnostics.text = "Conversion : ${(System.nanoTime() - began) / 1_000_000} ms ; PSS max %.1f Mio ; thermique max ${memory.peakThermal}".format(Locale.FRENCH, memory.peakPssMiB) }
                post { status.text = "Extrait privé prêt. Le clonage reste indisponible tant que l'encodeur n'est pas validé." }
            } catch (t: Throwable) {
                post { status.text = if (current.isCancelled()) { if (thermalPause.get()) "Conversion arrêtée : téléphone trop chaud. Attendre le refroidissement." else "Traitement de l'extrait arrêté." } else {
                        val failure = t as? ReferenceFailure ?: ReferenceFailure(stage, t.javaClass.simpleName)
                        diagnostics.text = failure.diagnostic()
                        "Extrait refusé (${failure.stage}) : " + when (failure.stage) {
                            "MP01" -> "vérifier le début et la durée."
                            "MP08" -> "conversion 24 kHz impossible."
                            "MP09" -> "signal audio invalide."
                            "MP10", "MP11" -> "enregistrement local impossible ; ancien extrait conservé."
                            "MP12" -> "lecture audio indisponible ; extrait conservé."
                            else -> "décodage local impossible."
                        }
                    } }
            } finally {
                audio?.close(); player.set(null); memory.close()
                post { busy = false; token = null; refresh() }
            }
        }
    }

    private fun post(action: () -> Unit) { main.post { if (!destroyed) action() } }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("text", text.text.toString()); super.onSaveInstanceState(outState) }
    override fun onStop() {
        if (::fallback.isInitialized && (busy || androidSpeaking)) requestStop()
        worker.execute { engine?.close(); engine = null }
        super.onStop()
    }
    override fun onDestroy() {
        destroyed = true; token?.cancel(); player.get()?.stop()
        if (::fallback.isInitialized) fallback.close()
        worker.execute { engine?.close(); engine = null }; worker.shutdown()
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
