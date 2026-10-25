package com.jarvis.voice.voicestudio

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.jarvis.voice.final01.*
import java.nio.charset.CodingErrorAction

/** Independent launcher. Original Studio remains available inside this app for the unchanged vault. */
class FinalActivity: AppCompatActivity() {
    private val app get()=application as FinalApplication
    private val voice get()=app.finalVoice
    private val c get()=app.controller
    private lateinit var text: EditText
    private lateinit var status: TextView
    private lateinit var mode: Spinner
    private lateinit var emotion: Spinner
    private lateinit var steps: Spinner
    private lateinit var temperature: Spinner
    private val idleButtons=mutableListOf<Button>()
    private var side="B"
    private val preferences get()=getSharedPreferences("final01-settings",0)
    private fun guard(block: ()->Unit) { try { block() } catch(t: Exception) { c.error(t);refresh() } }
    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved);side=saved?.getString("exportSide")?.takeIf { it in setOf("A","B") } ?: "B";window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val col=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(24,32,24,24) }
        setContentView(ScrollView(this).apply { addView(col) })
        fun label(value: String) { col.addView(TextView(this).apply { this.text=value;setPadding(0,12,0,8) }) }
        fun button(value: String,active: Boolean=false,action: ()->Unit) { col.addView(Button(this).apply { this.text=value;setOnClickListener { guard(action) };if(!active) idleButtons.add(this) }) }
        fun spinner(values: List<String>)=Spinner(this).apply { adapter=ArrayAdapter(this@FinalActivity,android.R.layout.simple_spinner_dropdown_item,values);col.addView(this) }
        label("ATHÉNA · VOICE FINAL-01 · EXPÉRIMENTAL\nHors ligne. Pas de validation POCO ou d’émotion reconnue. Aucun profil remplacé automatiquement.")
        button("Identités, MP3 locaux, modèles, archives et comparaisons de profils") {
            check(!FinalSafety.quarantined);startActivity(Intent(this,StudioActivity::class.java))
        }
        label("Texte privé : jusqu’à 200 000 caractères / 4 000 segments. Le frontend officiel peut normaliser ponctuation et nombres. La longue narration reste à qualifier à l’oreille.")
        text=EditText(this).apply { minLines=4;maxLines=10;isSaveEnabled=false;setText(voice.draftText ?: voice.session?.plan?.text ?: "Demain, je prendrai le train pour Paris.")
            filters=arrayOf(FinalTextFilter { c.notice("Texte trop long : modification entière refusée, aucun préfixe tronqué.") });col.addView(this) }
        button("Importer un texte UTF-8 local") { choose(700,"text/plain",false) }
        label("Profil de ressources : un thread ONNX dans tous les modes ; ÉCO ajoute du repos entre segments, AUTO s’adapte à la batterie et au thermique.")
        mode=spinner(ResourceMode.entries.map { it.name });mode.setSelection(preferences.getInt("mode",2).coerceIn(0,2))
        label("Émotion : seul NEUTRAL est réalisable. Les autres choix renvoient UNSUPPORTED.")
        emotion=spinner(Emotion.entries.map { if(it==Emotion.NEUTRAL) "NEUTRAL" else "${it.name} · UNSUPPORTED" })
        label("Sampler officiel : paramètres d’essai, aucun réglage déclaré supérieur.")
        steps=spinner(PocketControls.steps.map { "$it étapes" });steps.setSelection(PocketControls.steps.indexOf(c.state.settings.steps).coerceAtLeast(0))
        temperature=spinner(PocketControls.temperatures.map { it.toString() });temperature.setSelection(PocketControls.temperatures.indexOf(c.state.settings.temperature).coerceAtLeast(0))
        button("Préparer le document") {
            val previous=c.state.settings
            preferences.edit().putString("previousSettings",snapshot(previous,preferences.getInt("mode",2)).toString()).putInt("mode",mode.selectedItemPosition).apply()
            c.update { it.copy(settings=it.settings.copy(steps=PocketControls.steps[steps.selectedItemPosition],temperature=PocketControls.temperatures[temperature.selectedItemPosition])) }
            voice.prepare(text.text.toString(),ResourceMode.entries[mode.selectedItemPosition],Emotion.entries[emotion.selectedItemPosition])
        }
        button("Lire depuis le début préparé") { require(voice.session?.plan?.text==text.text.toString()) { "PREPARE_CHANGED_TEXT" };requirePlanText();voice.speak() }
        button("Pause après le segment courant",true) { voice.pause() }
        button("Reprendre après une pause") { requirePlanText();voice.speak(true) }
        button("Interruption : reprendre explicitement le segment incomplet") { confirm("Le segment incomplet repartira de son début ; les segments terminés ne seront pas rejoués.") { requirePlanText();voice.speak(true,Recovery.RETRY_INTERRUPTED_SEGMENT) } }
        button("Interruption : ignorer explicitement le segment incomplet") { confirm("Ce choix omet le segment incomplet ; cette omission sera comptée dans le rapport.") { requirePlanText();voice.speak(true,Recovery.SKIP_INTERRUPTED_SEGMENT) } }
        button("ARRÊT IMMÉDIAT",true) { voice.stop() }
        label("Comparaison A/B : document d’un seul segment, même voix/texte/graine/sampler. A = moteur VOICE-05 ; B = instrumentation FINAL-01, sans filtre ni modification acoustique.")
        button("Générer et écouter A") { requirePlanText();voice.speak(comparison="A") }
        button("Générer et écouter B") { requirePlanText();voice.speak(comparison="B") }
        button("Réécouter A sans modèle") { voice.replay("A") }
        button("Réécouter B en PCM16 sans modèle") { voice.replay("B") }
        button("Réécouter B en FLOAT32 pour contrôler la conversion") { voice.replay("B",true) }
        button("Exporter volontairement WAV A") { side="A";choose(701,"audio/wav",true) }
        button("Exporter volontairement WAV B") { side="B";choose(701,"audio/wav",true) }
        button("Effacer seulement les réécoutes A/B") { confirm("Effacer les deux WAV privés ; les profils restent conservés.") { voice.clearAudio() } }
        button("Analyser les références de la voix sélectionnée") { voice.analyzeReferences() }
        button("Exporter les compteurs sans texte, voix ou identifiant") { choose(702,"application/json",true) }
        button("Restaurer les paramètres précédents") {
            val old=c.state.settings;val oldMode=mode.selectedItemPosition
            val saved=org.json.JSONObject(preferences.getString("previousSettings",null) ?: snapshot(old,oldMode).toString())
            val restored=com.jarvis.voice.studio.StudioSettings(seed=saved.getLong("seed"),steps=saved.getInt("steps"),temperature=saved.getDouble("temperature"),
                intent=com.jarvis.voice.expression.VoiceExpressionIntent.valueOf(saved.getString("intent")),pauses=saved.getBoolean("pauses"),pauseMs=saved.getInt("pauseMs"))
            val restoredMode=saved.getInt("mode");require(restoredMode in ResourceMode.entries.indices)
            c.update { it.copy(settings=restored) }
            preferences.edit().putString("previousSettings",snapshot(old,oldMode).toString()).putInt("mode",restoredMode).apply()
            steps.setSelection(PocketControls.steps.indexOf(restored.steps));temperature.setSelection(PocketControls.temperatures.indexOf(restored.temperature));mode.setSelection(restoredMode);refresh()
        }
        status=TextView(this).apply { setPadding(0,24,0,24);col.addView(this) };refresh()
    }
    private fun snapshot(s: com.jarvis.voice.studio.StudioSettings,resource: Int)=org.json.JSONObject().put("seed",s.seed).put("steps",s.steps).put("temperature",s.temperature)
        .put("intent",s.intent.name).put("pauses",s.pauses).put("pauseMs",s.pauseMs).put("mode",resource)
    private fun requirePlanText() {
        require(voice.session?.plan?.text==text.text.toString()) { "PREPARE_CHANGED_TEXT" }
        voice.requireUiOptions(ResourceMode.entries[mode.selectedItemPosition],Emotion.entries[emotion.selectedItemPosition],PocketControls.steps[steps.selectedItemPosition],PocketControls.temperatures[temperature.selectedItemPosition])
    }
    private fun confirm(message: String,action: ()->Unit) { androidx.appcompat.app.AlertDialog.Builder(this).setMessage(message).setNegativeButton("Annuler",null).setPositiveButton("Confirmer") { _,_->guard(action) }.show() }
    private fun choose(code: Int,mime: String,create: Boolean) {
        check(!c.busy);startActivityForResult(Intent(if(create) Intent.ACTION_CREATE_DOCUMENT else Intent.ACTION_OPEN_DOCUMENT).apply { type=mime;addCategory(Intent.CATEGORY_OPENABLE);if(create) putExtra(Intent.EXTRA_TITLE,if(code==701) "ATHENA-rendu-$side.wav" else "ATHENA-compteurs.json") },code)
    }
    @Deprecated("SAF API26") override fun onActivityResult(code: Int,result: Int,data: Intent?) {
        super.onActivityResult(code,result,data);if(result!=Activity.RESULT_OK) return
        val uri=data?.data ?: return
        guard { c.requireLocal(uri)
            when(code) {
                700 -> c.run("Import UTF-8 privé…") { token ->
                    val bytes=contentResolver.openInputStream(uri)!!.use { input ->
                        val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                        while(true) { token.check();val n=input.read(buffer);if(n<0) break;require(out.size()+n<=800000) { "TEXT_FILE_LIMIT" };out.write(buffer,0,n) };out.toByteArray()
                    }
                    val decoded=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
                    require(decoded.length<=DocumentPlanner.MAX_UNITS);runOnUiThread { text.setText(decoded) };"Texte local importé ; préparer avant lecture."
                }
                701 -> voice.export(side,uri)
                702 -> c.run("Export volontaire des compteurs…") { _ -> contentResolver.openOutputStream(uri,"wt")!!.use { it.write(voice.report.toString(2).toByteArray()) };"Compteurs exportés, sans texte ni audio."
                }
            }
        }
    }
    private fun refresh() { if(!::status.isInitialized) return;val report=voice.report;val summary=org.json.JSONObject()
        listOf("schema","segments","wordBoundaryCuts","prepareTotalMs","completed","skipped","state","runElapsedFromPressMs","pressedToFirstPcmMs","pressedToPresentationEstimatedMs","quantiles").forEach { key->if(report.has(key)) summary.put(key,report.opt(key)) }
        val jobs=report.optJSONArray("jobs");summary.put("recordedJobsThisRun",jobs?.length() ?: 0)
        if(jobs!=null && jobs.length()>0) summary.put("lastStagesMs",jobs.optJSONObject(jobs.length()-1)?.optJSONObject("stagesMs"))
        status.text=voice.status+"\n"+c.message+"\n"+c.metrics+"\n"+summary.toString(2)+"\nDétail complet disponible par export volontaire des compteurs.";idleButtons.forEach { it.isEnabled=!c.busy };text.isEnabled=!c.busy;steps.isEnabled=!c.busy;temperature.isEnabled=!c.busy;mode.isEnabled=!c.busy;emotion.isEnabled=!c.busy }
    override fun onStart() { super.onStart();voice.listener={refresh()};c.observe(this) { refresh() };refresh() }
    override fun onSaveInstanceState(out: Bundle) { out.putString("exportSide",side);super.onSaveInstanceState(out) }
    override fun onStop() { if(::text.isInitialized) voice.draftText=text.text.toString();if(!isChangingConfigurations) voice.stop();voice.listener=null;c.unobserve(this);super.onStop() }
}
