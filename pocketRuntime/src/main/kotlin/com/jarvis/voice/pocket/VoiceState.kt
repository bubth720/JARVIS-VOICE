package com.jarvis.voice.pocket

import java.io.File
import java.io.RandomAccessFile

data class VoiceTensor(val dtype: Int, val shape: LongArray, val bytes: ByteArray)

/** PTVB1 is a bundle-specific preset format, not an imported or cloned recording. */
object VoiceState {
    fun read(file: File, selected: String): Map<String, VoiceTensor> = RandomAccessFile(file, "r").use { f ->
        fun u16() = f.readUnsignedByte() or (f.readUnsignedByte() shl 8)
        fun u32(): Long = Integer.toUnsignedLong(Integer.reverseBytes(f.readInt()))
        fun string() = ByteArray(u16()).also { f.readFully(it) }.toString(Charsets.UTF_8)
        val magic = ByteArray(5).also { f.readFully(it) }.toString(Charsets.US_ASCII)
        require(magic == "PTVB1") { "Profil prédéfini incompatible" }
        val count = u32()
        require(count in 1..8)
        repeat(count.toInt()) {
            val name = string()
            val entries = u16()
            require(entries == 18)
            val state = linkedMapOf<String, VoiceTensor>()
            repeat(entries) {
                val key = string()
                val dtype = f.readUnsignedByte()
                val rank = f.readUnsignedByte()
                require(dtype in 0..1 && rank in 1..5)
                val shape = LongArray(rank) { u32().also { require(it in 1..1024) } }
                val size = u32()
                require(size == shape.fold(1L) { a, b -> a * b } * if (dtype == 0) 4 else 8)
                require(size <= 8 * 1024 * 1024)
                if (name == selected) {
                    val bytes = ByteArray(size.toInt()).also { f.readFully(it) }
                    require(state.put(key, VoiceTensor(dtype, shape, bytes)) == null)
                } else f.seek(f.filePointer + size)
            }
            if (name == selected) return@use state
        }
        error("Voix prédéfinie absente : $selected")
    }
}
