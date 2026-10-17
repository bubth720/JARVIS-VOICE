package com.jarvis.voice.bridgepilot

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

internal data class Principal(val uid: Int,val packageName: String,val certificate: String,val installed: Long,val updated: Long)
/** No package supplied by a payload participates in caller authentication. */
internal class PilotIdentity(private val context: Context) {
    private fun certificate(name: String): String {
        val pm=context.packageManager
        val bytes=if(Build.VERSION.SDK_INT>=28) {
            val signing=pm.getPackageInfo(name,PackageManager.GET_SIGNING_CERTIFICATES).signingInfo
            need(signing!=null && !signing.hasMultipleSigners() && (signing.signingCertificateHistory?.size ?: 0)<=1,"SIGNING_ROTATION")
            val current=signing!!.apkContentsSigners;need(current.size==1,"CERTIFICATES");current.single().toByteArray()
        } else {
            @Suppress("DEPRECATION") val current=pm.getPackageInfo(name,PackageManager.GET_SIGNATURES).signatures
            need(current!=null && current.size==1,"CERTIFICATES");current!!.single().toByteArray()
        }
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it.toInt() and 255)}
    }
    fun candidate(uid: Int): Principal {
        val pm=context.packageManager
        need(pm.getPackagesForUid(uid)?.toSet() == setOf(PilotWire.CLIENT),"UID_PACKAGE")
        val info=pm.getPackageInfo(PilotWire.CLIENT,0)
        need(info.applicationInfo!!.uid == uid,"UID_PACKAGE")
        val cert=certificate(PilotWire.CLIENT)
        need(cert == certificate(context.packageName),"DISTINCT_SIGNER_NOT_PAIRED")
        return Principal(uid,PilotWire.CLIENT,cert,info.firstInstallTime,info.lastUpdateTime)
    }
}
