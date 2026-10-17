package com.jarvis.core.voicepilot.fixture

import android.content.*
import android.content.pm.PackageManager
import android.os.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import com.jarvis.voice.bridgepilot.ipc.IVoicePilotFixture
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** EMULATOR ONLY. Two separately installed fixture APKs required; no models or personal files. */
@RunWith(AndroidJUnit4::class)
class BinderFixtureTests {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val component=ComponentName("com.jarvis.voice.bridgepilot","com.jarvis.voice.bridgepilot.VoicePilotService")
    private fun id()=UUID.randomUUID().toString()
    private fun bytes(fields: Map<String,String>)=JSONObject(fields).toString().toByteArray(Charsets.UTF_8)
    private fun base()=mapOf("version" to "voice.bridge.fixture/1","contract_sha256" to BuildConfig.CONTRACT_SHA,"session_id" to id())
    @Before fun emulatorOnlyAndRevoke() {
        Assume.assumeTrue(Build.HARDWARE in setOf("ranchu","goldfish") || Build.FINGERPRINT.startsWith("generic"))
        context.startActivity(Intent().setComponent(ComponentName(component.packageName,"${component.packageName}.PilotActivity")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val revoke=device.wait(Until.findObject(By.text("Révoquer")),5000) ?: error("Fixture visible required")
        revoke.click()
    }
    private fun withEndpoint(block: (IVoicePilotFixture)->Unit) {
        var remote: IVoicePilotFixture?=null;val connected=CountDownLatch(1)
        val connection=object: ServiceConnection {
            override fun onServiceConnected(name: ComponentName,binder: IBinder) { remote=IVoicePilotFixture.Stub.asInterface(binder);connected.countDown() }
            override fun onServiceDisconnected(name: ComponentName) { remote=null }
        }
        var bound=false
        try {
            bound=context.bindService(Intent().setComponent(component),connection,Context.BIND_AUTO_CREATE)
            assertTrue(bound);assertTrue(connected.await(5,TimeUnit.SECONDS));block(remote ?: error("No endpoint"))
        } finally { if(bound) context.unbindService(connection) }
    }
    private fun authorizeVisible() {
        device.findObject(By.text("Vérifier puis autoriser le client public pour 60 secondes")).click()
        val allow=device.wait(Until.findObject(By.text("Autoriser")),3000) ?: error("Certificate confirmation missing")
        allow.click()
    }
    @Test fun signaturePermissionIsActuallyGrantedOrDeniedByAndroid() {
        val permission=context.checkSelfPermission("com.jarvis.voice.bridgepilot.permission.BIND")
        assertEquals(if(context.packageName.endsWith("untrusted")) PackageManager.PERMISSION_DENIED else PackageManager.PERMISSION_GRANTED,permission)
    }
    @Test fun unarmedCallerCannotPassMalformedPayload() {
        if(context.packageName.endsWith("untrusted")) {
            assertThrows(SecurityException::class.java){withEndpoint { it.hello(byteArrayOf(0xff.toByte()),Binder()) }}
        } else withEndpoint { remote ->assertThrows(SecurityException::class.java){remote.hello(byteArrayOf(0xff.toByte()),Binder())} }
    }
    @Test fun authorizedPublicRequestIsCorrelatedButNeverGeneratesWithoutCoreProof() {
        Assume.assumeTrue(context.packageName=="com.jarvis.core.voicepilot.fixture")
        authorizeVisible()
        withEndpoint { remote ->
            val hello=base();val h=JSONObject(String(remote.hello(bytes(hello),Binder()),Charsets.UTF_8))
            assertEquals("false",h.getString("native_generation_available"));assertEquals(BuildConfig.CONTRACT_SHA,h.getString("contract_sha256"))
            val r=hello+mapOf("correlation_id" to id(),"cancellation_id" to id(),"grant_ref" to h.getString("grant_ref"),"core_epoch" to id(),
                "lease_id" to id(),"generation" to "1","remaining_ms" to "1000","text" to "Bonjour, ceci est un test public.")
            val result=JSONObject(String(remote.request(bytes(r)),Charsets.UTF_8))
            assertEquals(r["correlation_id"],result.getString("correlation_id"));assertEquals("NATIVE_AUTHORITY_UNAVAILABLE",result.getString("code"))
            assertFalse(result.has("text"));assertEquals("REFUSED",result.getString("phase"))
            val replay=JSONObject(String(remote.request(bytes(r)),Charsets.UTF_8));assertEquals("GRANT_OR_REPLAY",replay.getString("code"))
            device.findObject(By.text("Révoquer")).click()
            assertThrows(SecurityException::class.java){remote.request(bytes(r))}
        }
    }
    @Test fun oversizedTransactionRejectedBeforeJsonParsing() {
        Assume.assumeTrue(context.packageName=="com.jarvis.core.voicepilot.fixture")
        authorizeVisible();withEndpoint { remote ->assertThrows(SecurityException::class.java){remote.hello(ByteArray(16384),Binder())} }
    }
}
