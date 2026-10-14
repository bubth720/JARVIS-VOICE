package com.jarvis.voice.voiceprototype

import android.content.Intent
import android.app.Activity
import android.net.Uri
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jarvis.voice.pocket.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Platform/privacy tests use synthetic PCM only, no gated weights or voice impersonation. */
@RunWith(AndroidJUnit4::class)
class PrototypePrivacyTests {
    private fun text(view: View): List<String> = when(view) {
        is TextView -> listOf(view.text.toString())
        is ViewGroup -> (0 until view.childCount).flatMap { text(view.getChildAt(it)) }
        else -> emptyList()
    }
    @Test fun independentPackageHasNoNetworkMicrophoneOrBackup() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.jarvis.voice.voiceprototype005e",context.packageName)
        val info=context.packageManager.getPackageInfo(context.packageName,PackageManager.GET_PERMISSIONS)
        val permissions=info.requestedPermissions?.toSet().orEmpty()
        assertFalse(permissions.contains("android.permission.INTERNET"))
        assertFalse(permissions.contains("android.permission.RECORD_AUDIO"))
        assertFalse(permissions.contains("android.permission.MANAGE_EXTERNAL_STORAGE"))
        assertEquals(0,context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }
    @Test fun activityClearlySignalsExperimentalStatus() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val activity=instrumentation.startActivitySync(Intent(instrumentation.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            instrumentation.runOnMainSync {
                val labels=text(activity.window.decorView)
                assertTrue(labels.any { it.contains("EXPÉRIMENTAL") })
                assertTrue(labels.any { it.contains("005D") })
                assertTrue(labels.any { it.contains("ARRÊT") })
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
    @Test fun privateWavStaysInNoBackupSandboxAndCanBeDeleted() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File(context.noBackupFilesDir,"synthetic-instrumentation.wav")
        try {
            val pcm=FloatArray(120000) { kotlin.math.sin(it*.01).toFloat()*.1f }
            ReferenceAudio.writeWav(file,pcm)
            assertEquals(120000,PrivateWav.read(file).size)
            assertTrue(file.canonicalPath.startsWith(context.noBackupFilesDir.canonicalPath+File.separator))
            assertTrue(file.delete()); assertFalse(file.exists())
        } finally { file.delete() }
    }
    @Test fun firstPrivateImportReachesZipValidationInsteadOfCheckingANonexistentFolder() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        org.junit.Assume.assumeTrue(context.noBackupFilesDir.usableSpace>=1200L*1048576)
        val zip=File(context.noBackupFilesDir,"synthetic-invalid-pack.zip")
        ZipOutputStream(zip.outputStream()).use { out -> out.putNextEntry(ZipEntry("wrong-model.txt")); out.write(byteArrayOf(1)); out.closeEntry() }
        val activity=instrumentation.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        try {
            instrumentation.runOnMainSync {
                val callback=MainActivity::class.java.getDeclaredMethod("onActivityResult",Integer.TYPE,Integer.TYPE,Intent::class.java)
                callback.isAccessible=true // our application callback, not a hidden Android API
                callback.invoke(activity,200,Activity.RESULT_OK,Intent().setData(Uri.fromFile(zip)))
            }
            var observed=false
            val deadline=System.currentTimeMillis()+10000
            while(!observed && System.currentTimeMillis()<deadline) {
                instrumentation.runOnMainSync { observed=text(activity.window.decorView).any { it.contains("Entrée ZIP FP32 inconnue") } }
                if(!observed) Thread.sleep(50)
            }
            assertTrue("Private import did not reach strict ZIP validation",observed)
            assertFalse(File(context.noBackupFilesDir,"models/fp32").exists())
        } finally { zip.delete(); instrumentation.runOnMainSync { activity.finish() } }
    }
}
