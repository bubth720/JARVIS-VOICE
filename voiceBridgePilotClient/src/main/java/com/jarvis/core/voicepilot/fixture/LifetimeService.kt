package com.jarvis.core.voicepilot.fixture

import android.app.Service
import android.content.Intent
import android.os.*

/** Fixture-only second process. No bridge activation, profile or resource authority. */
class LifetimeService: Service() {
    private val token=object: Binder() {
        override fun onTransact(code: Int,data: Parcel,reply: Parcel?,flags: Int): Boolean {
            if(Binder.getCallingUid()!=applicationInfo.uid) throw SecurityException("Fixture UID required")
            if(code!=IBinder.FIRST_CALL_TRANSACTION || data.dataSize()!=0 || reply==null) return false
            reply.writeInt(Process.myPid());return true
        }
    }
    override fun onBind(intent: Intent): IBinder=token
}
