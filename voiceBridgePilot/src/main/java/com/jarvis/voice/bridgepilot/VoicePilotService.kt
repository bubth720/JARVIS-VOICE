package com.jarvis.voice.bridgepilot

import android.app.Service
import android.content.Intent
import android.os.*
import com.jarvis.voice.bridgepilot.ipc.IVoicePilotFixture

/** Authentication precedes unmarshalling; no caller can activate the service or grant itself authority. */
class VoicePilotService: Service() {
    private val auth get()=PilotRuntime.authorization
    private fun caller(): Principal = PilotIdentity(this).candidate(Binder.getCallingUid()).also { auth.authenticate(it) }
    private var liveness: IBinder?=null
    private val died=IBinder.DeathRecipient { auth.revoke() }
    private val endpoint=object: IVoicePilotFixture.Stub() {
        override fun onTransact(code: Int,data: Parcel,reply: Parcel?,flags: Int): Boolean {
            if(code in IBinder.FIRST_CALL_TRANSACTION..IBinder.LAST_CALL_TRANSACTION) {
                try { caller();need(data.dataSize()<=8192,"PARCEL_SIZE") }
                catch(_: Exception) { throw SecurityException("Voice pilot not authorized") }
            }
            return super.onTransact(code,data,reply,flags)
        }
        private fun invoke(bytes: ByteArray,names: Set<String>,action: (Principal,Map<String,String>)->Map<String,String>): ByteArray {
            val principal=caller() // Capture actual Binder UID again, before any asynchronous work.
            return try { PilotWire.encode(action(principal,PilotWire.decode(bytes,names))) }
            catch(e: Refused) { PilotWire.encode(mapOf("version" to PilotWire.VERSION,"contract_sha256" to BuildConfig.CONTRACT_SHA,"code" to e.code)) }
            catch(_: Exception) { PilotWire.encode(mapOf("version" to PilotWire.VERSION,"contract_sha256" to BuildConfig.CONTRACT_SHA,"code" to "INVALID_INPUT")) }
        }
        override fun hello(payload: ByteArray,clientLiveness: IBinder): ByteArray = invoke(payload,PilotWire.base) { p,f ->
            synchronized(this@VoicePilotService) {
                need(liveness==null || liveness===clientLiveness,"LIVENESS_CHANGED")
                need(clientLiveness.isBinderAlive,"CLIENT_DEAD")
                if(liveness==null) { clientLiveness.linkToDeath(died,0);liveness=clientLiveness }
                auth.hello(p,f)
            }
        }
        override fun request(payload: ByteArray)=invoke(payload,PilotWire.request,auth::request)
        override fun status(key: ByteArray)=invoke(key,PilotWire.key,auth::status)
        override fun cancel(key: ByteArray)=invoke(key,PilotWire.key,auth::status) // Idempotent refusal, no fake native stop.
    }
    override fun onBind(intent: Intent): IBinder=endpoint
    override fun onUnbind(intent: Intent): Boolean { auth.revoke();return false }
    override fun onDestroy() {
        auth.revoke();liveness?.runCatching { unlinkToDeath(died,0) };liveness=null;super.onDestroy()
    }
}
