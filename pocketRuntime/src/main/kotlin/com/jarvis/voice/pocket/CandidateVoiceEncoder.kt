package com.jarvis.voice.pocket

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.security.MessageDigest

/** Private validation candidate. Not exposed by the APK or accepted by ModelPack.
 * Real Mimi+BOS conditioning and 18 FlowLM states, never a renamed WAV or preset.
 * The UI remains disabled until independent recurrence and Android are validated.
 */
internal class CandidateVoiceEncoder(root: File, cancel: CancellationToken) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val opened = mutableListOf<OrtSession>()
    private val encoder: OrtSession
    private val main: OrtSession

    init {
        val lock = JSONObject(requireNotNull(javaClass.getResourceAsStream("/cloning-candidate-lock.json"))
            .bufferedReader().use { it.readText() })
        require(!lock.getBoolean("androidImportEnabled")) { "Validation privée requise" }
        try {
            val files = lock.getJSONObject("files")
            for (name in files.keys()) {
                cancel.check()
                val path = File(root, name)
                val spec = files.getJSONObject(name)
                require(path.isFile && path.length() == spec.getLong("sizeBytes")) { "Candidat encodeur incomplet" }
                val digest = MessageDigest.getInstance("SHA-256")
                path.inputStream().use { input ->
                    val bytes = ByteArray(65536)
                    while (true) {
                        cancel.check()
                        val count = input.read(bytes)
                        if (count < 0) break
                        digest.update(bytes, 0, count)
                    }
                }
                require(digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } == spec.getString("sha256")) { "SHA-256 candidat incorrect" }
            }
            fun session(name: String) = OrtSession.SessionOptions().use { options ->
                options.setIntraOpNumThreads(1); options.setInterOpNumThreads(1)
                options.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
                env.createSession(File(root, name).absolutePath, options).also { opened.add(it) }
            }
            encoder = session("mimi_encoder_with_bos.onnx")
            main = session("flow_lm_main_fp32.onnx")
            require(encoder.inputNames == setOf("audio"))
            require(main.inputNames == (0 until 18).map { "state_$it" }.toSet() + setOf("sequence", "text_embeddings"))
        } catch (failure: Throwable) { close(); throw failure }
    }

    /** Mono 24 kHz PCM already decoded locally. Returns compact states; no file/network I/O. */
    fun encode(pcm: FloatArray, cancel: CancellationToken): Map<String, VoiceTensor> {
        require(pcm.size in 120000..360000 && pcm.all { it.isFinite() }) { "Extrait mono 24 kHz de 5 à 15 secondes requis" }
        val frames = 1 + (pcm.size + 1919) / 1920 // one BOS, exactly once
        return cancel.withOptions { options ->
            OnnxTensor.createTensor(env, FloatBuffer.wrap(pcm), longArrayOf(1, 1, pcm.size.toLong())).use { audio ->
                encoder.run(mapOf("audio" to audio), options).use { encoded ->
                    val prompt = encoded.get("conditioning_with_bos").get() as OnnxTensor
                    require(prompt.info.shape.contentEquals(longArrayOf(1, frames.toLong(), 1024))) { "Forme encodeur incompatible" }
                    val promptValues = prompt.floatBuffer
                    while (promptValues.hasRemaining()) require(promptValues.get().isFinite()) { "Conditionnement non fini" }
                    cancel.check()
                    val initial = linkedMapOf<String, OnnxTensor>()
                    try {
                        for (layer in 0 until 6) {
                            for (key in 0..1) initial["state_${layer * 3 + key}"] = OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(0)), longArrayOf(1))
                            initial["state_${layer * 3 + 2}"] = OnnxTensor.createTensor(env, FloatBuffer.wrap(FloatArray(2 * 1024 * 16 * 64)), longArrayOf(2, 1, 1024, 16, 64))
                        }
                        OnnxTensor.createTensor(env, FloatBuffer.wrap(FloatArray(0)), longArrayOf(1, 0, 32)).use { empty ->
                            main.run(initial + mapOf("sequence" to empty, "text_embeddings" to prompt), options).use { result ->
                                val state = linkedMapOf<String, VoiceTensor>()
                                for (layer in 0 until 6) {
                                    cancel.check()
                                    val module = "transformer.layers.$layer.self_attn/"
                                    for ((key, index) in listOf("offset" to 0, "pad" to 1)) {
                                        val tensor = result.get("next_state_${layer * 3 + index}").get() as OnnxTensor
                                        require(tensor.info.shape.contentEquals(longArrayOf(1)))
                                        val value = tensor.longBuffer.get(0)
                                        require(value == if (key == "offset") frames.toLong() else 0L) { "Offset ou padding incompatible" }
                                        state[module + key] = VoiceTensor(1, longArrayOf(1), ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array())
                                    }
                                    val tensor = result.get("next_state_${layer * 3 + 2}").get() as OnnxTensor
                                    require(tensor.info.shape.contentEquals(longArrayOf(2, 1, 1024, 16, 64)))
                                    val values = tensor.floatBuffer
                                    val block = frames * 16 * 64
                                    val compact = ByteBuffer.allocate(2 * block * 4).order(ByteOrder.LITTLE_ENDIAN)
                                    for (kv in 0..1) for (position in 0 until block) {
                                        val value = values.get(kv * 1024 * 16 * 64 + position)
                                        require(value.isFinite()) { "Cache vocal non fini" }
                                        compact.putFloat(value)
                                    }
                                    state[module + "cache"] = VoiceTensor(0, longArrayOf(2, 1, frames.toLong(), 16, 64), compact.array())
                                }
                                cancel.check()
                                state
                            }
                        }
                    } finally { initial.values.forEach { it.close() } }
                }
            }
        }
    }

    override fun close() { opened.asReversed().forEach { it.close() }; opened.clear() }
}
