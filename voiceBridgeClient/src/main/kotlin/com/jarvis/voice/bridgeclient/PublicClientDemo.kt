package com.jarvis.voice.bridgeclient
import java.util.UUID

fun main() {
    val client=CoreSpeechClientPort(PublicSpeechTransport { r -> SpeechProgressEnvelope(r.key,LogicalSpeechPhase.REFUSED,SpeechBlockCode.NATIVE_AUTHORITY_UNAVAILABLE) })
    val request=PublicSpeechJobRequestV2(SpeechCorrelation(UUID.randomUUID().toString(),UUID.randomUUID().toString(),1),PublicUtterance.HELLO)
    val started=System.nanoTime()
    val receipt=client.submit(request)
    val elapsedMs=(System.nanoTime()-started)/1e6
    check(receipt.mode==SpeechConnectionMode.SPEECH_BLOCKED && receipt.nativeObservation==NativeSpeechObservation.UNKNOWN)
    println("VOICE07 SYNTHETIC_IN_MEMORY_TRANSPORT_ONLY ${receipt.phase} ${receipt.code}; audio=false; nativeReleaseVerified=false; coreConnected=false; elapsedMs=$elapsedMs")
}
