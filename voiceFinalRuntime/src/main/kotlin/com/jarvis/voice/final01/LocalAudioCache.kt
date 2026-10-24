package com.jarvis.voice.final01

import com.jarvis.voice.pocket.CancellationToken
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import org.json.JSONObject

/** Two bounded private float WAVs. Exact raw PCM retained; no normalizer or filter. */
class LocalAudioCache(private val root: File) {
    init { root.mkdirs();listOf("A","B").forEach { File(root,"$it.candidate").delete() } }
    private fun slot(slot: String): File { require(slot in setOf("A","B"));return File(root,"$slot.wav") }
    private fun digest(bytes: ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    @Synchronized fun save(side: String,binding: String,pcm: FloatArray,cancel: CancellationToken) {
        require(binding.length in 1..1000 && pcm.size in 1..480000 && pcm.all { it.isFinite() })
        val b=ByteBuffer.allocate(44+pcm.size*4).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray());b.putInt(36+pcm.size*4);b.put("WAVEfmt ".toByteArray());b.putInt(16);b.putShort(3);b.putShort(1)
        b.putInt(24000);b.putInt(96000);b.putShort(4);b.putShort(32);b.put("data".toByteArray());b.putInt(pcm.size*4)
        pcm.forEachIndexed { i,v -> if(i%4096==0) cancel.check();b.putFloat(v) }
        val bytes=b.array();cancel.check();val target=slot(side);val tmp=File(root,"$side.candidate")
        try {
            tmp.writeBytes(bytes);cancel.check();check(tmp.renameTo(target))
            File(root,"$side.json").writeText(JSONObject().put("schema",1).put("binding",binding).put("samples",pcm.size).put("sha256",digest(bytes)).toString())
        } finally { tmp.delete() }
    }
    @Synchronized fun load(side: String,binding: String,cancel: CancellationToken): FloatArray {
        val f=slot(side);require(f.length() in 48..1920044)
        val meta=File(root,"$side.json");require(meta.length() in 1..4096)
        val m=JSONObject(meta.readText());require(m.getInt("schema")==1 && m.getString("binding")==binding) { "PROFILE_OR_AB_CONTEXT_CHANGED" }
        val bytes=f.readBytes();require(digest(bytes)==m.getString("sha256")) { "AUDIO_INTEGRITY" }
        val n=m.getInt("samples");require(n in 1..480000 && bytes.size==44+n*4)
        val b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);b.position(44)
        return FloatArray(n) { i->if(i%4096==0) cancel.check();b.float.also { require(it.isFinite()) } }
    }
    /** Export only the already verified WAV, never metadata or another identity's audio. */
    @Synchronized fun export(side: String,binding: String,cancel: CancellationToken,write: (ByteArray)->Unit) {
        load(side,binding,cancel).fill(0f);cancel.check();write(slot(side).readBytes())
    }
    @Synchronized fun clear() { listOf("A","B").forEach { slot(it).delete();File(root,"$it.json").delete();File(root,"$it.candidate").delete() } }
}
