package com.jarvis.voice.voicestudio
import android.view.View
import android.widget.LinearLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jarvis.voice.identity.*
import com.jarvis.voice.identity.VoiceProfile
import com.jarvis.voice.studio.*
import com.jarvis.voice.pocket.CancellationToken

class VoicesScreen(private val a: StudioActivity) {
    private val u=a.ui;private val c=a.controller
    fun view(): View {
        val page=u.column();u.text(page,"Mes voix",30f);u.text(page,"Une bibliothèque privée. Aucun profil remplacé automatiquement.",14f,true)
        u.button(page,"Ajouter une voix",primary=true) { c.navigate(StudioScreen.CREATE) }
        val rows=c.vault.list()
        if(rows.isEmpty()) u.text(page,"Votre bibliothèque est vide. Restaurer une sauvegarde chiffrée ou suivre l’assistant.")
        for(i in rows) {
            val active=i.activeProfiles[PocketIdentity.bindingKey]
            val card=u.card(page,i.name,"${i.references.size} extrait(s) · ${i.profiles.size} profil(s)\n"+if(c.vault.selected()==i.id) "Voix sélectionnée · profil ${active?.take(8) ?: "à choisir"}" else "Conservée localement")
            u.button(card,"Choisir un profil") { showProfiles(i) }
            u.button(card,"Gérer cette voix") { manage(i) }
        }
        u.text(page,"Vous venez de 005I ? Garder cette application et sauvegarder votre identité B en .jvi chiffré. Restaurer ici sans réencodage.",14f,true)
        return u.page(page)
    }
    fun showProfiles(i: VoiceIdentity) {
        if(i.profiles.isEmpty()) { c.notice("Aucun profil créé pour cette voix. Continuer l’assistant avec un extrait valide.");return }
        val labels=i.profiles.map { p -> "${i.references.single { it.id==p.referenceId }.label} · ${p.id.take(8)} · ${p.status}" }
        MaterialAlertDialogBuilder(a).setTitle("Profil de ${i.name}").setItems(labels.toTypedArray()) { _,n ->
            val p=i.profiles[n]
            MaterialAlertDialogBuilder(a).setTitle("Choisir ce profil ?").setMessage("${labels[n]}\nUUID ${p.id}\nSHA256 ${p.sha256}\nPack ${p.packId}\nLes étiquettes décrivent la référence ; elles ne commandent pas une émotion.")
                .setNegativeButton("Annuler",null).setNeutralButton("Gestion") { _,_->profileMenu(i,p) }
                .setPositiveButton("Utiliser cette voix") { _,_->a.guard { c.activate(i.id,p.id);c.navigate(StudioScreen.SPEAK) } }.showPrivate()
        }.showPrivate()
    }
    private fun profileMenu(i: VoiceIdentity,p: VoiceProfile) {
        val options=arrayOf("Choisir comme neutre principal","Désactiver sans supprimer","Réactiver sans sélectionner","Supprimer ce profil seulement")
        MaterialAlertDialogBuilder(a).setTitle("Gestion du profil").setItems(options) { _,n -> a.guard {
            when(n) {
                0->c.activate(i.id,p.id,true)
                1->a.confirm("Désactiver ?","Aucun autre profil ne sera choisi automatiquement.") { c.run("Désactivation…") { cancel->c.vault.deactivate(i.id,p.id,cancel);"Profil désactivé." } }
                2->c.run("Réactivation…") { cancel->c.vault.enable(i.id,p.id,cancel);"Profil réactivé, sans sélection automatique." }
                3->a.confirm("Supprimer ce profil ?","Ses caches seulement seront effacés. Les autres profils, références et anciennes applications restent conservés.") { c.removeProfile(i.id,p.id) }
            }
        } }.showPrivate()
    }
    private fun manage(i: VoiceIdentity) {
        val options=arrayOf("Sauvegarder en archive chiffrée","Ajouter un extrait à cette identité","Renommer localement","Voir les références","Effacer les réécoutes seulement","Supprimer cette voix de 005J")
        MaterialAlertDialogBuilder(a).setTitle(i.name).setItems(options) { _,n -> a.guard {
            when(n) {
                0->a.export(i.id)
                1->{ c.draft { WizardDraft(step=VoiceStep.AUDIO,identityId=i.id.value,name=i.name,note=i.consentNote) };c.navigate(StudioScreen.CREATE) }
                2->{ val col=u.column();val name=u.input(col,"Nom local",i.name,80);MaterialAlertDialogBuilder(a).setTitle("Renommer").setView(col).setNegativeButton("Annuler",null).setPositiveButton("Enregistrer") { _,_->a.guard { val value=name.text.toString();c.run("Renommage…") { cancel->c.vault.rename(i.id,value,cancel);"Nom local enregistré." } } }.showPrivate() }
                3->references(i)
                4->a.confirm("Effacer les réécoutes ?","Aucun profil ni extrait ne sera supprimé.") { c.clearAudio(i.id) }
                5->a.confirm("Supprimer cette voix ?","Supprimer ses données 005J uniquement ? Garder une sauvegarde chiffrée. 005I et les autres voix resteront intactes.") { c.removeIdentity(i.id) }
            }
        } }.showPrivate()
    }
    fun references(i: VoiceIdentity) {
        if(i.references.isEmpty()) { c.notice("Aucun extrait dans cette identité.");return }
        val names=i.references.map { "${it.label} · ${it.durationMs/1000.0} s · WAV ${if(it.retained) "présent" else "absent"} · ${it.id.take(8)}" }
        MaterialAlertDialogBuilder(a).setTitle("Références privées").setItems(names.toTypedArray()) { _,n -> val r=i.references[n]
            MaterialAlertDialogBuilder(a).setTitle(names[n]).setMessage(r.analysis+"\n"+r.comment)
                .setNegativeButton("Fermer",null).setPositiveButton("Vérifier / annoter") { _,_->
                    c.draft { WizardDraft(step=VoiceStep.REVIEW,identityId=i.id.value,referenceId=r.id,name=i.name,note=i.consentNote,startMs=r.startMs,durationMs=r.durationMs,sourceMs=r.sourceDurationMs,sourceFormat=r.sourceFormat,label=r.label,keepWav=r.keepAfterEncoding,comment=r.comment) };c.navigate(StudioScreen.CREATE)
                }.setNeutralButton("Supprimer…") { _,_->
                    MaterialAlertDialogBuilder(a).setTitle("Suppression séparée").setItems(arrayOf("WAV seulement, profils conservés","Métadonnées si aucun profil lié")) { _,k ->
                        a.confirm("Confirmer la suppression ?","Cette action ne concerne que cette référence 005J.") { c.run("Suppression de référence…") { cancel -> if(k==0) c.vault.removeReferenceAudio(i.id,r.id,true,cancel) else c.vault.removeReference(i.id,r.id,true,cancel);"Référence mise à jour ; profils existants conservés." } }
                    }.showPrivate()
                }.showPrivate()
        }.showPrivate()
    }
}
