package com.jarvis.voice.sdk

import com.jarvis.voice.identity.VoiceIdentityVault
import java.nio.file.Files
import java.io.File

/** Public synthetic orchestration example; NEVER a voice-quality/native inference claim. */
fun main() {
    val root=Files.createTempDirectory("voice-sdk-demo-").toFile()
    try {
        val actual=SpeechSessionRepository(VaultQualitySpeechEngine(VoiceIdentityVault(File(root,"vault")),File(root,"absent")))
        println("REAL_ADAPTER_MISSING_PACK="+actual.open(RecordingOutput()).speak(SpeechQueue(listOf(request()))))
        val repo=SpeechSessionRepository(LocalSpeechEnginePort { _,_ -> RenderedSpeech(FloatArray(240) { .1f },true) })
        val session=repo.open(RecordingOutput()) { println("SYNTHETIC_STATE=$it") }
        println("SYNTHETIC_ONLY="+session.speak(SpeechQueue(listOf(request(),request()))))
        session.close()
        println("NO_NATIVE_GENERATION_OR_AUDIBILITY_VERIFIED")
    } finally { root.deleteRecursively() }
}
