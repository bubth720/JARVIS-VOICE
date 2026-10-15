package com.jarvis.voice.pocket

import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

data class PersonalVoiceProfile(val packId: String, val tensors: Map<String, VoiceTensor>)

/** JVFP1 is distinct from the preset-only INT8 PTVB1 format. Exact FP32 pack binding. */
object PersonalVoiceCodec {
    fun validate(profile: PersonalVoiceProfile) {
        require(profile.packId == ExperimentalModelPack.id) { "Profil d'un autre pack : recréer le profil" }
        val expected = (0 until 6).flatMap { layer -> listOf("offset", "pad", "cache").map { "transformer.layers.$layer.self_attn/$it" } }.toSet()
        require(profile.tensors.keys == expected) { "18 états FP32 exacts requis" }
        var frames: Long? = null
        for (layer in 0 until 6) {
            val prefix = "transformer.layers.$layer.self_attn/"
            fun integer(key: String): Long {
                val tensor = requireNotNull(profile.tensors[prefix + key])
                require(tensor.dtype == 1 && tensor.shape.contentEquals(longArrayOf(1)) && tensor.bytes.size == 8)
                return ByteBuffer.wrap(tensor.bytes).order(ByteOrder.LITTLE_ENDIAN).long
            }
            val offset = integer("offset"); require(offset in 64..189 && integer("pad") == 0L)
            if (frames == null) frames = offset else require(frames == offset)
            val cache = requireNotNull(profile.tensors[prefix + "cache"])
            require(cache.dtype == 0 && cache.shape.contentEquals(longArrayOf(2, 1, offset, 16, 64)))
            require(cache.bytes.size == 2 * offset.toInt() * 16 * 64 * 4)
            val values = ByteBuffer.wrap(cache.bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
            while (values.hasRemaining()) require(values.get().isFinite()) { "Cache FP32 non fini" }
        }
    }
    fun read(file: File): PersonalVoiceProfile = RandomAccessFile(file, "r").use { input ->
        require(input.length() in 100..10000000) { "Profil FP32 invalide" }
        fun u16() = input.readUnsignedByte() or (input.readUnsignedByte() shl 8)
        fun u32() = Integer.toUnsignedLong(Integer.reverseBytes(input.readInt()))
        fun text(): String { val size = u16(); require(size in 1..256); return ByteArray(size).also { input.readFully(it) }.toString(Charsets.UTF_8) }
        require(ByteArray(5).also { input.readFully(it) }.toString(Charsets.US_ASCII) == "JVFP1" && u32() == 1L) { "Format de profil FP32 requis" }
        val packId = text(); require(packId == ExperimentalModelPack.id)
        require(u16() == 18)
        val tensors = linkedMapOf<String, VoiceTensor>()
        repeat(18) {
            val name = text(); val dtype = input.readUnsignedByte(); val rank = input.readUnsignedByte()
            require(dtype in 0..1 && rank in 1..5)
            val shape = LongArray(rank) { u32().also { require(it in 1..189) } }
            val bytes = u32(); require(bytes <= 2L * 189 * 16 * 64 * 4)
            val expected = shape.fold(1L) { a, b -> a * b } * if (dtype == 0) 4 else 8
            require(bytes == expected && input.filePointer + bytes <= input.length())
            require(tensors.put(name, VoiceTensor(dtype, shape, ByteArray(bytes.toInt()).also { input.readFully(it) })) == null)
        }
        require(input.filePointer == input.length()) { "Données supplémentaires interdites" }
        PersonalVoiceProfile(packId, tensors).also(::validate)
    }
    fun write(file: File, profile: PersonalVoiceProfile) {
        validate(profile)
        RandomAccessFile(file, "rw").use { out ->
            out.setLength(0)
            fun u16(value: Int) { out.writeByte(value); out.writeByte(value ushr 8) }
            fun u32(value: Int) { out.writeInt(Integer.reverseBytes(value)) }
            fun text(value: String) { val bytes = value.toByteArray(); u16(bytes.size); out.write(bytes) }
            out.writeBytes("JVFP1"); u32(1); text(profile.packId); u16(18)
            for ((name, tensor) in profile.tensors) {
                text(name); out.writeByte(tensor.dtype); out.writeByte(tensor.shape.size)
                tensor.shape.forEach { u32(it.toInt()) }; u32(tensor.bytes.size); out.write(tensor.bytes)
            }
            out.fd.sync()
        }
    }
}

/** A single private personal profile; versioned folder activation preserves old state on error. */
class PersonalVoiceRepository(private val root: File) {
    init { root.mkdirs() }
    private val active get() = File(root, "active.json")
    private fun current(): File {
        val id = JSONObject(active.readText()).getString("id")
        require(id.matches(Regex("[a-f0-9-]{36}")))
        return File(root, id)
    }
    fun exists() = active.isFile
    @Synchronized fun load(): PersonalVoiceProfile {
        val directory = current()
        val file = File(directory, "state.bin"); val info = JSONObject(File(directory, "metadata.json").readText())
        require(info.getInt("schema") == 1 && info.getString("packId") == ExperimentalModelPack.id && info.getBoolean("consentConfirmed"))
        require(ExperimentalModelPack.sha256(file) == info.getString("sha256")) { "Profil privé corrompu" }
        return PersonalVoiceCodec.read(file)
    }
    @Synchronized fun save(profile: PersonalVoiceProfile, consentConfirmed: Boolean, cancel: CancellationToken) {
        require(consentConfirmed) { "Consentement explicite requis" }
        val id = UUID.randomUUID().toString()
        val stage = File(root, id).apply { mkdirs() }
        val pending = File(root, "active.pending")
        var committed = false
        try {
            cancel.check(); val data = File(stage, "state.bin"); PersonalVoiceCodec.write(data, profile)
            PersonalVoiceCodec.read(data)
            File(stage, "metadata.json").writeText(JSONObject().put("schema", 1).put("packId", profile.packId).put("consentConfirmed", true)
                .put("sha256", ExperimentalModelPack.sha256(data, cancel::isCancelled)).toString())
            RandomAccessFile(pending, "rw").use { pointer ->
                pointer.setLength(0); pointer.write(JSONObject().put("schema", 1).put("id", id).toString().toByteArray()); pointer.fd.sync()
            }
            cancel.check()
            check(pending.renameTo(active)) { "Profil non activé" }
            committed = true
            root.listFiles()?.filter { it.isDirectory && it != stage }?.forEach { it.deleteRecursively() }
        } finally { pending.delete(); if (!committed) stage.deleteRecursively() }
    }
    @Synchronized fun delete() { root.listFiles()?.forEach { check(it.deleteRecursively()) { "Suppression du profil impossible" } } }
}

class FP32VoiceEncoder(root: File, cancel: CancellationToken) : AutoCloseable {
    private val encoder: CandidateVoiceEncoder
    init { ExperimentalModelPack.verify(root, cancel::isCancelled); encoder = CandidateVoiceEncoder(root, cancel) }
    fun create(pcm: FloatArray, cancel: CancellationToken): PersonalVoiceProfile = PersonalVoiceProfile(ExperimentalModelPack.id, encoder.encode(pcm, cancel)).also(PersonalVoiceCodec::validate)
    override fun close() = encoder.close()
}
