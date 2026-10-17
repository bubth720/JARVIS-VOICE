package com.jarvis.core.voicepilot.fixture

import android.app.Activity
import android.content.*
import android.os.*
import android.widget.*
import com.jarvis.voice.bridgepilot.ipc.IVoicePilotFixture
import java.util.concurrent.Executors

/** Separate public adversarial APKs. No CORE app, personal data, model or implicit fallback. */
class ClientActivity: Activity(),ServiceConnection {
    private val worker=Executors.newSingleThreadExecutor()
    private var bound=false
    private var closed=false
    private lateinit var status: TextView
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        status=TextView(this).apply { text="Client public V1. Autorisation visible côté VOICE requise. Aucun moteur/audio." }
        setContentView(LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL;addView(status)
            addView(Button(this@ClientActivity).apply { text="Tester un payload invalide (aucune génération)";setOnClickListener { if(!bound) connect() } })
        })
    }
    private fun verifyEndpoint() {
        val pm=packageManager;val pkg="com.jarvis.voice.bridgepilot"
        val info=pm.getApplicationInfo(pkg,0)
        check(pm.getPackagesForUid(info.uid)?.toSet()==setOf(pkg))
        fun signer(name: String): ByteArray {
            if(Build.VERSION.SDK_INT>=28) {
                val sign=pm.getPackageInfo(name,android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES).signingInfo ?: error("NO_SIGNER")
                check(!sign.hasMultipleSigners() && (sign.signingCertificateHistory?.size ?: 0)<=1)
                return sign.apkContentsSigners.single().toByteArray()
            }
            @Suppress("DEPRECATION") val sign=pm.getPackageInfo(name,android.content.pm.PackageManager.GET_SIGNATURES).signatures
            return sign!!.single().toByteArray()
        }
        check(signer(pkg).contentEquals(signer(packageName)))
        val permission=pm.getPermissionInfo("com.jarvis.voice.bridgepilot.permission.BIND",0)
        @Suppress("DEPRECATION") val level=permission.protectionLevel and android.content.pm.PermissionInfo.PROTECTION_MASK_BASE
        check(permission.packageName==pkg && level==android.content.pm.PermissionInfo.PROTECTION_SIGNATURE)
    }
    private fun connect() {
        try {
            verifyEndpoint()
            val component=ComponentName("com.jarvis.voice.bridgepilot","com.jarvis.voice.bridgepilot.VoicePilotService")
            val info=packageManager.resolveService(Intent().setComponent(component),0)?.serviceInfo ?: error("SERVICE_ABSENT")
            check(info.exported && info.permission=="com.jarvis.voice.bridgepilot.permission.BIND")
            bound=bindService(Intent().setComponent(component),this,Context.BIND_AUTO_CREATE)
            if(!bound) status.text="Liaison refusée."
        } catch(_: Exception) { status.text="Service absent ou liaison non autorisée." }
    }
    override fun onServiceConnected(name: ComponentName,binder: IBinder) {
        worker.execute {
            val result=try {
                check(name==ComponentName("com.jarvis.voice.bridgepilot","com.jarvis.voice.bridgepilot.VoicePilotService"));verifyEndpoint()
                IVoicePilotFixture.Stub.asInterface(binder).hello(byteArrayOf(0xff.toByte()),Binder())
                "Requête traitée/refusée par le service ; ce résultat ne prouve aucune génération."
            } catch(_: Exception) { "Appel refusé. Vérifier certificat, package et autorisation visible." }
            runOnUiThread { if(!closed) status.text=result }
        }
    }
    override fun onServiceDisconnected(name: ComponentName) { if(!closed) status.text="Service déconnecté. Aucun repli automatique." }
    override fun onDestroy() { closed=true;if(bound) unbindService(this);worker.shutdown();super.onDestroy() }
}
