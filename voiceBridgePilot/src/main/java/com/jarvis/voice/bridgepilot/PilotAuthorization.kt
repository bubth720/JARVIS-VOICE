package com.jarvis.voice.bridgepilot

import android.os.SystemClock
import java.util.UUID

/** Volatile local consent: restart/pause/revoke removes all authority. No private content stored. */
internal class PilotAuthorization(private val clock: ()->Long=SystemClock::elapsedRealtime) {
    val epoch=UUID.randomUUID().toString()
    private var visible=false
    private var peer: Principal?=null
    private var deadline=0L
    private var session: String?=null
    private var grant: String?=null
    private var consumed=false
    private var ticket: Map<String,String>?=null
    @Synchronized fun visibility(resumed: Boolean) { visible=resumed;if(!resumed) revoke() }
    @Synchronized fun arm(principal: Principal) {
        need(visible,"VISIBLE_OPT_IN_REQUIRED");revoke();peer=principal;deadline=clock()+60000
    }
    @Synchronized fun revoke() { peer=null;deadline=0;session=null;grant=null;ticket=null;consumed=false }
    @Synchronized fun authenticate(principal: Principal) {
        need(visible && peer==principal && clock()<deadline,"LOCAL_AUTHORIZATION_REQUIRED")
    }
    @Synchronized fun hello(principal: Principal,fields: Map<String,String>): Map<String,String> {
        authenticate(principal)
        val sid=fields.getValue("session_id")
        need(!consumed && (session==null || session==sid),"SESSION_OR_REPLAY")
        session=sid;if(grant==null) grant=UUID.randomUUID().toString()
        return fields+mapOf("voice_epoch" to epoch,"grant_ref" to grant!!,"expires_elapsed_ms" to deadline.toString(),
            "scope" to "AUTHENTICATED_FIXTURE_ONLY","native_generation_available" to "false","global_admission_available" to "false")
    }
    @Synchronized fun request(principal: Principal,fields: Map<String,String>): Map<String,String> {
        authenticate(principal);PilotWire.validateKey(fields)
        need(!consumed && fields["session_id"]==session && fields["grant_ref"]==grant,"GRANT_OR_REPLAY")
        need(PilotWire.validText(fields.getValue("text")),"TEXT")
        val budget=PilotWire.number(fields.getValue("remaining_ms"),90000)
        need(budget<=deadline-clock(),"EXPIRED")
        // References in this payload do not establish CORE authority. No engine exists here.
        ticket=fields.filterKeys { it in PilotWire.key };consumed=true
        return receipt(ticket!!)
    }
    @Synchronized fun status(principal: Principal,fields: Map<String,String>): Map<String,String> {
        authenticate(principal);PilotWire.validateKey(fields)
        need(consumed && fields == ticket,"STALE_RESULT");return receipt(fields)
    }
    private fun receipt(fields: Map<String,String>)=fields+mapOf("voice_epoch" to epoch,"phase" to "REFUSED","code" to "NATIVE_AUTHORITY_UNAVAILABLE")
}
internal object PilotRuntime { val authorization=PilotAuthorization() }
