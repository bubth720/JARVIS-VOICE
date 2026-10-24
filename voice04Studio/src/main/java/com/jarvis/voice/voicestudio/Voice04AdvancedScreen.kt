package com.jarvis.voice.voicestudio
import androidx.core.widget.doAfterTextChanged
import android.view.View
import android.widget.LinearLayout
import com.jarvis.voice.studio.*
import com.jarvis.voice.identity.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class AdvancedScreen(private val a: StudioActivity) {
    private val u=a.ui;private val c=a.controller
    private fun fold(page: LinearLayout,title: String,build: (LinearLayout)->Unit) {
        val body=LinearLayout(a).apply { orientation=LinearLayout.VERTICAL;visibility=View.GONE }
        u.button(page,title) { body.visibility=if(body.visibility==View.VISIBLE) View.GONE else View.VISIBLE }
        page.addView(body);build(body)
    }
    fun view(): View {
        val page=u.column();
        if(a.packageName!="com.jarvis.voice.studio.voice04") fold(page,"VOICE-05 · Narration et comparaison A/B") { body ->
            u.text(body,"Opt-in indépendant. <=6 phrases/1800 caractères, <=50 tokens/20s par job. Un thread ONNX et ouverture/fermeture par phrase. Le pool conserve seulement des tableaux PCM effacés, jamais de modèle.",14f)
            val input=u.input(body,"Narration locale",c.longDraft,1800,multiline=true)
            input.filters=arrayOf(Voice04LongTextFilter { c.notice("Collage refusé en entier : créer des lots distincts.") })
            val manual=u.check(body,"Coupes manuelles : une phrase complète par ligne",c.longManual)
            input.doAfterTextChanged { a.guard { c.editLong(it.toString()) } }
            manual.setOnCheckedChangeListener { _,value->a.guard { c.editLong(input.text.toString(),value) } }
            val modes=com.jarvis.voice.speed.SafePlaybackMode.entries
            val policy=u.spinner(body,"Politique audio",modes.map { it.name },c.narrativeMode.ordinal)
            u.button(body,"Préparer toutes les phrases") { a.guard { c.narrativePolicy(modes[policy.selectedItemPosition]);c.prepareNarrative(input.text.toString(),manual.isChecked) } }
            u.text(body,c.narrativeStatus,14f)
            u.button(body,"Lire le plan préparé") { a.guard { c.narrativePolicy(modes[policy.selectedItemPosition]);c.speakNarrative() } }
            u.button(body,"Pause après la phrase en cours") { c.pauseNarrative() }.tag="voice05-pause"
            u.button(body,"Reprendre explicitement") { a.guard { c.speakNarrative(true) } }
            u.button(body,"A · référence différée") { a.guard { c.compareNarrative("A") } }
            u.button(body,"B · même voix/texte/graine, politique choisie") { a.guard { c.narrativePolicy(modes[policy.selectedItemPosition]);c.compareNarrative("B") } }
            u.button(body,"Exporter les mesures expurgées") { a.speedExport() }
        }
        if(a.packageName.endsWith("voice06") || a.packageName.endsWith("voice07")) fold(page,"VOICE-06 · Effets logiciels A/B, émotions non validées") { body ->
            u.text(body,"Enveloppes d’énergie seulement : aucun changement de pitch/débit, pas d’émotion native, pas de rires ajoutés. Le modèle génère A une seule fois ; B transforme ce même PCM en mémoire privée. NEUTRAL et fallback restent exacts.",14f)
            val phrase=u.spinner(body,"Phrase publique de comparaison",c.emotionPhrases)
            val styles=listOf(com.jarvis.voice.expression.VoiceEmotionIntent.JOY,com.jarvis.voice.expression.VoiceEmotionIntent.SURPRISE,com.jarvis.voice.expression.VoiceEmotionIntent.CALM)
            val style=u.spinner(body,"Intention d’essai logiciel (pas une émotion validée)",styles.map { it.name })
            val intensity=u.spinner(body,"Intensité de l’effet logiciel",listOf("0","1","2"),1)
            val consent=u.check(body,"J’autorise cet effet logiciel expérimental sur le PCM généré, sans modifier ma voix enregistrée.")
            u.button(body,"Générer et écouter A neutre") { a.guard { c.emotionBaseline(phrase.selectedItemPosition) } }
            u.button(body,"Créer et écouter B logiciel") { a.guard { c.emotionEffect(styles[style.selectedItemPosition],intensity.selectedItemPosition,consent.isChecked) } }
            u.button(body,"Réécouter A") { a.guard { c.replayEmotion("A") } }
            u.button(body,"Réécouter B") { a.guard { c.replayEmotion("B") } }
            val heard=u.check(body,"J’ai réellement entendu A et B en entier.")
            u.button(body,"Noter cette observation d’écoute locale") { a.guard { c.emotionReview(heard.isChecked) } }
            u.text(body,c.emotionDiagnostic,14f)
            u.text(body,"CONCERN / THOUGHTFUL / SADNESS / LAUGH : STYLE_UNSUPPORTED. Sourire/rire facial et chronologie phonétique : non disponibles. Autres moteurs désactivés, aucun nouveau modèle téléchargé.",14f,true)
        }
        fold(page,"VOICE-04 · Diagnostic de vitesse") { body ->
            u.text(body,"Laboratoire indépendant. Mesures locales sans texte ni profil exporté. Lecture différée de référence. Les chiffres ne prouvent pas que le son a été entendu.",14f)
            val progressive=u.toggle(body,"Lecture progressive expérimentale · désactivée par défaut",c.progressiveEnabled)
            progressive.setOnCheckedChangeListener { _,enabled -> a.guard { c.progressive(enabled) } }
            u.text(body,"Diagnostic et Texte long uniquement ; Parler reste différé. API26–30 : repli différé obligatoire (streaming non qualifié). Prélecture 2 s ; 4 s maximum dans la file, PCM source limité à 20 s par job. Débit mesuré trop lent : différé avant le premier son, jamais double lecture.",14f,true)
            u.button(body,"Mesurer · phrase courte publique") { a.guard { c.speedDiagnostic("Bonjour à vous.") } }
            u.button(body,"Mesurer · deux phrases publiques") { a.guard { c.speedDiagnostic("Bonjour à vous. Le train arrive bientôt.") } }
            u.button(body,"Exporter les compteurs JSON expurgés") { a.speedExport() }
        }
        u.text(page,"Réglages avancés",30f);u.text(page,"Le mode courant reste simple. Ces réglages sont manuels et ne créent aucune émotion native.",14f,true)
        fold(page,"VOICE-04 · Texte long expérimental") { body ->
            u.text(body,"2–6 phrases complètes recommandées, chacune <=50 tokens/300 caractères ; entrée <=1800 caractères. Pas de suppression ni réécriture. Décimales, sigles et citations demandent vos points de coupe explicites. Les silences de rechargement peuvent être longs.",14f)
            val input=u.input(body,"Texte long local (en mémoire seulement)",c.longDraft,1800,multiline=true)
            input.filters=arrayOf(Voice04LongTextFilter { c.notice("Insertion refusée en entier : maximum 1800 caractères. Choisir des lots distincts ; aucun préfixe tronqué n’a été accepté.") })
            val cuts=u.check(body,"J’ai choisi les points de coupe : une phrase complète par ligne.",c.longManual)
            u.button(body,"Prévisualiser et vérifier tous les segments") { a.guard { c.prepareLong(input.text.toString(),cuts.isChecked) } }
            val preview=u.text(body,c.longPreview,14f)
            input.doAfterTextChanged { a.guard { c.editLong(it.toString());preview.text=c.longPreview } }
            cuts.setOnCheckedChangeListener { _,checked -> a.guard { c.editLong(input.text.toString(),checked);preview.text=c.longPreview } }
            u.button(body,"Lire le plan préparé") { a.confirm("Lire les segments préparés ?",c.longPreview+"\nLe mode progressif choisi dans Diagnostic est appliqué. Aucune modification de la voix.") { c.speakLong() } }
            u.button(body,"Reprendre après les jobs terminés") { a.confirm("Reprendre explicitement ?","Aucun job terminé ne sera rejoué. Le premier job incomplet recommencera depuis son début, même si une partie en a déjà été entendue. Même profil et réglages conservés.") { c.speakLong(true) } }
        }
        fold(page,"VOICE-04 · Émotions (expérimental)") { body ->
            u.text(body,"Neutre : voix de référence inchangée. Surprise, joie et tristesse ne sont pas encore des commandes acoustiques validées dans Pocket French 6L. Les intentions et pauses existantes restent distinctes des émotions audibles. Aucun filtre ajouté pour les simuler.",14f)
            for(intent in com.jarvis.voice.speed.EmotionalIntentV1.entries.filter { it!=com.jarvis.voice.speed.EmotionalIntentV1.NEUTRAL }) {
                val title=when(intent) {
                    com.jarvis.voice.speed.EmotionalIntentV1.JOY->"Joie"
                    com.jarvis.voice.speed.EmotionalIntentV1.SURPRISE->"Surprise"
                    com.jarvis.voice.speed.EmotionalIntentV1.SADNESS->"Tristesse"
                    com.jarvis.voice.speed.EmotionalIntentV1.CALM->"Calme"
                    com.jarvis.voice.speed.EmotionalIntentV1.SERIOUS->"Sérieux"
                    else->"Curiosité"
                }
                u.button(body,"$title · indisponible acoustiquement") {}.isEnabled=false
            }
            u.text(body,"Les modèles expressifs alternatifs nécessitent une qualification séparée sur matériel adapté. Aucun téléchargement ni transfert de B.",14f,true)
        }
        fold(page,"Synthèse · graine, LSD, température") { body ->
            val s=c.state.settings;val seed=u.input(body,"Graine aléatoire",s.seed.toString(),20)
            val steps=u.spinner(body,"Étapes LSD",listOf("1","2","3","5"),listOf(1,2,3,5).indexOf(s.steps))
            val temp=u.spinner(body,"Température",listOf("0.1","0.2","0.3","0.4","0.5","0.6"),listOf(.1,.2,.3,.4,.5,.6).indexOf(s.temperature))
            u.text(body,"EOS −4, queue officielle, 1 thread CPU. Aucun mode Pleine puissance sur téléphone.",14f,true)
            u.button(body,"Enregistrer ces réglages") { a.guard {
                val number=requireNotNull(seed.text.toString().toLongOrNull()) { "Saisir une graine entière valide." }
                c.update { it.copy(settings=it.settings.copy(seed=number,steps=steps.selectedItem.toString().toInt(),temperature=temp.selectedItem.toString().toDouble())) };c.notice("Réglages enregistrés explicitement. Régénérer A avant B si le contexte a changé.")
            } }
        }
        fold(page,"Comparaison A/B · même voix, intentions / pauses") { body ->
            u.text(body,"Même profil exact, texte, graine, LSD et température. A est NEUTRAL sans pauses. B utilise les options choisies sur Parler. Les intentions non prises en charge peuvent donner un audio identique.",14f)
            u.button(body,"Générer A NEUTRAL") { a.guard { c.generate("A") } }
            u.button(body,"Générer B avec mes options") { a.guard { c.generate("B") } }
            u.button(body,"Réécouter A") { a.guard { c.replay("A") } };u.button(body,"Réécouter B") { a.guard { c.replay("B") } }
        }
        fold(page,"Styles logiciels · comparaison publique neutre / Athéna") { body ->
            u.text(body,"Essai explicite : même profil, texte public fixe, graine et sampler. A sans pause ; B pause de 500 ms entre deux phrases explicites. Aucun effet émotionnel natif, aucun nouveau profil. Les PCM32 restent dans le cache privé et peuvent être effacés avec les réécoutes.",14f)
            u.button(body,"Générer style A neutre") { a.guard { c.generateStyleComparison("A") } }
            u.button(body,"Générer style B Athéna (pauses seulement)") { a.guard { c.generateStyleComparison("B") } }
            u.button(body,"Réécouter style A") { a.guard { c.replayStyle("A") } }
            u.button(body,"Réécouter style B") { a.guard { c.replayStyle("B") } }
        }
        fold(page,"Comparer deux profils de la même identité") { body ->
            val identity=c.currentIdentity();val profiles=identity?.profiles?.filter { it.status==ProfileStatus.VALID }.orEmpty()
            u.text(body,"Expérience distincte : changer de profil exige votre choix explicite. La voix principale n’est pas remplacée. Pas de pauses ajoutées pour cette comparaison de conditionnements.",14f)
            if(profiles.size<2) u.text(body,"Créer au moins deux profils de cette même identité pour cette comparaison. Un seul profil suffit pour utiliser JARVIS.")
            else {
                val names=profiles.map { p->"${identity!!.references.single { it.id==p.referenceId }.label} · ${p.id.take(8)}" }
                val ap=u.spinner(body,"Profil A",names);val bp=u.spinner(body,"Profil B",names,1)
                fun gen(slot: String,pid: String) { a.confirm("Comparer ce profil ?","Utiliser uniquement pour cet essai le profil $pid. La sélection principale reste inchangée.") { c.compareProfile(slot,pid,true) } }
                u.button(body,"Générer profil A") { a.guard { gen("A",profiles[ap.selectedItemPosition].id) } }
                u.button(body,"Générer profil B") { a.guard { gen("B",profiles[bp.selectedItemPosition].id) } }
                u.button(body,"Réécouter profil A") { a.guard { c.replayProfile("A") } };u.button(body,"Réécouter profil B") { a.guard { c.replayProfile("B") } }
            }
        }
        fold(page,"Noter la dernière écoute, en privé") { body ->
            u.text(body,"Évaluer seulement après écoute complète. Pour les profils : ressemblance ; pour les intentions : expressivité. Rien n’est prérempli.",14f)
            val scores=listOf("Naturel","Expressivité / ressemblance selon l’essai","Constance de timbre","Intelligibilité","Artefacts métalliques (0 = absents)").map { u.spinner(body,it,listOf("Non noté","0","1","2","3","4","5")) }
            val heard=u.check(body,"J’ai réellement entendu cet audio et connais la voix de référence.")
            u.button(body,"Enregistrer mes notes") { a.guard { require(scores.all { it.selectedItemPosition>0 }) { "Renseigner les cinq notes après écoute." };c.note(scores.map { it.selectedItem.toString().toInt() },heard.isChecked) } }
        }
        fold(page,"Références · annotation et proposition de 5 s") { body ->
            val identity=c.currentIdentity()
            if(identity==null) u.text(body,"Choisir d’abord une voix dans Mes voix.")
            else {
                u.button(body,"Gérer les références") { VoicesScreen(a).references(identity) }
                val refs=identity.references.filter { it.retained }
                if(refs.isNotEmpty()) {
                    val ref=u.spinner(body,"WAV privé source",refs.map { "${it.label} · ${it.id.take(8)}" })
                    val legal=u.check(body,"Consentement légal et même locuteur confirmés pour cette proposition.")
                    u.button(body,"Ajouter une proposition contiguë de 5 s") { a.guard {
                        require(legal.isChecked) { "Confirmer consentement et même locuteur." };val r=refs[ref.selectedItemPosition]
                        c.run("Analyse énergétique locale…") { token ->
                            val pcm=c.vault.readReference(identity.id,r.id,token);val region=com.jarvis.voice.pocket.ReferenceSelection.suggestFiveSeconds(pcm)
                            val rid=c.vault.addReference(identity.id,pcm.copyOfRange(region.first,region.last+1),"WAV",c.vault.referenceDigest(identity.id,r.id,token),region.first/24L,r.label,"Proposition contiguë de ${r.id}",identity.consentNote,true,true,true,token,sourceDurationMs=r.durationMs)
                            c.draft { WizardDraft(step=VoiceStep.REVIEW,identityId=identity.id.value,referenceId=rid,name=identity.name,note=identity.consentNote,startMs=region.first/24L,durationMs=5000,sourceMs=r.durationMs,sourceFormat="WAV",label=r.label,keepWav=true) }
                            "Proposition ajoutée sans remplacement. Analyse d’énergie seulement, pas de reconnaissance de locuteur."
                        }
                    } }
                }
            }
        }
        val diag=u.card(page,"État du téléphone et diagnostics")
        u.text(diag,DeviceMetrics.summary(a),13f,true)
        u.text(diag,c.diagnostics+"\n"+c.metrics,13f,true).id=R.id.live_diagnostics
        u.button(diag,"Tester le lecteur (signal synthétique)") { c.testSignal() }
        u.button(page,"Installer le ZIP FP32 existant") { a.choose(200) }
        u.button(page,"Licences et confidentialité") { MaterialAlertDialogBuilder(a).setTitle("JARVIS VOICE-04 · Laboratoire")
            .setMessage(a.assets.open("THIRD_PARTY_NOTICES.txt").bufferedReader().use { it.readText() }).setPositiveButton("Fermer",null).showPrivate() }
        return u.page(page)
    }
}
