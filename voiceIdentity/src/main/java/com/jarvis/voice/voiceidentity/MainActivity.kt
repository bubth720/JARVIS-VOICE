package com.jarvis.voice.voiceidentity

import android.app.*
import android.content.*
import android.net.Uri
import android.os.*
import android.provider.DocumentsContract
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.widget.*
import com.jarvis.voice.identity.*
import com.jarvis.voice.identity.VoiceProfile
import com.jarvis.voice.pocket.*
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/** Survives activity recreation: an old cancelled native call must close before any new model starts. */
private object IdentityWorkQueue { val executor=Executors.newSingleThreadExecutor() }

/** Independent 005H sandbox. No service, network, recorder, implicit identity switch or automatic backup. */
class MainActivity: Activity() {
    private val worker=IdentityWorkQueue.executor
    private val main=Handler(Looper.getMainLooper())
    private val player=AtomicReference<PcmPlayer?>()
    private var token: CancellationToken?=null
    private var busy=false
    @Volatile private var destroyed=false
    private lateinit var vault: VoiceIdentityVault
    private val pack get()=File(noBackupFilesDir,"models/fp32")
    private fun audioRoot(id: VoiceIdentityId)=File(noBackupFilesDir,"identity-audio/${id.value}")
    private fun comparisons(id: VoiceIdentityId)=IdentityComparisonStore(File(audioRoot(id),"comparison"))
    private fun single(id: VoiceIdentityId)=IdentityComparisonStore(File(audioRoot(id),"single"))
    private val archives get()=VoiceIdentityArchive(File(noBackupFilesDir,"archive-work"))
    private val controls=mutableListOf<View>()
    private lateinit var status: TextView
    private lateinit var library: TextView
    private lateinit var diagnostics: TextView
    private lateinit var metrics: TextView
    private lateinit var identities: Spinner
    private lateinit var references: Spinner
    private lateinit var profiles: Spinner
    private lateinit var profileA: Spinner
    private lateinit var profileB: Spinner
    private lateinit var labels: Spinner
    private lateinit var steps: Spinner
    private lateinit var temperature: Spinner
    private lateinit var name: EditText
    private lateinit var consentNote: EditText
    private lateinit var comment: EditText
    private lateinit var start: EditText
    private lateinit var duration: EditText
    private lateinit var text: EditText
    private lateinit var seed: EditText
    private lateinit var consent: CheckBox
    private lateinit var samePerson: CheckBox
    private lateinit var keepReference: CheckBox
    private lateinit var includeReferences: CheckBox
    private lateinit var heard: CheckBox
    private lateinit var ratings: List<Spinner>
    private lateinit var stop: Button
    private var identityRows=emptyList<VoiceIdentity>()
    private var referenceRows=emptyList<VoiceReference>()
    private var profileRows=emptyList<VoiceProfile>()
    private var audition: JSONObject?=null
    private var archiveIdentity: VoiceIdentityId?=null
    private var archiveIncludesReferences=false
    private val supported get()="arm64-v8a" in Build.SUPPORTED_ABIS
    private fun ui(block: ()->Unit) { main.post { if(!destroyed) block() } }
    private fun selectedIdentity()=requireNotNull(vault.selected()) { "Créer ou sélectionner une identité" }
    private fun referenceId()=referenceRows.getOrNull(references.selectedItemPosition)?.id ?: error("Choisir une référence")
    private fun profileId(spinner: Spinner=profiles)=profileRows.getOrNull(spinner.selectedItemPosition)?.id ?: error("Choisir un profil")
    private fun confirm(message: String,action: ()->Unit) {
        AlertDialog.Builder(this).setTitle("Confirmation locale").setMessage(message)
            .setNegativeButton("Annuler",null).setPositiveButton("Confirmer") { _,_->guard(action) }.show()
    }
    private fun guard(action: ()->Unit) { try { action() } catch(e: Exception) { status.text=safeError(e) } }
    private fun safeError(t: Throwable): String=when(t) {
        is ReferenceFailure -> t.diagnostic()
        is FP32ImportException -> t.message.orEmpty().take(250)
        is PlaybackFailure -> "Lecture : ${t.reason} ; audio complet conservé pour réécoute"
        is IllegalArgumentException, is IllegalStateException -> "${t.javaClass.simpleName} — ${t.message.orEmpty().take(180)}"
        else -> "Échec ${t.javaClass.simpleName} ; données précédentes conservées. Aucun chemin ou contenu privé journalisé."
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        try { vault=VoiceIdentityVault(File(noBackupFilesDir,"identity-vault")) } catch(_: Exception) {
            setContentView(TextView(this).apply { text="005H — coffre indisponible ou incompatible. Aucun effacement automatique. Conserver les données ; ne pas désinstaller." });return
        }
        // Queued behind any cancelled operation from a previous activity, never racing its private files.
        worker.execute {
            File(noBackupFilesDir,"incoming-audio.private").delete()
            File(noBackupFilesDir,"archive-work").listFiles()?.forEach { it.deleteRecursively() }
        }
        val layout=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(24,24,24,24) }
        fun label(value: String)=TextView(this).apply { text=value;textSize=16f;setTextIsSelectable(true);layout.addView(this) }
        fun button(value: String,action: ()->Unit)=Button(this).apply { text=value;layout.addView(this);controls.add(this);setOnClickListener { guard(action) } }
        fun field(hintText: String,value: String,max: Int=500)=EditText(this).apply { hint=hintText;setText(value);filters=arrayOf(InputFilter.LengthFilter(max));layout.addView(this);controls.add(this) }
        fun check(value: String)=CheckBox(this).apply { text=value;layout.addView(this);controls.add(this) }
        fun spinner(title: String,options: List<String> = emptyList(),selected: Int=0): Spinner {
            label(title);return Spinner(this).apply { adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,options);setSelection(selected);layout.addView(this);controls.add(this) }
        }
        label("JARVIS VOICE IDENTITY 005H — EXPÉRIMENTAL").textSize=23f
        label("Application indépendante : 005D/005F/005G et leurs profils restent intacts. Synthèse artificielle. Tout reste sur ce téléphone ; aucune identification automatique de locuteur.")
        button("Importer le ZIP FP32 existant") { choose("application/zip",200) }
        label("Identités et bibliothèque privée")
        identities=spinner("Identité à sélectionner")
        button("Sélectionner explicitement cette identité") {
            val id=identityRows.getOrNull(identities.selectedItemPosition)?.id ?: error("Aucune identité")
            task("Sélection locale",false) { vault.select(id,it);audition=null;ui { consent.isChecked=false;samePerson.isChecked=false };"Identité sélectionnée ; confirmer de nouveau le consentement avant tout nouvel import." }
        }
        name=field("Nom local de l'identité", "",80)
        consentNote=field("Note de consentement privée (par exemple : ma propre voix)","",500)
        consent=check("C'est ma voix, ou cette personne consent explicitement et légalement au clonage local.")
        button("Créer une nouvelle identité") {
            val n=name.text.toString();val note=consentNote.text.toString();require(consent.isChecked)
            task("Création de l'identité",false) { vault.create(n,note,true,it);ui { consent.isChecked=false;samePerson.isChecked=false };"Identité créée, aucun ancien profil effacé. Confirmer la provenance de chaque référence importée." }
        }
        button("Renommer l'identité active") { val id=selectedIdentity();val n=name.text.toString();task("Renommage",false) { vault.rename(id,n,it);"Nom local modifié." } }
        start=field("Début de l'extrait (secondes, 0 à 175)","0",3)
        duration=field("Durée contiguë (5 à 15 secondes)","10",2)
        labels=spinner("Annotation manuelle de la référence — pas un style contrôlé par le moteur",ReferenceLabel.entries.map { it.name },4)
        comment=field("Commentaire privé de cette référence","",1000)
        samePerson=check("Je confirme que cet extrait contient la même personne que l'identité active, sans autre locuteur.")
        keepReference=check("Conserver cet extrait WAV privé après l'encodage pour réencodage futur. Sinon : supprimé après création réussie du profil.")
        button("Ajouter un MP3/WAV local — un fichier à la fois") { selectedIdentity();require(consent.isChecked && samePerson.isChecked) { "Confirmer consentement et même personne" };choose("audio/*",201) }
        references=spinner("Référence de l'identité active")
        button("Écouter cette référence") { val id=selectedIdentity();val rid=referenceId();task("Écoute de la référence",false) { play(vault.readReference(id,rid,it),it);"Référence écoutée ; vérifier parole claire, musique, bruit et locuteur." } }
        button("Annoter la référence sélectionnée") { val id=selectedIdentity();val rid=referenceId();val label=ReferenceLabel.valueOf(labels.selectedItem.toString());val note=comment.text.toString();task("Annotation",false) { vault.annotateReference(id,rid,label,note,it);"Annotation manuelle enregistrée ; aucun contrôle émotionnel ajouté." } }
        button("Ajouter une proposition contiguë de 5 s, sans remplacer l'ancienne") { suggest() }
        button("Créer un profil séparé depuis cette référence") {
            val id=selectedIdentity();val rid=referenceId()
            task("Encodage FP32 officiel Mimi/BOS/18 états, un seul modèle…",true) {
                IdentityEncoding.official(vault,id,rid,pack,it)
                "Profil individuel ajouté ; aucun prédécesseur effacé. Choisir explicitement le profil actif."
            }
        }
        profiles=spinner("Profil à inspecter / choisir / supprimer")
        button("Activer ce profil pour les synthèses") { activate(false) }
        button("Choisir ce profil comme NEUTRE PRINCIPAL et l'activer") { activate(true) }
        button("Désactiver ce profil, sans supprimer ses données") { val id=selectedIdentity();val p=profileId();confirm("Désactiver ce profil ? Aucun autre ne sera choisi automatiquement.") { task("Désactivation",false) { vault.deactivate(id,p,it);"Profil désactivé." } } }
        button("Réactiver ce profil — sans le sélectionner") { val id=selectedIdentity();val p=profileId();task("Réactivation",false) { vault.enable(id,p,it);"Profil disponible ; sélection active inchangée." } }
        library=label("")
        label("Synthèse 005G équivalente — un thread CPU, lecture différée STATIC PCM16")
        text=field("Même texte pour A/B",state?.getString("phrase") ?: "Demain, je prendrai le train pour Paris.",300)
        seed=field("Même graine pour A/B", "42",20)
        steps=spinner("Étapes LSD communes à A et B",listOf("1","2","3","5"))
        temperature=spinner("Température commune à A et B",listOf("0.2","0.3","0.5"),1)
        label("EOS −4 ; queue officielle selon texte ; sortie PCM16 commune. Les paramètres de référence 005G restent disponibles, aucun filtre ajouté.")
        button("Générer avec le profil actif de l'identité") { generate(null) }
        button("Réécouter la synthèse active sans modèle") { replay("main") }
        profileA=spinner("Profil A — comparaison au sein de la même identité")
        profileB=spinner("Profil B — autre conditionnement de cette même personne")
        button("Générer A puis écouter") { generate("A") }
        button("Générer B puis écouter — mêmes paramètres que A") { generate("B") }
        button("Réécouter A sans recalcul") { replay("A") }
        button("Réécouter B sans recalcul") { replay("B") }
        label("Évaluation subjective de la dernière écoute complète — jamais préremplie")
        ratings=listOf("Naturel (0 robotique, 5 naturel)","Ressemblance (0 faible, 5 forte)","Constance de timbre (0 variable, 5 constante)","Intelligibilité (0 faible, 5 excellente)","Artefacts métalliques (0 absents, 5 prononcés)").map { spinner(it,listOf("Non noté","0","1","2","3","4","5")) }
        heard=check("J'ai réellement entendu cet audio sur ce téléphone et je connais la voix de référence.")
        button("Enregistrer les cinq notes localement pour le profil écouté") { rate() }
        button("Tester le lecteur avec un signal synthétique") { task("Test 440 Hz",false) { c -> play(FloatArray(24000) { (kotlin.math.sin(it*2*kotlin.math.PI*440/24000)*.1).toFloat() },c);"Signal synthétique terminé ; confirmer son audibilité réelle." } }
        label("Archivage privé manuel — uniquement chiffré, fichier local choisi par vous")
        includeReferences=check("Inclure aussi les extraits WAV encore conservés et consentis dans l'archive chiffrée.")
        button("Sauvegarder cette identité en archive chiffrée") {
            archiveIdentity=selectedIdentity();archiveIncludesReferences=includeReferences.isChecked
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { type="application/octet-stream";addCategory(Intent.CATEGORY_OPENABLE);putExtra(Intent.EXTRA_TITLE,"JARVIS-VOICE-IDENTITY.jvi") },203)
        }
        button("Restaurer une archive chiffrée locale — aucun écrasement") { require(consent.isChecked) { "Confirmer le consentement légal des voix de cette archive avant restauration" };choose("application/octet-stream",204) }
        button("Supprimer seulement le profil sélectionné") { val id=selectedIdentity();val pid=profileId();confirm("Supprimer ce profil et ses seuls audios ? Les autres profils et références restent conservés.") { task("Suppression du profil",false) { cancel -> comparisons(id).deleteProfile(id,pid,cancel);single(id).deleteProfile(id,pid,cancel);vault.removeProfile(id,pid,true,cancel);audition=null;"Profil supprimé, autres profils intacts." } } }
        button("Effacer seulement les audios de l'identité active — profils conservés") { val id=selectedIdentity();confirm("Effacer les caches de réécoute de cette identité seulement ? Aucun profil ni référence ne sera supprimé.") { task("Suppression des caches privés",false) { comparisons(id).delete();single(id).delete();audition=null;"Audios de cette identité effacés ; profils et autres identités intacts." } } }
        button("Supprimer seulement le WAV de cette référence") { val id=selectedIdentity();val rid=referenceId();confirm("Supprimer cet extrait WAV privé ? Les profils encodés restent utilisables, mais un réimport sera nécessaire pour réencoder.") { task("Suppression de l'extrait",false) { vault.removeReferenceAudio(id,rid,true,it);"WAV supprimé, métadonnées et profils conservés." } } }
        button("Supprimer les métadonnées de cette référence sans profil") { val id=selectedIdentity();val rid=referenceId();confirm("Supprimer cette référence ? Refus si elle est encore liée à un profil.") { task("Suppression de la référence",false) { vault.removeReference(id,rid,true,it);"Référence supprimée." } } }
        button("Supprimer l'identité active et ses données privées") { val id=selectedIdentity();confirm("Supprimer cette identité, ses références, profils et audios ? Aucun changement des autres identités ni des applications 005D/F/G.") { task("Suppression de l'identité",false) { check(audioRoot(id).deleteRecursively());vault.removeIdentity(id,true,it);audition=null;"Identité supprimée ; aucune autre identité sélectionnée automatiquement." } } }
        stop=Button(this).apply { text="ARRÊT";layout.addView(this);setOnClickListener { stop() } }
        status=label("Importer le ZIP FP32 existant puis créer une identité. Aucun fichier personnel à transmettre au développeur.")
        diagnostics=label("");metrics=label(DeviceMetrics.summary(this))
        button("Licences et limites") { AlertDialog.Builder(this).setTitle("005H — identité, pas contrôle émotionnel")
            .setMessage(assets.open("THIRD_PARTY_NOTICES.txt").bufferedReader().use { it.readText() }).setPositiveButton("Fermer",null).show() }
        setContentView(ScrollView(this).apply { fitsSystemWindows=true;addView(layout) });refresh()
    }
    private fun fill(spinner: Spinner,items: List<String>,position: Int=0) {
        spinner.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,items)
        if(items.isNotEmpty()) spinner.setSelection(position.coerceIn(0,items.lastIndex))
    }
    private fun refresh() {
        if(!::status.isInitialized) return
        try {
            val oldRef=referenceRows.getOrNull(references.selectedItemPosition)?.id
            val oldProfile=profileRows.getOrNull(profiles.selectedItemPosition)?.id
            val oldA=profileRows.getOrNull(profileA.selectedItemPosition)?.id;val oldB=profileRows.getOrNull(profileB.selectedItemPosition)?.id
            identityRows=vault.list();val id=vault.selected();val current=identityRows.singleOrNull { it.id==id }
            fill(identities,identityRows.map { it.name+" · "+it.id.value.take(8) },identityRows.indexOfFirst { it.id==id })
            referenceRows=current?.references.orEmpty();profileRows=current?.profiles.orEmpty()
            fill(references,referenceRows.map { "${it.label} · ${it.id.take(8)} · ${it.durationMs/1000.0} s · WAV ${if(it.retained) "conservé" else "absent"}" },referenceRows.indexOfFirst { it.id==oldRef })
            val names=profileRows.map { p -> "${referenceRows.single { it.id==p.referenceId }.label} · ${p.id.take(8)} · ${p.status}" }
            fill(profiles,names,profileRows.indexOfFirst { it.id==oldProfile });fill(profileA,names,profileRows.indexOfFirst { it.id==oldA })
            fill(profileB,names,profileRows.indexOfFirst { it.id==oldB }.takeIf { it>=0 } ?: 1)
            val active=current?.activeProfiles?.get(PocketIdentity.bindingKey)
            library.text=if(current==null) "Aucune identité active. Aucune voix de remplacement automatique." else
                "Identité active : ${current.name} · ${current.id.value}\nConsentement : ${current.consentNote}\nProfil actif : ${active ?: "AUCUN — choix explicite requis"}\nRéférence neutre principale : ${current.preferredNeutralReferenceId ?: "aucune"}\n"+
                current.references.joinToString("\n") { "Référence ${it.id.take(8)} : ${it.label}, ${it.durationMs/1000.0} s depuis ${it.startMs/1000.0} s, WAV ${if(it.retained) "privé présent" else "supprimé"}. ${it.comment}\n${it.analysis}" }+
                "\n"+current.profiles.joinToString("\n") { "Profil ${it.id.take(8)} : ${it.status}, référence ${it.referenceId.take(8)}, ${it.engineId}\nSHA256 ${it.sha256}\n${it.provenance}\nÉvaluation humaine : ${it.evaluation ?: "non noté"}" }
        } catch(_: Exception) { status.text="Index privé indisponible ; aucun effacement automatique." }
        controls.forEach { it.isEnabled=!busy && supported };stop.isEnabled=busy
    }
    private fun task(title: String,heavy: Boolean,cleanup: ()->Unit={},operation: (CancellationToken)->String): Boolean {
        if(busy) { cleanup();return false }
        if(DeviceMetrics.isThermallySevere(this)) { status.text="Protection thermique : laisser refroidir.";cleanup();return false }
        if(heavy) {
            val m=ActivityManager.MemoryInfo();(getSystemService(ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(m)
            try { IdentityAdmission.requireHeavy(m.availMem,m.lowMemory,DeviceMetrics.thermal(this)) } catch(e: Exception) { status.text=safeError(e);cleanup();return false }
        }
        val cancel=CancellationToken();token=cancel;busy=true;status.text=title;refresh()
        worker.execute {
            val measure=OperationMetrics(this);measure.start { cancel.cancel();player.get()?.stop() }
            try {
                cancel.check()
                if(heavy) { val m=ActivityManager.MemoryInfo();(getSystemService(ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(m);IdentityAdmission.requireHeavy(m.availMem,m.lowMemory,DeviceMetrics.thermal(this)) }
                val result=operation(cancel);cancel.check();ui { status.text=result }
            }
            catch(t: Throwable) { ui { status.text=if(cancel.isCancelled()) "Opération annulée ou garde thermique/mémoire déclenchée ; vérifier l'état de la bibliothèque." else safeError(t) } }
            finally { cleanup();measure.close();ui { metrics.text=measure.summary();busy=false;token=null;refresh() } }
        }
        return true
    }
    private fun stop() { token?.cancel();player.get()?.stop();if(::status.isInitialized) status.text="Arrêt demandé ; audio interrompu, opération native en cours d'annulation." }
    private fun choose(mime: String,code: Int)=startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type=mime;addCategory(Intent.CATEGORY_OPENABLE) },code)
    /** Providers with their own cloud transport are deliberately refused, even without INTERNET in this APK. */
    private fun requireLocal(uri: Uri) {
        require(uri.scheme=="content" && uri.authority in setOf("com.android.externalstorage.documents","com.android.providers.downloads.documents","com.android.providers.media.documents")) { "Choisir un document local dans Téléchargements/stockage interne ; fournisseur distant ou inconnu refusé" }
    }
    @Deprecated("SAF activity results retained for minSdk 26")
    override fun onActivityResult(code: Int,result: Int,data: Intent?) {
        super.onActivityResult(code,result,data);if(result!=RESULT_OK) return
        val uri=data?.data ?: return
        guard {
            requireLocal(uri)
            when(code) {
                200 -> task("Import fermé et SHA256 du pack FP32…",false) { cancel ->
                    require(noBackupFilesDir.usableSpace>=1200L*1048576) { "Au moins 1,2 Gio de stockage libre requis" }
                    requireNotNull(contentResolver.openInputStream(uri)).use { ExperimentalModelPack.install(it,File(noBackupFilesDir,"models"),cancel::isCancelled) }
                    "Pack FP32 vérifié ; aucun poids modifié."
                }
                201 -> importReference(uri)
                203 -> { val id=requireNotNull(archiveIdentity) { "Sélectionner de nouveau l'identité à sauvegarder" };passwordDialog(true) { chars ->
                    val started=task("Sauvegarde privée chiffrée AES-256-GCM…",true,cleanup={ chars.fill('\u0000') }) { cancel ->
                        try { requireNotNull(contentResolver.openOutputStream(uri,"w")).use { archives.export(vault,id,chars,archiveIncludesReferences,it,cancel) };"Archive chiffrée écrite dans le document local choisi. Conserver le mot de passe séparément ; aucune récupération possible sans lui." }
                        catch(t: Throwable) { runCatching { DocumentsContract.deleteDocument(contentResolver,uri) };throw t }
                        finally { chars.fill('\u0000') }
                    }
                    if(!started) chars.fill('\u0000')
                } }
                204 -> { require(consent.isChecked) { "Confirmer le consentement légal des voix de cette archive avant restauration" };passwordDialog(false) { chars -> val started=task("Authentification puis restauration atomique…",true,cleanup={ chars.fill('\u0000') }) { cancel ->
                    try { requireNotNull(contentResolver.openInputStream(uri)).use { archives.restore(vault,chars,it,cancel) };audition=null;ui { consent.isChecked=false;samePerson.isChecked=false };"Identité restaurée et sélectionnée explicitement par cette opération ; aucun profil précédent écrasé." }
                    finally { chars.fill('\u0000') }
                };if(!started) chars.fill('\u0000') } }
            }
        }
    }
    private fun passwordDialog(export: Boolean,action: (CharArray)->Unit) {
        val layout=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        val entry=EditText(this).apply { hint="Mot de passe (12 à 128 caractères)";inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD;filters=arrayOf(InputFilter.LengthFilter(128));isSaveEnabled=false;importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO;imeOptions=android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;layout.addView(this) }
        val repeat=if(export) EditText(this).apply { hint="Confirmer le mot de passe";inputType=entry.inputType;filters=arrayOf(InputFilter.LengthFilter(128));isSaveEnabled=false;importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO;imeOptions=entry.imeOptions;layout.addView(this) } else null
        val dialog=AlertDialog.Builder(this).setTitle(if(export) "Sauvegarde chiffrée locale" else "Restauration chiffrée locale")
            .setMessage("Aucun mot de passe enregistré. Sans lui, pas de restauration. Une identité existante n'est jamais remplacée.")
            .setView(layout).setNegativeButton("Annuler",null).setPositiveButton("Continuer",null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val chars=CharArray(entry.length()) { entry.text[it] }
            val confirmation=repeat?.let { view -> CharArray(view.length()) { view.text[it] } }
            val matches=confirmation==null || chars.contentEquals(confirmation);confirmation?.fill('\u0000')
            if(chars.size !in 12..128 || !matches) { chars.fill('\u0000');entry.error="12 à 128 caractères, confirmation identique requise" }
            else { entry.text.clear();repeat?.text?.clear();dialog.dismiss();action(chars) }
        } };dialog.setOnDismissListener { entry.text.clear();repeat?.text?.clear() };dialog.show()
    }
    private fun importReference(uri: Uri) {
        val id=selectedIdentity();require(consent.isChecked && samePerson.isChecked) { "Consentement et même personne requis pour chaque import" }
        val begin=start.text.toString().toLongOrNull();val seconds=duration.text.toString().toLongOrNull()
        require(begin!=null && begin in 0..175 && seconds!=null && seconds in 5..15)
        val note=consentNote.text.toString();val annotation=ReferenceLabel.valueOf(labels.selectedItem.toString());val c=comment.text.toString();val keep=keepReference.isChecked
        task("Import et décodage locaux, original temporaire limité à 32 Mio…",false) { cancel ->
            val incoming=File(noBackupFilesDir,"incoming-audio.private")
            try {
                requireNotNull(contentResolver.openInputStream(uri)).use { input -> boundedCopy(input,incoming,33554432,cancel) }
                val hash=VoiceIdentityVault.sha(incoming,cancel)
                val wav=incoming.inputStream().use { input -> ByteArray(4).also { java.io.DataInputStream(input).readFully(it) }.toString(Charsets.US_ASCII)=="RIFF" }
                val info=if(wav) IdentityWav.inspect(incoming) else LocalMp3.inspect(incoming)
                val pcm=if(wav) IdentityWav.decode(incoming,begin*1000,seconds*1000,cancel) else LocalMp3.decode(incoming,begin*1000,seconds*1000,cancel)
                vault.addReference(id,pcm,if(wav) "WAV" else "MP3",hash,begin*1000,annotation,c,note,true,true,keep,cancel,sourceDurationMs=info.durationMs)
                ui { consent.isChecked=false;samePerson.isChecked=false }
                "Référence ajoutée sans effacer les précédentes. Original MP3/WAV supprimé de la zone temporaire.\n"+ReferenceSelection.assess(pcm).summary()
            } finally { incoming.delete() }
        }
    }
    private fun boundedCopy(input: java.io.InputStream,file: File,limit: Long,cancel: CancellationToken) {
        file.outputStream().use { out -> val b=ByteArray(65536);var count=0L;while(true) { cancel.check();val n=input.read(b);if(n<0) break;count+=n;require(count<=limit) { "Document trop grand" };out.write(b,0,n) };out.fd.sync() }
    }
    private fun suggest() {
        val id=selectedIdentity();val rid=referenceId();require(consent.isChecked && samePerson.isChecked)
        val source=referenceRows.single { it.id==rid };val note=consentNote.text.toString();val keep=keepReference.isChecked
        task("Proposition énergétique d'une portion contiguë, aucun filtre…",false) { cancel ->
            val pcm=vault.readReference(id,rid,cancel);val region=ReferenceSelection.suggestFiveSeconds(pcm)
            // Provenance ties this derivative to the selected WAV, never to another identity.
            val digest=vault.referenceDigest(id,rid,cancel)
            vault.addReference(id,pcm.copyOfRange(region.first,region.last+1),"WAV",digest,region.first/24L,source.label,"Proposition contiguë issue de la référence ${source.id}",note,true,true,keep,cancel,sourceDurationMs=source.durationMs)
            "Nouvelle proposition ajoutée ; ancienne référence et profils préservés. Écouter avant de l'encoder. Analyse d'énergie uniquement, pas de VAD ou reconnaissance de locuteur."
        }
    }
    private fun activate(neutral: Boolean) { val id=selectedIdentity();val pid=profileId();task("Choix explicite du profil actif",false) { vault.activate(id,pid,neutral,it);"Profil actif choisi. Aucun basculement automatique vers une variante expressive." } }
    private fun settings()=QualitySampling(steps=steps.selectedItem.toString().toInt(),temperature=temperature.selectedItem.toString().toDouble())
    private fun generate(slot: String?) {
        val id=selectedIdentity();val identity=identityRows.single { it.id==id }
        val pid=if(slot==null) requireNotNull(identity.activeProfiles[PocketIdentity.bindingKey]) { "Activer explicitement un profil avant synthèse" } else profileId(if(slot=="A") profileA else profileB)
        val profile=identity.profiles.single { it.id==pid };val phrase=text.text.toString().trim();require(phrase.isNotEmpty())
        val randomSeed=requireNotNull(seed.text.toString().toLongOrNull()) { "Graine entière requise" };val sampling=settings()
        val store=if(slot==null) single(id) else comparisons(id);val side=slot ?: "A"
        task("${identity.name} / profil ${pid.take(8)} : ${sampling.label()} ; génération…",true) { cancel ->
            audition=null
            require(vault.selected()==id) { "Identité active modifiée ; la sélectionner de nouveau avant génération" }
            if(slot=="B") store.requireB(id,profile,store.context(id,phrase,randomSeed,sampling),cancel)
            val voice=vault.binding().resolve(id,pid,cancel);val chunks=mutableListOf<FloatArray>();var samples=0
            val result=QualityEngine(pack,voice,loadCancel=cancel,sampling=sampling).use { engine ->
                engine.generate(phrase,cancel,randomSeed,wholeSentence=true) { block -> samples+=block.size;require(samples<=480000);chunks.add(block) }
            }
            cancel.check();val pcm=FloatArray(samples);var offset=0;chunks.forEach { it.copyInto(pcm,offset);offset+=it.size };chunks.clear()
            store.save(side,id,profile,phrase,randomSeed,sampling,pcm,cancel)
            val stats=PcmStatistics.measure(pcm).summary();ui { diagnostics.text="Génération réussie : ${identity.name} / ${pid.take(8)} ; EOS ${result.eosReached} ; RTF ${result.rtf}\n$stats\nPCM conservé, lecture pas encore commencée." }
            play(pcm,cancel);remember(slot ?: "main",store.metadata(side))
            "Génération réussie et lecture terminée : ${identity.name} / ${pid.take(8)}. Aucun résultat de naturel ou ressemblance déduit automatiquement."
        }
    }
    private fun play(pcm: FloatArray,cancel: CancellationToken) {
        audition=null;ui { heard.isChecked=false;ratings.forEach { it.setSelection(0) } }
        val stats=PcmStatistics.measure(pcm);require(stats.samples>0 && stats.nonFinite==0)
        ui { diagnostics.text="${stats.summary()}\nLecture : chargement STATIC PCM16…" }
        val audio=PcmPlayer(this,cancel,{ snapshot -> ui { diagnostics.text="${stats.summary()}\n$snapshot" } },{ ui { status.text="Lecture commencée — progression mesurée" } })
        player.set(audio)
        try { audio.write(pcm);audio.drain();check(audio.completed);ui { status.text="Lecture terminée — toutes les frames présentées" } }
        finally { audio.close();player.compareAndSet(audio,null) }
    }
    private fun replay(slot: String) {
        val id=selectedIdentity();val store=if(slot=="main") single(id) else comparisons(id);val side=if(slot=="main") "A" else slot
        task("Réécoute $slot sans modèle ni recalcul…",false) { cancel ->
            val meta=store.metadata(side);play(store.load(side,vault,cancel),cancel);remember(slot,meta)
            "Réécoute $slot terminée ; identité et profil vérifiés, aucun recalcul."
        }
    }
    private fun remember(slot: String,meta: JSONObject) { audition=JSONObject(meta.toString()).put("slot",slot) }
    private fun rate() {
        val a=requireNotNull(audition) { "Écouter un profil avant de noter" }
        require(heard.isChecked && ratings.all { it.selectedItemPosition>0 }) { "Confirmer l'audibilité et renseigner les cinq notes" }
        val scores=ratings.map { it.selectedItem.toString().toInt() };val slot=a.getString("slot");val id=VoiceIdentityId(a.getString("identityId"));val store=if(slot=="main") single(id) else comparisons(id);val side=if(slot=="main") "A" else slot
        task("Évaluation humaine privée",false) { cancel ->
            store.load(side,vault,cancel)
            require(store.metadata(side).getString("pcmHash")==a.getString("pcmHash") && store.metadata(side).getString("context")==a.getString("context")) { "Audio remplacé ; réécouter avant de noter" }
            vault.noteProfile(VoiceIdentityId(a.getString("identityId")),a.getString("profileId"),scores,true,cancel,evidence=a)
            ui { heard.isChecked=false;ratings.forEach { it.setSelection(0) } }
            "Évaluation humaine conservée localement pour ce profil ; aucune amélioration générale déclarée."
        }
    }
    override fun onSaveInstanceState(state: Bundle) { if(::text.isInitialized) state.putString("phrase",text.text.toString());super.onSaveInstanceState(state) }
    override fun onStop() { if(busy) stop();super.onStop() }
    override fun onDestroy() { destroyed=true;token?.cancel();player.get()?.stop();super.onDestroy() }
}
