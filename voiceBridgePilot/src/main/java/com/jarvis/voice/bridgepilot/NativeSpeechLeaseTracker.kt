package com.jarvis.voice.bridgepilot
import com.jarvis.voice.bridgeclient.NativeSpeechObservation

/** CORE02 events/UNI01 JSON/timeout/stop booleans cannot mint a native admission proof. */
internal class NativeSpeechLeaseTracker {
    val observation get()=NativeSpeechObservation.UNKNOWN
    fun logicalJobEnded()=observation
    fun cancellationRequested()=observation
}
