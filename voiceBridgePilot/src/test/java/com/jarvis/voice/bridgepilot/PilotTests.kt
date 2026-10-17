package com.jarvis.voice.bridgepilot

import android.app.Application
import android.content.Intent
import android.content.pm.*
import android.os.*
import com.jarvis.voice.bridgepilot.ipc.IVoicePilotFixture
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBinder
import java.util.UUID

/** Android APIs simulated by Robolectric; never a cross-APK kernel Binder proof. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class PilotTests {
    private fun id()=UUID.randomUUID().toString()
    private val peer=Principal(12345,PilotWire.CLIENT,"a".repeat(64),10,20)
    private var now=100L
    private val auth=PilotAuthorization { now }
    private fun hello(session: String=id())=mapOf("version" to PilotWire.VERSION,"contract_sha256" to BuildConfig.CONTRACT_SHA,"session_id" to session)
    private fun request(h: Map<String,String>)=h.filterKeys { it in PilotWire.base }+mapOf("correlation_id" to id(),"cancellation_id" to id(),
        "grant_ref" to h.getValue("grant_ref"),"core_epoch" to id(),"lease_id" to id(),"generation" to "1","text" to "Bonjour, ceci est un test public.","remaining_ms" to "1000")
    private fun arm() { auth.visibility(true);auth.arm(peer) }
    private fun installed(context: android.content.Context,name: String,uid: Int,sign: String="common-test-only") {
        val info=PackageInfo().apply {
            packageName=name;applicationInfo=ApplicationInfo().apply { packageName=name;this.uid=uid }
            signingInfo=SigningInfo().also { Shadows.shadowOf(it).setSignatures(arrayOf(Signature(sign.toByteArray()))) }
            firstInstallTime=10;lastUpdateTime=20
        }
        Shadows.shadowOf(context.packageManager).installPackage(info)
        Shadows.shadowOf(context.packageManager).setPackagesForUid(uid,name)
    }
    @After fun cleanup() { PilotRuntime.authorization.visibility(false);ShadowBinder.reset() }
    @Test fun defaultDisabledAndLocalOptInMustBeVisible() {
        assertThrows(Refused::class.java){auth.authenticate(peer)}
        assertThrows(Refused::class.java){auth.arm(peer)}
        arm();auth.authenticate(peer);auth.visibility(false)
        assertThrows(Refused::class.java){auth.authenticate(peer)}
    }
    @Test fun helloCannotClaimNativeCapabilitiesOrExposeProfiles() {
        arm();val h=auth.hello(peer,hello())
        assertEquals("false",h["native_generation_available"]);assertEquals("false",h["global_admission_available"])
        assertFalse(h.keys.any { it.contains("profile") || it.contains("pcm") })
    }
    @Test fun selfDeclaredGlobalLeaseNeverAuthorizesGeneration() {
        arm();val r=request(auth.hello(peer,hello()));val response=auth.request(peer,r)
        assertEquals("REFUSED",response["phase"]);assertEquals("NATIVE_AUTHORITY_UNAVAILABLE",response["code"])
        assertFalse(response.containsKey("text"));assertEquals(r.filterKeys { it in PilotWire.key },response.filterKeys { it in PilotWire.key })
    }
    @Test fun grantCannotBeReplayedOrReissuedAfterConsumption() {
        arm();val h=auth.hello(peer,hello());val r=request(h);auth.request(peer,r)
        assertThrows(Refused::class.java){auth.request(peer,r)}
        assertThrows(Refused::class.java){auth.hello(peer,h.filterKeys { it in PilotWire.base })}
    }
    @Test fun sessionAndGrantAreBoundToRealPrincipal() {
        arm();val r=request(auth.hello(peer,hello()))
        for(principal in listOf(peer.copy(uid=77),peer.copy(certificate="b".repeat(64)),peer.copy(installed=11),peer.copy(updated=21)))
            assertThrows(Refused::class.java){auth.request(principal,r)}
        for(change in listOf(mapOf("session_id" to id()),mapOf("grant_ref" to id())))
            assertThrows(Refused::class.java){auth.request(peer,r+change)}
    }
    @Test fun expirationRevocationAndRestartInvalidateAuthority() {
        arm();val r=request(auth.hello(peer,hello()));now=60101
        assertThrows(Refused::class.java){auth.request(peer,r)}
        now=100;arm();auth.revoke();assertThrows(Refused::class.java){auth.request(peer,r)}
        assertThrows(Refused::class.java){PilotAuthorization { now }.request(peer,r)}
    }
    @Test fun budgetCannotOutliveLocalAuthorizationOrExceedProtocol() {
        arm();val r=request(auth.hello(peer,hello()))
        for(budget in listOf("90001","60001","0","01","-1"))
            assertThrows(Refused::class.java){auth.request(peer,r+("remaining_ms" to budget))}
    }
    @Test fun textIsNeverTruncatedAndLegacyValidationAgrees() {
        for(text in listOf("Bonjour.","😀".repeat(150),"x".repeat(301),"\uD800","<s>abc","", "\u0000", "Une phrase.\nUne autre.")) {
            val legacy=runCatching { com.jarvis.voice.bridge.validateSpeechText(text) }.isSuccess
            assertEquals(legacy,PilotWire.validText(text))
        }
        arm();val r=request(auth.hello(peer,hello()));assertThrows(Refused::class.java){auth.request(peer,r+("text" to "x".repeat(301)))}
        assertEquals("REFUSED",auth.request(peer,r)["phase"])
    }
    @Test fun oldResultsAndChangedGenerationAreRefused() {
        arm();val r=request(auth.hello(peer,hello()));auth.request(peer,r);val key=r.filterKeys { it in PilotWire.key }
        for(field in listOf("correlation_id","cancellation_id","core_epoch","lease_id","generation")) {
            val value=if(field=="generation") "2" else id()
            assertThrows(Refused::class.java){auth.status(peer,key+(field to value))}
        }
        assertEquals(auth.status(peer,key),auth.status(peer,key))
    }
    @Test fun concurrentRequestsConsumeAtMostOneGrant() {
        arm();val h=auth.hello(peer,hello());val pool=java.util.concurrent.Executors.newFixedThreadPool(4)
        try {
            val outcomes=(1..8).map { pool.submit<Boolean> { runCatching { auth.request(peer,request(h)) }.isSuccess } }.map { it.get() }
            assertEquals(1,outcomes.count { it })
        } finally { pool.shutdown() }
    }
    @Test fun strictJsonRejectsMalformedDuplicateEscapedUnknownAndNonStringFields() {
        for(bytes in listOf("{\"version\":\"a\",\"ver\\u0073ion\":\"b\"}".toByteArray(),"{\"version\":1}".toByteArray(),
            "{'version':'a'}".toByteArray(),byteArrayOf(0xc3.toByte()),ByteArray(4097),"{} trailing".toByteArray()))
            assertThrows(Refused::class.java){PilotWire.decode(bytes,PilotWire.base)}
        val h=hello();assertEquals(h,PilotWire.decode(PilotWire.encode(h),PilotWire.base))
        assertThrows(Refused::class.java){PilotWire.decode(PilotWire.encode(h+("profile_uri" to "file://private")),PilotWire.base)}
    }
    @Test fun keyRejectsNonCanonicalIdsAndInvalidGeneration() {
        arm();val r=request(auth.hello(peer,hello()))
        for(change in listOf(mapOf("lease_id" to "legacy"),mapOf("generation" to "9007199254740992"),mapOf("generation" to "01")))
            assertThrows(Refused::class.java){auth.request(peer,r+change)}
    }
    @Test fun packageManagerResolvesRealCallingUidAndSameCertificate() {
        val ctx=RuntimeEnvironment.getApplication();installed(ctx,ctx.packageName,10001);installed(ctx,PilotWire.CLIENT,12345)
        ShadowBinder.setCallingUid(12345)
        assertEquals(12345,PilotIdentity(ctx).candidate(Binder.getCallingUid()).uid)
    }
    @Test fun wrongUidCertificateAndSharedUidAreDenied() {
        val ctx=RuntimeEnvironment.getApplication();installed(ctx,ctx.packageName,10001);installed(ctx,PilotWire.CLIENT,12345)
        assertThrows(Refused::class.java){PilotIdentity(ctx).candidate(12346)}
        installed(ctx,PilotWire.CLIENT,12345,"wrong-key");assertThrows(Refused::class.java){PilotIdentity(ctx).candidate(12345)}
        installed(ctx,PilotWire.CLIENT,12345);Shadows.shadowOf(ctx.packageManager).setPackagesForUid(12345,PilotWire.CLIENT,"shared.other")
        assertThrows(Refused::class.java){PilotIdentity(ctx).candidate(12345)}
    }
    @Test fun actualStubRefusesCallerBeforeMalformedPayloadDeserialization() {
        val controller=Robolectric.buildService(VoicePilotService::class.java).create()
        val endpoint=controller.get().onBind(Intent()) as IVoicePilotFixture.Stub
        val parcel=Parcel.obtain()
        try { parcel.writeByteArray(byteArrayOf(0xff.toByte()));assertThrows(SecurityException::class.java){endpoint.onTransact(IBinder.FIRST_CALL_TRANSACTION,parcel,null,0)} }
        finally { parcel.recycle();controller.destroy() }
    }
    @Test fun authorizedStubStillRefusesNativeGenerationAndRevokesOnUnbind() {
        val ctx=RuntimeEnvironment.getApplication();installed(ctx,ctx.packageName,10001);installed(ctx,PilotWire.CLIENT,12345)
        ShadowBinder.setCallingUid(12345);val p=PilotIdentity(ctx).candidate(12345)
        PilotRuntime.authorization.visibility(true);PilotRuntime.authorization.arm(p)
        val controller=Robolectric.buildService(VoicePilotService::class.java).create();val service=controller.get()
        val endpoint=IVoicePilotFixture.Stub.asInterface(service.onBind(Intent()))
        try {
            val names=PilotWire.base+setOf("voice_epoch","grant_ref","expires_elapsed_ms","scope","native_generation_available","global_admission_available")
            val h=PilotWire.decode(endpoint.hello(PilotWire.encode(hello()),Binder()),names)
            val response=PilotWire.decode(endpoint.request(PilotWire.encode(request(h))),PilotWire.key+setOf("voice_epoch","phase","code"))
            assertEquals("NATIVE_AUTHORITY_UNAVAILABLE",response["code"])
            service.onUnbind(Intent());assertThrows(Refused::class.java){endpoint.status(PilotWire.encode(response.filterKeys { it in PilotWire.key }))}
        } finally { controller.destroy() }
    }
    @Test fun activityIsOptInAndPauseRevokes() {
        val activity=Robolectric.buildActivity(PilotActivity::class.java).setup()
        assertThrows(Refused::class.java){PilotRuntime.authorization.authenticate(peer)}
        activity.pause().stop().destroy()
    }
}
