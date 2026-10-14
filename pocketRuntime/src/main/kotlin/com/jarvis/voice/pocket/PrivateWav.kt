package com.jarvis.voice.pocket

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Bounded RIFF reader for local PCM16/float32 mono 24 kHz; never exports the input. */
object PrivateWav {
    fun read(file: File, startSamples: Int = 0, lengthSamples: Int? = null, cancelled: () -> Boolean = { false }): FloatArray = RandomAccessFile(file, "r").use { input ->
        require(input.length() in 44..33554432)
        fun tag() = ByteArray(4).also { input.readFully(it) }.toString(Charsets.US_ASCII)
        fun u32() = Integer.toUnsignedLong(Integer.reverseBytes(input.readInt()))
        fun u16() = java.lang.Short.toUnsignedInt(java.lang.Short.reverseBytes(input.readShort()))
        require(tag() == "RIFF"); val riff = u32(); require(riff + 8 <= input.length()); require(tag() == "WAVE")
        var format = 0; var bits = 0; var dataPosition = -1L; var dataSize = 0L
        while (input.filePointer + 8 <= riff + 8) {
            val name = tag(); val size = u32(); val next = input.filePointer + size + size % 2
            require(next <= input.length() && next <= riff + 8)
            if (name == "fmt ") {
                require(size in 16..65536); format = u16(); require(u16() == 1) { "WAV mono requis" }
                require(u32() == 24000L) { "WAV à 24 kHz requis" }
                u32(); val align = u16(); bits = u16()
                require((format == 1 && bits == 16 || format == 3 && bits == 32) && align == bits / 8)
            } else if (name == "data") { require(dataPosition == -1L); dataPosition = input.filePointer; dataSize = size }
            input.seek(next)
        }
        require(format in listOf(1, 3) && dataPosition >= 0 && dataSize % (bits / 8) == 0L)
        val available = (dataSize / (bits / 8)).toInt()
        require(available in 120000..4320000 && startSamples >= 0)
        val length = lengthSamples ?: (available - startSamples)
        require(length in 120000..360000 && startSamples.toLong() + length <= available)
        input.seek(dataPosition + startSamples.toLong() * (bits / 8))
        val pcm = FloatArray(length)
        for (i in pcm.indices) {
            if (i % 2048 == 0 && cancelled()) throw java.util.concurrent.CancellationException()
            pcm[i] = if (bits == 16) java.lang.Short.reverseBytes(input.readShort()) / 32768f
            else Float.fromBits(Integer.reverseBytes(input.readInt()))
            require(pcm[i].isFinite()) { "WAV contenant des valeurs non finies" }
        }
        pcm
    }
}
