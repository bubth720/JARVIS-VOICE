package com.jarvis.voice.studio
import com.jarvis.voice.identity.*
import com.jarvis.voice.identity.VoiceProfile
import com.jarvis.voice.pocket.*

/** No automatic activation. Library edits and encoding never select a replacement voice. */
class StudioLibrary(val vault: VoiceIdentityVault,val choice: StudioProfileChoice) {
    fun create(draft: WizardDraft,consent: Boolean,cancel: CancellationToken): WizardDraft {
        require(consent) { "Confirmer le droit de cloner cette voix." };require(draft.name.isNotBlank()) { "Donner un nom local à cette voix." }
        if(draft.identityId!=null) {
            val id=VoiceIdentityId(draft.identityId);vault.rename(id,draft.name,cancel)
            return draft.copy(step=VoiceStep.AUDIO)
        }
        val previous=vault.selected();val id=vault.create(draft.name,draft.note.ifBlank { "Consentement local explicite" },true,cancel)
        // The user can prepare a new identity while keeping the old speaking voice selected.
        if(previous!=null) vault.select(previous,CancellationToken())
        return draft.copy(identityId=id.value,step=VoiceStep.AUDIO)
    }
    fun append(draft: WizardDraft,pcm: FloatArray,sourceSha: String,legal: Boolean,sameSpeaker: Boolean,cancel: CancellationToken): WizardDraft {
        require(legal && sameSpeaker) { "Confirmer le consentement et le même locuteur pour ce fichier." }
        val id=VoiceIdentityId(requireNotNull(draft.identityId));val source=requireNotNull(draft.sourceMs)
        WizardPolicy.requireRegion(source,draft.startMs,draft.durationMs)
        require(pcm.size.toLong()==draft.durationMs*24 && pcm.all { it.isFinite() }) { "Extrait incomplet ou invalide ; aucune référence ajoutée." }
        val rid=vault.addReference(id,pcm,requireNotNull(draft.sourceFormat),sourceSha,draft.startMs,draft.label,draft.comment,
            draft.note.ifBlank { "Consentement local explicite" },true,true,draft.keepWav,cancel,sourceDurationMs=source)
        return draft.copy(referenceId=rid,step=VoiceStep.ENCODE)
    }
    fun encode(draft: WizardDraft,cancel: CancellationToken,encoder: (FloatArray,CancellationToken)->PersonalVoiceProfile): String {
        val id=VoiceIdentityId(requireNotNull(draft.identityId));val ref=requireNotNull(draft.referenceId)
        return IdentityEncoding.create(vault,id,ref,cancel,encoder)
    }
    fun activate(id: VoiceIdentityId,pid: String,cancel: CancellationToken,neutral: Boolean=false) {
        val p=vault.list().single { it.id==id }.profiles.single { it.id==pid }
        vault.binding().resolve(id,pid,cancel);cancel.check()
        vault.activate(id,pid,neutral,cancel);vault.select(id,cancel);choice.confirm(p)
    }
    fun activeConfirmed(): VoiceProfile {
        val id=requireNotNull(vault.selected()) { "Aucune voix active. Ouvrir Mes voix pour importer ou créer une voix." }
        val i=vault.list().single { it.id==id };val pid=requireNotNull(i.activeProfiles[PocketIdentity.bindingKey]) { "Choisir un profil compatible dans Mes voix." }
        return i.profiles.single { it.id==pid }.also(choice::requireConfirmed)
    }
}
