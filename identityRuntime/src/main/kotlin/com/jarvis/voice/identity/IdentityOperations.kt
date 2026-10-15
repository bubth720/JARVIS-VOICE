package com.jarvis.voice.identity

import com.jarvis.voice.pocket.*
import java.io.File

object IdentityAdmission {
    fun requireHeavy(availableBytes: Long,lowMemory: Boolean,thermalStatus: Int) {
        require(thermalStatus<3) { "Protection thermique : laisser refroidir le téléphone" }
        require(!lowMemory && availableBytes>=2048L*1048576) { "Au moins 2 Gio de RAM disponibles requis" }
    }
}
object IdentityEncoding {
    /** The callback opens/closes exactly one official encoder. Cancellation precedes any vault commit. */
    fun create(vault: VoiceIdentityVault,id: VoiceIdentityId,referenceId: String,cancel: CancellationToken,
        encode: (FloatArray,CancellationToken)->PersonalVoiceProfile): String {
        cancel.check();val pcm=vault.readReference(id,referenceId,cancel)
        val value=encode(pcm,cancel);cancel.check()
        return vault.saveProfile(id,referenceId,value,cancel)
    }
    fun official(vault: VoiceIdentityVault,id: VoiceIdentityId,referenceId: String,pack: File,cancel: CancellationToken)=
        create(vault,id,referenceId,cancel) { pcm,token -> FP32VoiceEncoder(pack,token).use { it.create(pcm,token) } }
}
