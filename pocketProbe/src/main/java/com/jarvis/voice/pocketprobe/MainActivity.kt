package com.jarvis.voice.pocketprobe

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.widget.*
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
    private lateinit var engines: RadioGroup
    private lateinit var androidChoice: RadioButton
    private lateinit var fallback: AndroidFallback
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
        label("Pas de clonage dans cette version. Votre APK JARVIS VOICE reste indépendant.")
        import = Button(this).apply { setText("Importer le pack français (.zip)"); layout.addView(this) }
        engines = RadioGroup(this).apply { layout.addView(this) }
        val pocket = RadioButton(this).apply { id = android.view.View.generateViewId(); setText("Pocket TTS français — CPU"); engines.addView(this) }
        androidChoice = RadioButton(this).apply { id = android.view.View.generateViewId(); setText("Android TTS — secours explicite"); engines.addView(this); isEnabled = false }
        engines.check(pocket.id)
        fallbackInfo = label("Initialisation du secours Android…")
        economy = Switch(this).apply { setText("Mode économique : 1 thread (sinon 2)"); isChecked = true; layout.addView(this) }
        text = EditText(this).apply {
            hint = "Saisir une courte phrase française"
            setText("Bonjour, je suis JARVIS. Cette voix fonctionne sur ce téléphone.")
            minLines = 3; maxLines = 6; filters = arrayOf(InputFilter.LengthFilter(300)); layout.addView(this)
        }
        read = Button(this).apply { setText("Lecture"); layout.addView(this) }
        stop = Button(this).apply { setText("Arrêt"); layout.addView(this) }
        status = label(if (pack.isDirectory) "Pack présent : il sera vérifié avant lecture." else "Importer le pack français avant de choisir Lecture.")
        diagnostics = label(DeviceMetrics.summary(this)).apply { setTextIsSelectable(true) }
        label("MP3 : architecture prévue, import et création de profil désactivés jusqu'à validation du moteur.")
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
        engines.setOnCheckedChangeListener { _, _ -> refresh() }
        read.setOnClickListener { speak() }
        stop.setOnClickListener { requestStop() }
        refresh()
    }

    private fun refresh() {
        val android = engines.checkedRadioButtonId == androidChoice.id
        read.isEnabled = !busy && !androidSpeaking && if (android) fallback.ready else supported && pack.isDirectory
        import.isEnabled = !busy && !androidSpeaking && supported
        economy.isEnabled = !busy && !androidSpeaking && !android
        for (i in 0 until engines.childCount) engines.getChildAt(i).isEnabled = !busy && !androidSpeaking &&
            (engines.getChildAt(i) != androidChoice || fallback.ready)
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
        val threads = if (economy.isChecked) 1 else 2
        val current = CancellationToken()
        token = current; stopNs = 0; busy = true
        val pressed = System.nanoTime()
        status.text = "Pocket TTS : vérification et chargement, puis génération…"
        refresh()
        worker.execute {
            val memory = MemorySampler()
            val audio = PcmPlayer(current)
            player.set(audio)
            var firstMs = 0.0
            var thermalPause = false
            try {
                current.check()
                val cold = engine == null || engine?.threads != threads
                if (cold) { engine?.close(); engine = null; engine = PocketEngine(pack, threads) }
                current.check()
                val active = requireNotNull(engine)
                val metrics = active.generate(raw, current) { pcm ->
                    if (DeviceMetrics.isThermallySevere(this)) { thermalPause = true; current.cancel(); current.check() }
                    if (firstMs == 0.0) firstMs = (System.nanoTime() - pressed) / 1e6
                    audio.write(pcm)
                }
                post { status.text = "Pocket TTS : fin de lecture audio…" }
                audio.drain()
                val output = String.format(Locale.FRENCH,
                    "Moteur : Pocket TTS français, voix prédéfinie Alba\nCPU : %d thread(s)\nChargement : %s\nPremier PCM depuis Lecture : %.0f ms\nPremier PCM après chargement : %.0f ms\nLancement AudioTrack depuis Lecture : %.0f ms\nGénération hors attente de lecture : %.0f ms\nAudio : %.2f s ; RTF : %.3f ; vitesse : %.2f×\nPSS maximal observé (250 ms) : %.1f Mio\nFin de phrase détectée : oui\n\n%s",
                    threads, if (cold) "%.0f ms".format(Locale.FRENCH, active.modelLoadMs) else "modèle réutilisé",
                    firstMs, metrics.firstPcmMs, (audio.launchNs - pressed) / 1e6,
                    metrics.computeMs, metrics.audioSeconds, metrics.rtf, metrics.speed, memory.peakPssMiB, DeviceMetrics.summary(this))
                post { status.text = "Lecture Pocket terminée."; diagnostics.text = output }
            } catch (t: Throwable) {
                val computeStoppedNs = System.nanoTime()
                post {
                    if (current.isCancelled()) {
                        val stopped = if (stopNs != 0L) "\nCalcul arrêté après %.0f ms.".format(Locale.FRENCH, (computeStoppedNs - stopNs) / 1e6) else ""
                        status.text = if (thermalPause) "Pocket arrêté : état thermique sévère. Attendre ou choisir Android TTS." else "Pocket TTS arrêté.$stopped"
                    } else status.text = "Échec Pocket TTS : ${t.javaClass.simpleName} — ${t.message.orEmpty().take(350)}\nAucun remplacement automatique. Vous pouvez choisir le secours Android."
                }
            } finally {
                audio.close(); player.compareAndSet(audio, null); memory.close()
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
        if (requestCode != 100 || resultCode != RESULT_OK) return
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
