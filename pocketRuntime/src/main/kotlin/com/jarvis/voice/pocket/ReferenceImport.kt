package com.jarvis.voice.pocket

/** On-device interfaces. MP3 decoding is available; profile encoding remains unavailable. */
interface ReferenceImporter {
    fun inspectLocalReference(source: LocalReferenceSource): ReferenceInspection
}
data class LocalReferenceSource(val privateId: String)
data class ReferenceInspection(val durationMs: Long, val sampleRate: Int, val channels: Int)
interface ReferenceDecoder {
    fun decodeMono24k(source: LocalReferenceSource, startMs: Long, durationMs: Long): MonoPcmReference
}
data class MonoPcmReference(val privateId: String, val samples: Long, val sampleRate: Int = 24000)
interface LocalProfileEncoder {
    fun encode(reference: MonoPcmReference, modelFingerprint: String): VoiceProfile.Local
}
sealed interface VoiceProfile {
    data class Preset(val id: String) : VoiceProfile
    data class Local(val privateId: String, val modelFingerprint: String) : VoiceProfile
}
object CloningCapability {
    const val available = false
    const val reason = "Pack actuel sans encodeur ; encodeur candidat non validé contre le modèle français 6 couches. Clonage indisponible"
}
