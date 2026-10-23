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

/** RunOptions termination interrupts a running ONNX operation, not just playback. */
class CancellationToken {
    private val cancelled = AtomicBoolean(false)
    private var active: OrtSession.RunOptions? = null
    fun isCancelled() = cancelled.get()
    fun check() { if (isCancelled()) throw CancellationException("Annulé") }
    @Synchronized fun cancel() { cancelled.set(true); active?.setTerminate(true) }
    /** Shared termination scope for the isolated VOICE-04 observer; unchanged cancellation semantics. */
    fun <T> withOptions(block: (OrtSession.RunOptions) -> T): T {
        check()
        val options = OrtSession.RunOptions()
        synchronized(this) { active = options; if (isCancelled()) options.setTerminate(true) }
        try { check(); return block(options) }
        finally { synchronized(this) { active = null; options.close() } }
    }
}

data class GenerationMetrics(
    val firstPcmMs: Double,
    val computeMs: Double,
    val wallMs: Double,
    val audioSeconds: Double,
    val frames: Int,
    val tokens: Int,
    val eosReached: Boolean,
    val onnxMainMs: Double = 0.0,
    val onnxFlowMs: Double = 0.0,
    val onnxDecoderMs: Double = 0.0,
    val maxOnnxMs: Double = 0.0,
    val maxPcmGapMs: Double = 0.0,
) {
    val rtf: Double get() = computeMs / (audioSeconds * 1000.0)
    val speed: Double get() = 1.0 / rtf
}

/** French 6-layer preset-only schema-3 runtime. CPU only; one request per engine. */
class PocketEngine(private val root: File, val threads: Int = 1) : AutoCloseable {
    private val loadStart = System.nanoTime()
    private val bundle = ModelPack.verify(root)
    private val env = OrtEnvironment.getEnvironment(OrtLoggingLevel.ORT_LOGGING_LEVEL_ERROR)
    private val opened = mutableListOf<OrtSession>()
    private val frontend = FrenchFrontend(JSONObject(File(root, "tokenizer.json").readText()))
    private val voice = VoiceState.read(File(root, "voices.bin"), "alba")
    private val text: OrtSession
    private val main: OrtSession
    private val flow: OrtSession
    private val decoder: OrtSession
    val modelLoadMs: Double

    init {
        require(threads in 1..2)
        try {
            text = session("text_conditioner_int8.onnx")
            main = session("flow_lm_main_int8.onnx")
            flow = session("flow_lm_flow_int8.onnx")
            decoder = session("mimi_decoder_int8.onnx")
            val specs = bundle.getJSONArray("flow_lm_state_manifest")
            require(specs.length() == 18) { "Modèle français 6 couches requis" }
            require(main.inputNames == specs.names("input_name") + setOf("sequence", "text_embeddings")) { "Interface FlowLM incompatible" }
            require(decoder.inputNames == bundle.getJSONArray("mimi_state_manifest").names("input_name") + "latent") { "Interface Mimi incompatible" }
            modelLoadMs = (System.nanoTime() - loadStart) / 1e6
        } catch (t: Throwable) { opened.asReversed().forEach { it.close() }; throw t }
    }

    private fun session(name: String): OrtSession = OrtSession.SessionOptions().use { opts ->
        opts.setIntraOpNumThreads(threads)
        opts.setInterOpNumThreads(1)
        opts.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
        opts.setSessionLogLevel(OrtLoggingLevel.ORT_LOGGING_LEVEL_ERROR)
        env.createSession(File(root, name).absolutePath, opts).also { opened.add(it) }
    }

    fun generate(raw: String, cancel: CancellationToken, seed: Long = 42, wholeSentence: Boolean = false, onPcm: (FloatArray) -> Unit): GenerationMetrics {
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
                                if (step >= bundle.getInt("min_frames_before_eos") && eos > bundle.getDouble("eos_threshold") && eosFrame == null) eosFrame = step
                                if (eosFrame != null && step >= eosFrame!! + prepared.framesAfterEos) {
                                    decodePending()
                                    break
                                }
                                val noise = FloatArray(32) { (rng.nextGaussian() * sqrt(bundle.getDouble("temperature"))).toFloat() }
                                floatTensor(noise, longArrayOf(1, 32)).use { x ->
                                    floatTensor(floatArrayOf(0f), longArrayOf(1, 1)).use { s ->
                                        floatTensor(floatArrayOf(1f), longArrayOf(1, 1)).use { t ->
                                            measured(1) { flow.run(mapOf("c" to result.tensor("conditioning"), "s" to s, "t" to t, "x" to x), options) }.use { f ->
                                                val direction = f.tensor("flow_dir").floatBuffer
                                                previous = FloatArray(32) { noise[it] + direction.get(it) }
                                            }
                                        }
                                    }
                                }
                                pending.add(previous)
                                frames++
                                if (!wholeSentence && pending.size >= if (firstPcmNs == 0L) 2 else 4) decodePending()
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
