package com.jarvis.voice.pocket

import ai.onnxruntime.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.util.Random
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/** Experimental official FP32 only. A bound personal/public FP32 state is mandatory. */
class QualityEngine(private val root: File, private val profile: PersonalVoiceProfile, val threads: Int = 1, private val loadCancel: CancellationToken = CancellationToken(), private val sampling: QualitySampling = QualitySampling()) : ExperimentalSpeechBackend {
    override val engineId="pocket-french-6l-fp32-lsd"
    private val loadStart = System.nanoTime()
    private val bundle = ExperimentalModelPack.verify(root, loadCancel::isCancelled)
    private val env = OrtEnvironment.getEnvironment(OrtLoggingLevel.ORT_LOGGING_LEVEL_ERROR)
    private val opened = mutableListOf<OrtSession>()
    private val frontend = FrenchFrontend(JSONObject(File(root, "tokenizer.json").readText()))
    private val voice = profile.tensors.also { PersonalVoiceCodec.validate(profile) }
    private val text: OrtSession
    private val main: OrtSession
    private val flow: OrtSession
    private val decoder: OrtSession
    override val modelLoadMs: Double

    init {
        require(threads == 1)
        try {
            text = session("text_conditioner_fp32.onnx")
            main = session("flow_lm_main_fp32.onnx")
            flow = session("flow_lm_flow_fp32.onnx")
            decoder = session("mimi_decoder_fp32.onnx")
            val specs = bundle.getJSONArray("flow_lm_state_manifest")
            require(specs.length() == 18) { "Modèle français 6 couches requis" }
            require(main.inputNames == specs.names("input_name") + setOf("sequence", "text_embeddings")) { "Interface FlowLM incompatible" }
            require(decoder.inputNames == bundle.getJSONArray("mimi_state_manifest").names("input_name") + "latent") { "Interface Mimi incompatible" }
            modelLoadMs = (System.nanoTime() - loadStart) / 1e6
        } catch (t: Throwable) { opened.asReversed().forEach { it.close() }; throw t }
    }

    private fun session(name: String): OrtSession = OrtSession.SessionOptions().use { opts ->
        loadCancel.check()
        opts.setIntraOpNumThreads(threads)
        opts.setInterOpNumThreads(1)
        opts.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
        opts.setSessionLogLevel(OrtLoggingLevel.ORT_LOGGING_LEVEL_ERROR)
        env.createSession(File(root, name).absolutePath, opts).also { opened.add(it); loadCancel.check() }
    }

    override fun generate(raw: String, cancel: CancellationToken, seed: Long, wholeSentence: Boolean, onPcm: (FloatArray) -> Unit): GenerationMetrics {
        val started = System.nanoTime()
        val prepared = frontend.prepare(raw)
        val rng = Random(seed)
        var mainNs = 0L; var flowNs = 0L; var decoderNs = 0L
        var maxOnnxNs = 0L; var lastPcmNs = 0L; var maxGapNs = 0L
        fun <T> measured(stage: Int, block: () -> T): T {
            val begin = System.nanoTime()
            try { return block() } finally {
                val elapsed = System.nanoTime() - begin
                when (stage) { 0 -> mainNs += elapsed; 1 -> flowNs += elapsed; else -> decoderNs += elapsed }
                maxOnnxNs = maxOf(maxOnnxNs, elapsed)
            }
        }
        var sinkNs = 0L
        var firstPcmNs = 0L
        var samples = 0L
        var frames = 0
        var eosFrame: Int? = null
        val pending = mutableListOf<FloatArray>()
        cancel.check()
        return cancel.withOptions { options ->
            StateBank(main, bundle.getJSONArray("flow_lm_state_manifest"), voice).use { lm ->
                StateBank(decoder, bundle.getJSONArray("mimi_state_manifest")).use { mimi ->
                    longTensor(prepared.tokens, longArrayOf(1, prepared.tokens.size.toLong())).use { ids ->
                        text.run(mapOf("token_ids" to ids), options).use { encoded ->
                            floatTensor(FloatArray(0), longArrayOf(1, 0, 32)).use { empty ->
                                measured(0) { lm.run(mapOf("sequence" to empty, "text_embeddings" to encoded.tensor("text_embeddings")), options) }
                            }
                        }
                    }
                    floatTensor(FloatArray(0), longArrayOf(1, 0, 1024)).use { emptyText ->
                        var previous = FloatArray(32) { Float.NaN }
                        fun decodePending() {
                            if (pending.isEmpty()) return
                            cancel.check()
                            val latents = FloatArray(pending.size * 32)
                            pending.forEachIndexed { i, a -> a.copyInto(latents, i * 32) }
                            floatTensor(latents, longArrayOf(1, pending.size.toLong(), 32)).use { latent ->
                                val result = measured(2) { mimi.run(mapOf("latent" to latent), options) }
                                val audio = result.tensor("audio").floatBuffer
                                val pcm = FloatArray(audio.remaining()).also { audio.get(it) }
                                require(pcm.all { it.isFinite() }) { "Sortie audio non finie" }
                                if (pcm.isNotEmpty()) {
                                    cancel.check()
                                    if (firstPcmNs == 0L) firstPcmNs = System.nanoTime() - started
                                    val sinkStart = System.nanoTime()
                                    if (lastPcmNs != 0L) maxGapNs = maxOf(maxGapNs, sinkStart - lastPcmNs)
                                    lastPcmNs = sinkStart
                                    onPcm(pcm)
                                    sinkNs += System.nanoTime() - sinkStart
                                    samples += pcm.size
                                }
                            }
                            pending.clear()
                        }
                        for (step in 0 until 250) {
                            cancel.check()
                            val seq = floatTensor(previous, longArrayOf(1, 1, 32))
                            try {
                                val result = measured(0) { lm.run(mapOf("sequence" to seq, "text_embeddings" to emptyText), options) }
                                val eos = result.tensor("eos_logit").floatBuffer.get(0)
                                if (step >= bundle.getInt("min_frames_before_eos") && eos > sampling.eosThreshold && eosFrame == null) eosFrame = step
                                if (eosFrame != null && step >= eosFrame!! + (sampling.framesAfterEos ?: prepared.framesAfterEos)) {
                                    decodePending()
                                    break
                                }
                                val noise=FloatArray(32) { (rng.nextGaussian()*sqrt(sampling.temperature)).toFloat() }
                                previous=LsdSampler.decode(noise,sampling.steps,cancel) { s,t,current ->
                                    floatTensor(current,longArrayOf(1,32)).use { x ->
                                        floatTensor(floatArrayOf(s),longArrayOf(1,1)).use { startTime ->
                                            floatTensor(floatArrayOf(t),longArrayOf(1,1)).use { endTime ->
                                                measured(1) { flow.run(mapOf("c" to result.tensor("conditioning"),"s" to startTime,"t" to endTime,"x" to x),options) }.use { f ->
                                                    val direction=f.tensor("flow_dir").floatBuffer
                                                    FloatArray(32).also { direction.get(it) }
                                                }
                                            }
                                        }
                                    }
                                }
                                pending.add(previous)
                                frames++
                                if (pending.size >= 4) decodePending() // bounded decoder batches; the UI defers playback until completion
                            } finally { seq.close() }
                        }
                        require(eosFrame != null && frames < 250) { "Limite de 20 secondes atteinte sans fin de phrase fiable" }
                    }
                }
            }
            cancel.check()
            require(samples > 0) { "Pocket TTS n'a produit aucun audio" }
            val wallNs = System.nanoTime() - started
            GenerationMetrics(firstPcmNs / 1e6, (wallNs - sinkNs) / 1e6, wallNs / 1e6,
                samples / 24000.0, frames, prepared.tokens.size, eosFrame != null,
                mainNs / 1e6, flowNs / 1e6, decoderNs / 1e6, maxOnnxNs / 1e6, maxGapNs / 1e6)
        }
    }

    private fun floatTensor(a: FloatArray, shape: LongArray) = OnnxTensor.createTensor(env, FloatBuffer.wrap(a), shape)
    private fun longTensor(a: LongArray, shape: LongArray) = OnnxTensor.createTensor(env, LongBuffer.wrap(a), shape)

    /** Keep native result tensors alive between steps; do not copy 120 MiB of caches into Java each frame. */
    private inner class StateBank(
        val session: OrtSession,
        val specs: JSONArray,
        preset: Map<String, VoiceTensor> = emptyMap(),
    ) : AutoCloseable {
        private val initial = linkedMapOf<String, OnnxTensor>()
        private var values: Map<String, OnnxTensor> = initial
        private var owner: OrtSession.Result? = null
        init {
            try {
                for (i in 0 until specs.length()) {
                    val spec = specs.getJSONObject(i)
                    val shapeJson = spec.getJSONArray("shape")
                    val shape = LongArray(shapeJson.length()) { shapeJson.getLong(it) }
                    val count = shape.fold(1L) { a, b -> a * b }.toInt()
                    val dtype = spec.getString("dtype")
                    val size = when (dtype) { "float32" -> 4; "int64" -> 8; "bool" -> 1; else -> error("Type d'état non pris en charge") }
                    val bytes = ByteBuffer.allocateDirect(count * size).order(ByteOrder.nativeOrder())
                    val key = spec.getString("module") + "/" + spec.getString("key")
                    val source = preset[key]
                    if (dtype == "float32" && source != null) {
                        require(source.dtype == 0 && source.shape.size == 5 && source.shape[2] <= shape[2])
                        val target = bytes.asFloatBuffer()
                        for (j in 0 until count) target.put(j, Float.NaN)
                        val src = ByteBuffer.wrap(source.bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
                        val block = (source.shape[2] * source.shape[3] * source.shape[4]).toInt()
                        val stride = (shape[2] * shape[3] * shape[4]).toInt()
                        for (kv in 0..1) for (j in 0 until block) target.put(kv * stride + j, src.get(kv * block + j))
                    } else if (source != null) {
                        require(dtype == "int64" && source.dtype == 1 && source.bytes.size == count * 8)
                        bytes.put(source.bytes).rewind()
                    } else if (spec.getString("fill") == "ones") {
                        for (j in 0 until count) bytes.put(j, 1.toByte())
                    }
                    val tensor = when (dtype) {
                        "float32" -> OnnxTensor.createTensor(env, bytes.asFloatBuffer(), shape)
                        "int64" -> OnnxTensor.createTensor(env, bytes.asLongBuffer(), shape)
                        else -> OnnxTensor.createTensor(env, bytes, shape, OnnxJavaType.BOOL)
                    }
                    initial[spec.getString("input_name")] = tensor
                }
            } catch (t: Throwable) { close(); throw t }
        }
        fun run(extra: Map<String, OnnxTensor>, options: OrtSession.RunOptions): OrtSession.Result {
            val result = session.run(values + extra, options)
            val next = linkedMapOf<String, OnnxTensor>()
            try {
                for (i in 0 until specs.length()) {
                    val spec = specs.getJSONObject(i)
                    next[spec.getString("input_name")] = result.tensor(spec.getString("output_name"))
                }
            } catch (t: Throwable) { result.close(); throw t }
            owner?.close()
            initial.values.forEach { it.close() }; initial.clear()
            owner = result; values = next
            return result
        }
        override fun close() { owner?.close(); owner = null; initial.values.forEach { it.close() }; initial.clear() }
    }

    override fun close() { opened.asReversed().forEach { it.close() }; opened.clear() }
}

private fun JSONArray.names(field: String): Set<String> = (0 until length()).map { getJSONObject(it).getString(field) }.toSet()
private fun OrtSession.Result.tensor(name: String): OnnxTensor = get(name).orElseThrow { IllegalStateException("Sortie absente : $name") } as OnnxTensor
