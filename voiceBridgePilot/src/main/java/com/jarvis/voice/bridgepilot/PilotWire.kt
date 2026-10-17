package com.jarvis.voice.bridgepilot

import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import java.io.StringReader
import java.io.StringWriter
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.UUID

internal class Refused(val code: String): IllegalArgumentException(code)
internal fun need(ok: Boolean,code: String) { if(!ok) throw Refused(code) }
internal fun uuid(s: String)=runCatching { UUID.fromString(s).toString() == s }.getOrDefault(false)
internal object PilotWire {
    const val VERSION="voice.bridge.fixture/1"
    const val CLIENT="com.jarvis.core.voicepilot.fixture"
    const val PERMISSION="com.jarvis.voice.bridgepilot.permission.BIND"
    val base=setOf("version","contract_sha256","session_id")
    val key=base+setOf("correlation_id","cancellation_id","grant_ref","core_epoch","lease_id","generation")
    val request=key+setOf("text","remaining_ms")
    fun decode(bytes: ByteArray,names: Set<String>): Map<String,String> {
        need(bytes.size in 1..4096,"PAYLOAD_SIZE")
        val text=try { Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString() } catch(_: Exception) { throw Refused("INVALID_UTF8") }
        val fields=linkedMapOf<String,String>()
        try { JsonReader(StringReader(text)).use { r ->
            r.isLenient=false;r.beginObject()
            while(r.hasNext()) {
                val name=r.nextName();need(name in names && name !in fields,"FIELDS")
                need(r.peek() == JsonToken.STRING,"TYPE");val value=r.nextString()
                need(Charsets.UTF_8.newEncoder().canEncode(value),"UNICODE");fields[name]=value
            }
            r.endObject();need(r.peek() == JsonToken.END_DOCUMENT && fields.keys == names,"FIELDS")
        } } catch(e: Refused) { throw e } catch(_: Exception) { throw Refused("MALFORMED") }
        need(fields["version"] == VERSION && fields["contract_sha256"] == BuildConfig.CONTRACT_SHA,"CONTRACT")
        need(uuid(fields.getValue("session_id")),"SESSION")
        return fields
    }
    fun encode(fields: Map<String,String>): ByteArray {
        val s=StringWriter();JsonWriter(s).use { w ->w.beginObject();fields.toSortedMap().forEach { (k,v)->w.name(k).value(v) };w.endObject() }
        return s.toString().toByteArray(Charsets.UTF_8).also { need(it.size<=4096,"PAYLOAD_SIZE") }
    }
    fun number(s: String,max: Long): Long {
        val n=s.toLongOrNull();need(s.matches(Regex("[1-9][0-9]{0,15}")) && n!=null && n in 1..max,"NUMBER")
        return n ?: throw Refused("NUMBER")
    }
    fun validateKey(f: Map<String,String>) {
        for(k in listOf("correlation_id","cancellation_id","grant_ref","core_epoch","lease_id")) need(uuid(f.getValue(k)),"IDENTIFIER")
        number(f.getValue("generation"),9007199254740991L)
    }
    fun validText(text: String): Boolean {
        if(text.length !in 1..300 || text.isBlank() || text.toByteArray(Charsets.UTF_8).size>1200 || !Charsets.UTF_8.newEncoder().canEncode(text)) return false
        return text.none { it.isISOControl() && it!='\n' } && !Regex("<(?:unk|s|/s|pad)>").containsMatchIn(text)
    }
}
