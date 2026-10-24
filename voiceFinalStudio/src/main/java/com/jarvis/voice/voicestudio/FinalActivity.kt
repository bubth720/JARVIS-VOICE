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
        super.onCreate(saved);window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
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
        text=EditText(this).apply { minLines=4;maxLines=10;setText(voice.session?.plan?.text ?: "Demain, je prendrai le train pour Paris.")
            filters=arrayOf(FinalTextFilter { c.notice("Texte trop long : modification entière refusée, aucun préfixe tronqué.") });col.addView(this) }
        button("Importer un texte UTF-8 local") { choose(700,"text/plain",false) }
        label("Profil de ressources : un thread ONNX dans tous les modes ; ÉCO ajoute du repos entre segments, AUTO s’adapte à la batterie et au thermique.")
        mode=spinner(ResourceMode.entries.map { it.name });mode.setSelection(preferences.getInt("mode",2).coerceIn(0,2))
        label("Émotion : seul NEUTRAL est réalisable. Les autres choix renvoient UNSUPPORTED.")
        emotion=spinner(Emotion.entries.map { if(it==Emotion.NEUTRAL) "NEUTRAL" else "${it.name} · UNSUPPORTED" })
        label("Sampler officiel : paramètres d’essai, aucun réglage déclaré supérieur.")
        steps=spinner(listOf("1 étape","3 étapes","5 étapes"));steps.setSelection(listOf(1,3,5).indexOf(c.state.settings.steps).coerceAtLeast(0))
        temperature=spinner(listOf("0.3","0.5","0.7"));temperature.setSelection(listOf(.3,.5,.7).indexOf(c.state.settings.temperature).coerceAtLeast(0))
        button("Préparer le document") {
            val previous=c.state.settings
            preferences.edit().putInt("previousSteps",previous.steps).putFloat("previousTemperature",previous.temperature.toFloat()).putInt("mode",mode.selectedItemPosition).apply()
            c.update { it.copy(settings=it.settings.copy(steps=listOf(1,3,5)[steps.selectedItemPosition],temperature=listOf(.3,.5,.7)[temperature.selectedItemPosition])) }
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
            val old=c.state.settings
            c.update { it.copy(settings=it.settings.copy(steps=preferences.getInt("previousSteps",1),temperature=preferences.getFloat("previousTemperature",.3f).toDouble().let { t->listOf(.3,.5,.7).minBy { kotlin.math.abs(it-t) } })) }
            preferences.edit().putInt("previousSteps",old.steps).putFloat("previousTemperature",old.temperature.toFloat()).apply()
            steps.setSelection(listOf(1,3,5).indexOf(c.state.settings.steps));temperature.setSelection(listOf(.3,.5,.7).indexOf(c.state.settings.temperature));refresh()
        }
        status=TextView(this).apply { setPadding(0,24,0,24);col.addView(this) };refresh()
    }
    private fun requirePlanText() { require(voice.session?.plan?.text==text.text.toString()) { "PREPARE_CHANGED_TEXT" } }
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
    private fun refresh() { if(!::status.isInitialized) return;status.text=voice.status+"\n"+c.message+"\n"+c.metrics+"\n"+voice.report.toString(2);idleButtons.forEach { it.isEnabled=!c.busy };text.isEnabled=!c.busy;steps.isEnabled=!c.busy;temperature.isEnabled=!c.busy;mode.isEnabled=!c.busy;emotion.isEnabled=!c.busy }
    override fun onStart() { super.onStart();voice.listener={refresh()};c.observe(this) { refresh() };refresh() }
    override fun onStop() { if(!isChangingConfigurations) voice.stop();voice.listener=null;c.unobserve(this);super.onStop() }
}
