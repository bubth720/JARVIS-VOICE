package com.jarvis.voice.pocketprobe

/** Safe structured diagnostic: never retains exception messages, paths, URIs or audio. */
class ReferenceFailure(
    val stage: String,
    val exceptionType: String,
    val expectedSamples: Long = 0,
    val obtainedSamples: Long = 0,
    val elapsedMs: Long = 0
) : IllegalArgumentException("$stage/$exceptionType") {
    fun diagnostic() = "$stage • $exceptionType • échantillons $obtainedSamples/$expectedSamples • ${elapsedMs} ms"
}

internal inline fun <T> referenceStep(stage: String, block: () -> T): T = try {
    block()
} catch (t: java.util.concurrent.CancellationException) {
    throw t
} catch (t: ReferenceFailure) {
    throw t
} catch (t: Exception) {
    throw ReferenceFailure(stage, t.javaClass.simpleName)
}
