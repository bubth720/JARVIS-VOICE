package com.jarvis.voice.pocket

/** Rendering/measurement boundary for future independently validated engines and their own profiles. */
interface ExperimentalSpeechBackend : AutoCloseable {
    val engineId: String
    val modelLoadMs: Double
    fun generate(raw: String,cancel: CancellationToken,seed: Long=42,wholeSentence: Boolean=true,onPcm: (FloatArray) -> Unit): GenerationMetrics
}
