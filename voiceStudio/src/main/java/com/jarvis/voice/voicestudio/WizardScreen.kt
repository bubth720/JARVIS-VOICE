package com.jarvis.voice.voicestudio
import android.view.View
import android.widget.LinearLayout
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.jarvis.voice.identity.*
import com.jarvis.voice.studio.*

/** One step per screen. Back never deletes a committed reference or a profile. */
class WizardScreen(private val a: StudioActivity) {
    private val u=a.ui;private val c=a.controller
    private fun go(step: VoiceStep) { c.draft { it.copy(step=step) };c.navigate(StudioScreen.CREATE) }
    fun view(): View {
        val page=u.column();val d=c.state.wizard;val identity=c.wizardIdentity()
        u.text(page,"Créer une voix",30f)
        u.text(page,"Étape ${d.step.ordinal+1} sur 7 · ${listOf("Choisir","Identité et consentement","Ajouter des audios","Vérifier l’extrait","Créer le profil","Essayer et choisir","Sauvegarder")[d.step.ordinal]}",14f,true)
        page.addView(LinearProgressIndicator(a).apply { max=7;setProgress(d.step.ordinal+1,false);contentDescription="Étape ${d.step.ordinal+1} sur 7" },LinearLayout.LayoutParams(-1,u.dp(8)).apply { bottomMargin=u.dp(20) })
        when(d.step) {
            VoiceStep.CHOOSE -> {
                u.text(page,"Votre voix, à votre rythme.",25f)
                u.text(page,"Vous avez déjà une voix 005I ? Restaurer sa sauvegarde chiffrée conserve exactement son profil. Sinon, un seul bon extrait suffit pour commencer.")
                u.button(page,"Importer une voix existante",R.id.restore_voice,true) { a.choose(204) }
                u.button(page,"Créer une nouvelle voix") {
                    if(d.identityId==null) go(VoiceStep.CONSENT) else a.confirm("Nouvelle identité ?","Les profils précédents restent dans Mes voix.") { c.source.delete();c.draft { WizardDraft(step=VoiceStep.CONSENT) };c.navigate(StudioScreen.CREATE) }
                }
                if(identity!=null) u.button(page,"Reprendre cette préparation") { go(if(identity.profiles.isNotEmpty()) VoiceStep.TRY else VoiceStep.AUDIO) }
                if(!c.hasModel()) u.button(page,"Importer le modèle FP32 existant") { a.choose(200) }
            }
            VoiceStep.CONSENT -> {
                val card=u.card(page,"Un nom pour cette identité","Une seule identité pour tous ses extraits. Votre voix actuelle reste sélectionnée pendant la préparation.")
                val name=u.input(card,"Nom local de la voix",d.name,80,R.id.voice_name)
                val note=u.input(card,"Note de consentement (facultative)",d.note,500)
                name.doAfterTextChanged { value -> c.draft { it.copy(name=value.toString()) } };note.doAfterTextChanged { value->c.draft { it.copy(note=value.toString()) } }
                val legal=u.check(card,"C’est ma voix, ou j’ai son consentement explicite et légal pour ce clonage local.",id=R.id.consent)
                u.text(card,"Aucun enregistrement, mot de passe ou profil n’est transmis. Ne pas imiter une personne sans son accord.",14f,true)
                u.button(page,if(d.identityId==null) "Créer cette identité et continuer" else "Continuer avec cette identité",R.id.wizard_next,true) { a.guard { c.createIdentity(legal.isChecked) } }
            }
            VoiceStep.AUDIO -> {
                u.text(page,identity?.name ?: "Identité à retrouver",25f)
                u.text(page,"Ajouter un MP3 ou WAV local à la fois. Chaque fichier garde son propre extrait et son profil. Minimum 5 secondes ; aucune fusion de fichiers.")
                u.button(page,"Ajouter un audio local",R.id.audio_add,true) { a.choose(201) }
                val refs=identity?.references.orEmpty()
                if(refs.isNotEmpty()) {
                    u.text(page,"${refs.size} extrait(s) déjà validé(s) dans cette même identité.",16f)
                    val names=refs.map { "${it.label} · ${it.durationMs/1000.0} s · ${it.id.take(8)}" }
                    val selected=u.spinner(page,"Ou reprendre un extrait existant",names)
                    u.button(page,"Vérifier cet extrait",R.id.wizard_next) { val r=refs[selected.selectedItemPosition]
                        c.draft { it.copy(step=VoiceStep.REVIEW,referenceId=r.id,startMs=r.startMs,durationMs=r.durationMs,sourceMs=r.sourceDurationMs,sourceFormat=r.sourceFormat,label=r.label,keepWav=r.keepAfterEncoding,comment=r.comment) };c.navigate(StudioScreen.CREATE)
                    }
                }
            }
            VoiceStep.REVIEW -> {
                val ref=identity?.references?.singleOrNull { it.id==d.referenceId }
                u.text(page,"Écoutez avant de créer.",25f)
                u.text(page,"${d.sourceFormat ?: "Audio"} · durée réelle ${d.sourceMs?.div(1000.0) ?: "inconnue"} s\nExtrait contigu : ${d.durationMs/1000.0} s à partir de ${d.startMs/1000.0} s.")
                val start=u.input(page,"Début en secondes",(d.startMs/1000).toString(),3)
                val duration=u.input(page,"Durée : 5 à 15 secondes",(d.durationMs/1000).toString(),2)
                start.isEnabled=ref==null;duration.isEnabled=ref==null
                fun region() {
                    if(ref!=null) return
                    val b=start.text.toString().toLongOrNull();val n=duration.text.toString().toLongOrNull()
                    require(b!=null && n!=null) { "Saisir un début et une durée entiers en secondes." }
                    WizardPolicy.requireRegion(requireNotNull(d.sourceMs),b*1000,n*1000);c.draft { it.copy(startMs=b*1000,durationMs=n*1000) }
                }
                if(ref==null || ref.retained) u.button(page,"Écouter cet extrait") { a.guard { region();c.previewReference() } }
                else u.text(page,"WAV absent dans cette sauvegarde ou supprimé après encodage. Les profils déjà créés restent utilisables. Réimporter la source pour réencoder.",14f,true)
                if(ref!=null) u.text(page,ref.analysis,14f,true)
                val label=u.spinner(page,"Étiquette manuelle de la référence",ReferenceLabel.entries.map { it.name },d.label.ordinal)
                u.text(page,"L’étiquette décrit l’extrait. Elle ne commande pas une émotion.",12f,true)
                val comment=u.input(page,"Commentaire privé",d.comment,1000)
                val keep=u.check(page,"Conserver ce WAV après encodage pour sauvegarde et réencodage.",d.keepWav)
                if(ref==null) {
                    val legal=u.check(page,"Je confirme le droit de cloner cette voix pour ce fichier.",id=R.id.consent)
                    val same=u.check(page,"Un seul locuteur : c’est la même personne que cette identité.")
                    u.button(page,"Valider cet extrait",R.id.reference_save,true) { a.guard {
                        region();c.draft { it.copy(label=ReferenceLabel.valueOf(label.selectedItem.toString()),comment=comment.text.toString(),keepWav=keep.isChecked) };c.saveReference(legal.isChecked,same.isChecked)
                    } }
                } else {
                    u.button(page,"Enregistrer l’annotation") { val l=ReferenceLabel.valueOf(label.selectedItem.toString());val note=comment.text.toString()
                        c.run("Annotation locale…") { token -> c.vault.annotateReference(requireNotNull(identity).id,ref.id,l,note,token);c.draft { it.copy(label=l,comment=note) };"Annotation enregistrée. Le choix de conservation d’un WAV déjà ajouté reste inchangé." }
                    }
                    keep.isEnabled=false
                    u.button(page,"Continuer vers le profil",R.id.wizard_next,true) { go(VoiceStep.ENCODE) }
                }
            }
            VoiceStep.ENCODE -> {
                val existing=identity?.profiles?.any { it.referenceId==d.referenceId }==true
                u.text(page,if(existing) "Votre profil est prêt." else "Créer un profil individuel.",25f)
                u.text(page,"Le téléphone utilise l’encodeur officiel hors ligne. Un extrait, un profil. Aucun mélange des états vocaux et aucune création en lot imposée.")
                if(!c.hasModel()) u.button(page,"Importer le ZIP FP32 existant") { a.choose(200) }
                u.button(page,if(existing) "Essayer ce profil" else "Créer ce profil",R.id.profile_encode,true) { a.guard { c.encodeReference() } }
                u.text(page,"Arrêter reste disponible. Si le téléphone chauffe ou manque de mémoire, laisser refroidir ; les éléments validés sont conservés.",14f,true)
            }
            VoiceStep.TRY -> {
                val profiles=identity?.profiles.orEmpty()
                u.text(page,"C’est vous qui choisissez.",25f)
                if(profiles.isEmpty()) u.text(page,"Aucun profil dans cette identité. Revenir à l’étape de création ou restaurer une archive contenant des profils.")
                else {
                    val names=profiles.map { p -> "${identity!!.references.single { it.id==p.referenceId }.label} · ${p.id.take(8)} · ${p.status}" }
                    val active=identity!!.activeProfiles[PocketIdentity.bindingKey]
                    val pick=u.spinner(page,"Profil à confirmer",names,profiles.indexOfFirst { it.id==active }.coerceAtLeast(0),R.id.profile_select)
                    u.button(page,"Utiliser ce profil",R.id.activate_profile) { val p=profiles[pick.selectedItemPosition]
                        a.confirm("Confirmer cette voix ?","Profil ${p.id}\nSHA256 ${p.sha256}\nPack ${p.packId}\nAucun profil ne sera réencodé ou remplacé.") { c.activate(identity.id,p.id) }
                    }
                    val phrase=u.input(page,"Phrase de test",c.state.text,300,R.id.phrase,true);phrase.doAfterTextChanged { c.text(it.toString()) }
                    u.button(page,"Parler avec le profil confirmé",R.id.talk,true) { a.guard { require(c.confirmedProfile()?.identityId==identity.id) { "Confirmer un profil de cette identité avant l’essai." };c.generate() } }.isEnabled=c.confirmedProfile()?.identityId==identity.id && c.hasModel()
                    if(c.hasReplay()) u.button(page,"Réécouter",R.id.replay) { a.guard { c.replay() } }
                    u.button(page,"Comparer deux profils (avancé)") { c.navigate(StudioScreen.ADVANCED) }
                    u.button(page,"Ajouter un autre audio, même identité") { c.draft { it.copy(step=VoiceStep.AUDIO,referenceId=null,sourceMs=null,sourceFormat=null) };c.navigate(StudioScreen.CREATE) }
                    u.button(page,"Continuer vers la sauvegarde",R.id.wizard_next,true) { a.guard { require(c.confirmedProfile()?.identityId==identity.id) { "Choisir et confirmer le profil principal d’abord." };go(VoiceStep.BACKUP) } }
                }
            }
            VoiceStep.BACKUP -> {
                u.text(page,"Gardez votre voix en sécurité.",25f)
                u.text(page,"Sauvegarde facultative, uniquement chiffrée. Les profils restent exacts. Les WAV ne sont inclus que si vous le choisissez et s’ils sont encore présents. Conserver le mot de passe séparément.")
                u.button(page,"Sauvegarder ma voix en .jvi",primary=true) { a.guard { a.export(requireNotNull(identity).id) } }
                u.button(page,"Terminer et revenir à Parler",R.id.wizard_next) { c.navigate(StudioScreen.SPEAK) }
                u.button(page,"Restaurer une autre sauvegarde") { a.choose(204) }
            }
        }
        if(d.step!=VoiceStep.CHOOSE) u.button(page,"Retour",R.id.wizard_back) { go(WizardPolicy.back(d.step)) }
        u.button(page,"Autre parcours…") {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(a).setTitle("Assistant").setItems(arrayOf("Importer une archive existante","Commencer une nouvelle identité","Revenir à Mes voix")) { _,n ->
                when(n) { 0->a.choose(204);1->a.confirm("Nouvelle identité ?","Les profils et références validés restent dans Mes voix. Seul le brouillon courant sera remplacé.") { c.source.delete();c.draft { WizardDraft(step=VoiceStep.CONSENT) };c.navigate(StudioScreen.CREATE) };2->c.navigate(StudioScreen.VOICES) }
            }.showPrivate()
        }
        return u.page(page)
    }
}
