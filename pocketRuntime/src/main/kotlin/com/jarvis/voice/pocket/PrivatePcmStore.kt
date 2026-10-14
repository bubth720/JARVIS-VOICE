package com.jarvis.voice.pocket

import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.io.FileOutputStream
import java.io.DataOutputStream
import java.io.DataInputStream
import java.util.UUID

/** One last complete PCM32 utterance, <=20 s / 1.92 MB; atomic replacement; no text or recording export. */
class PrivatePcmStore(private val root: File) {
    private val active get()=File(root,"active.json")
    fun exists()=active.isFile
    private fun current(): File {
        require(active.length() in 1..4096)
        val id=JSONObject(active.readText()).getString("id")
        require(id.matches(Regex("[a-f0-9-]{36}")))
        return File(root,id)
    }
    @Synchronized fun save(pcm: FloatArray, cancel: CancellationToken) {
        require(pcm.size in 1..480000 && pcm.all { it.isFinite() }) { "PCM incomplet, non fini ou supérieur à 20 s" }
        root.mkdirs()
        val stage=File(root,UUID.randomUUID().toString()).apply { mkdirs() }
        val pending=File(root,"active.pending"); var committed=false
        try {
            val data=File(stage,"audio.pcm")
            FileOutputStream(data).use { file ->
                val out=DataOutputStream(file.buffered(65536))
                out.writeBytes("JVPCM1"); out.writeInt(pcm.size)
                for(i in pcm.indices) { if(i%4096==0) cancel.check(); out.writeInt(pcm[i].toRawBits()) }
                out.flush(); file.fd.sync()
            }
            File(stage,"sha256.txt").writeText(ExperimentalModelPack.sha256(data,cancel::isCancelled))
            RandomAccessFile(pending,"rw").use { out -> out.setLength(0); out.write(JSONObject().put("id",stage.name).toString().toByteArray()); out.fd.sync() }
            cancel.check(); check(pending.renameTo(active)) { "PCM privé non activé" }; committed=true
            root.listFiles()?.filter { it.isDirectory && it!=stage }?.forEach { it.deleteRecursively() }
        } finally { pending.delete(); if(!committed) stage.deleteRecursively() }
    }
    @Synchronized fun load(cancel: CancellationToken): FloatArray {
        val dir=current(); val data=File(dir,"audio.pcm")
        require(data.length() in 14..1920010 && File(dir,"sha256.txt").length()==64L) { "Cache PCM invalide" }
        require(ExperimentalModelPack.sha256(data,cancel::isCancelled)==File(dir,"sha256.txt").readText()) { "Cache PCM corrompu" }
        return DataInputStream(data.inputStream().buffered(65536)).use { input ->
            require(ByteArray(6).also { input.readFully(it) }.toString(Charsets.US_ASCII)=="JVPCM1")
            val size=input.readInt(); require(size in 1..480000 && data.length()==10L+size*4)
            FloatArray(size) { i -> if(i%4096==0) cancel.check(); Float.fromBits(input.readInt()).also { require(it.isFinite()) } }
        }
    }
    @Synchronized fun delete() { root.listFiles()?.forEach { check(it.deleteRecursively()) } }
}
