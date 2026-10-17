package com.jarvis.voice.bridge

import org.json.*
import java.security.MessageDigest
import java.time.Instant

enum class Envelope(val file: String) { CAPABILITY("capability-manifest"),REQUEST("request-envelope"),RESPONSE("response-envelope"),LEASE("resource-lease"),HEALTH("health-status") }
/** Validator for exactly the pinned five UNIFIED schemas, not a general JSON Schema engine. */
object UnifiedSchemas {
    private val keywordSet=setOf("\$schema","\$id","title","type","properties","required","additionalProperties","oneOf","enum","const","minimum","maximum","minLength","maxLength","pattern","format","maxItems","items")
    private fun resource(name: String)=requireNotNull(javaClass.getResourceAsStream("/unified-v1/$name")).use { it.readBytes() }
    private val schemas=run {
        val lock=JSONObject(resource("source-lock.json").toString(Charsets.UTF_8)).getJSONObject("sha256")
        Envelope.entries.associateWith { e ->
            val name=e.file+".schema.json";val bytes=resource(name)
            val hash=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            checkBridge(hash==lock.getString(name),BridgeError.INCOMPATIBLE)
            JSONObject(bytes.toString(Charsets.UTF_8)).also(::auditSchema)
        }
    }
    private fun auditSchema(s: JSONObject) {
        checkBridge(s.keys().asSequence().all { it in keywordSet },BridgeError.INCOMPATIBLE)
        s.optJSONObject("properties")?.let { p->p.keys().forEach { auditSchema(p.getJSONObject(it)) } }
        s.optJSONArray("oneOf")?.let { a->for(i in 0 until a.length()) auditSchema(a.getJSONObject(i)) }
        s.optJSONObject("items")?.let(::auditSchema)
    }
    fun parse(kind: Envelope,raw: String): JSONObject = try {
        StrictBridgeJson(raw).validate();val tok=JSONTokener(raw);val obj=JSONObject(tok)
        checkBridge(tok.nextClean()=='\u0000');validate(obj,schemas.getValue(kind));semantics(kind,obj);obj
    } catch(e: BridgeFailure) { throw e } catch(_: Exception) { throw BridgeFailure(BridgeError.INVALID_INPUT) }
    private fun validate(v: Any,s: JSONObject) {
        if(s.has("oneOf")) {
            val options=s.getJSONArray("oneOf");var count=0
            for(i in 0 until options.length()) if(runCatching { validate(v,options.getJSONObject(i)) }.isSuccess) count++
            checkBridge(count==1)
        }
        if(s.has("const")) checkBridge(v==s.get("const"))
        if(s.has("enum")) { val a=s.getJSONArray("enum");checkBridge((0 until a.length()).any { v==a.get(it) }) }
        when(s.optString("type")) {
            "object"->checkBridge(v is JSONObject)
            "array"->checkBridge(v is JSONArray)
            "string"->checkBridge(v is String)
            "integer"->checkBridge(v is Int || v is Long)
            "boolean"->checkBridge(v is Boolean)
            "null"->checkBridge(v===JSONObject.NULL)
            ""->{}
            else->throw BridgeFailure(BridgeError.INCOMPATIBLE)
        }
        if(v is JSONObject) {
            val p=s.optJSONObject("properties") ?: JSONObject();val keys=v.keys().asSequence().toSet()
            if(s.opt("additionalProperties")==false) checkBridge(keys.all { p.has(it) })
            s.optJSONArray("required")?.let { a->for(i in 0 until a.length()) checkBridge(v.has(a.getString(i))) }
            for(k in keys) if(p.has(k)) validate(v.get(k),p.getJSONObject(k))
        }
        if(v is JSONArray) {
            if(s.has("maxItems")) checkBridge(v.length()<=s.getInt("maxItems"))
            if(s.has("items")) for(i in 0 until v.length()) validate(v.get(i),s.getJSONObject("items"))
        }
        if(v is String) {
            unicode(v);val length=v.codePointCount(0,v.length)
            if(s.has("minLength")) checkBridge(length>=s.getInt("minLength"))
            if(s.has("maxLength")) checkBridge(length<=s.getInt("maxLength"))
            if(s.has("pattern")) checkBridge(Regex(s.getString("pattern")).matches(v))
            when(s.optString("format")) {
                "uuid"->uuid(v)
                "date-time"->{ checkBridge(v.endsWith("Z"));checkBridge(runCatching { Instant.parse(v) }.isSuccess) }
                ""->{}
                else->throw BridgeFailure(BridgeError.INCOMPATIBLE)
            }
        }
        if(v is Number) {
            if(s.has("minimum")) checkBridge(v.toLong()>=s.getLong("minimum"))
            if(s.has("maximum")) checkBridge(v.toLong()<=s.getLong("maximum"))
        }
    }
    private fun semantics(kind: Envelope,j: JSONObject) {
        when(kind) {
            Envelope.REQUEST->{
                val d=j.getJSONObject("deadline");val l=j.getJSONObject("limits");val ttl=d.getLong("expires_at_ms")-d.getLong("sent_at_ms")
                checkBridge(ttl>0 && ttl<=l.getLong("timeout_ms"))
                val p=j.getJSONObject("payload")
                if(j.getString("capability")=="speech.synthesize") checkBridge(p.has("voice_profile_ref") && p.has("pack_sha256"))
                if(j.getString("capability")=="text.generate") checkBridge(p.has("model_sha256"))
            }
            Envelope.LEASE->{
                if(j.getString("state")=="released") checkBridge(j.getBoolean("effective_end") && j.getBoolean("release_confirmed") && !j.getBoolean("model_resident"))
                if(j.getBoolean("release_confirmed")) checkBridge(j.getString("state")=="released" && j.getBoolean("effective_end") && !j.getBoolean("model_resident"))
                if(j.getString("state")=="native_stopped") checkBridge(j.getBoolean("effective_end"))
                if(j.getString("decision")=="refused") checkBridge(!j.getBoolean("model_resident") && j.getString("state") !in setOf("running","loading"))
            }
            Envelope.HEALTH->{
                checkBridge((j.getString("state")=="available")== (j.getString("reason_code")=="OK"))
                if(j.getString("state")=="unknown") checkBridge(j.getString("reason_code")=="UNKNOWN")
            }
            Envelope.CAPABILITY->{
                val a=j.getJSONArray("capabilities");val ids=mutableSetOf<String>()
                for(i in 0 until a.length()) { val c=a.getJSONObject(i);checkBridge(ids.add(c.getString("id")));if(c.getBoolean("available")) checkBridge(j.getString("state")=="available" && c.getString("qualification") !in setOf("unknown","not_checked")) }
            }
            Envelope.RESPONSE->{
                val status=j.getString("status")
                if(status!="success") {
                    val permitted=when(status) {
                        "cancelled"->setOf("CANCELLED");"expired"->setOf("EXPIRED");"unavailable"->setOf("MODULE_ABSENT")
                        "incompatible"->setOf("INCOMPATIBLE");"unauthorized"->setOf("UNAUTHORIZED");"unknown"->setOf("UNKNOWN")
                        "interrupted"->setOf("INTERRUPTED","CLEANUP_FAILED");"refused"->setOf("BUSY","INSUFFICIENT_RESOURCES","INVALID_INPUT")
                        else->setOf("UNKNOWN","INVALID_INPUT","CLEANUP_FAILED","INTERRUPTED")
                    }
                    checkBridge(j.getJSONObject("error").getString("code") in permitted)
                }
            }
        }
    }
}
