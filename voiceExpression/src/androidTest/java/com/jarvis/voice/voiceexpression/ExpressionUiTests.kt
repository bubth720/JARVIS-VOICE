package com.jarvis.voice.voiceexpression
import android.content.Intent
import android.widget.Spinner
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExpressionUiTests {
    @Test fun intentSelectionReportsUnsupportedAcousticsAndKeepsAllSynthesisSettings() {
        val i=InstrumentationRegistry.getInstrumentation();val c=i.targetContext
        val a=i.startActivitySync(Intent(c,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        fun field(name: String): Any=MainActivity::class.java.getDeclaredField(name).apply { isAccessible=true }.get(a)!!
        try { i.runOnMainSync {
            val style=field("expression") as Spinner;val steps=field("steps") as Spinner;val temp=field("temperature") as Spinner
            val before=steps.selectedItem.toString()+temp.selectedItem.toString()
            style.setSelection(6)
            MainActivity::class.java.getDeclaredMethod("updateExpressionStatus").apply { isAccessible=true }.invoke(a)
            val status=(field("expressionStatus") as TextView).text.toString()
            assertTrue(status.contains("NON PRISE EN CHARGE"));assertEquals(before,steps.selectedItem.toString()+temp.selectedItem.toString())
            style.setSelection(7);MainActivity::class.java.getDeclaredMethod("updateExpressionStatus").apply { isAccessible=true }.invoke(a)
            assertTrue((field("expressionStatus") as TextView).text.contains("rendu 005G conservé"))
        } } finally { i.runOnMainSync { a.finish() } }
    }
    @Test fun restoredActiveProfileNeedsExplicit005iChoiceAndConfirmationSurvivesRestart() {
        val i=InstrumentationRegistry.getInstrumentation();val c=i.targetContext
        val v=com.jarvis.voice.identity.VoiceIdentityVault(java.io.File(c.noBackupFilesDir,"identity-vault"));val previous=v.selected()
        val choice=java.io.File(c.noBackupFilesDir,"explicit-profile-choice.json");val before=choice.takeIf { it.exists() }?.readBytes();choice.delete()
        val id=SyntheticVault.identity(v,"SYNTHETIC-EXPLICIT-CHOICE");val ref=SyntheticVault.reference(v,id)
        val pa=v.saveProfile(id,ref,SyntheticVault.profile(),com.jarvis.voice.pocket.CancellationToken())
        val pb=v.saveProfile(id,ref,SyntheticVault.profile(.1f),com.jarvis.voice.pocket.CancellationToken());v.activate(id,pb)
        val profiles=v.list().single { it.id==id }.profiles
        val check=MainActivity::class.java.getDeclaredMethod("requireConfirmedProfile",com.jarvis.voice.identity.VoiceProfile::class.java).apply { isAccessible=true }
        var a=i.startActivitySync(Intent(c,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        try {
            try { check.invoke(a,profiles.single { it.id==pb });fail("Explicit confirmation required") } catch(e: java.lang.reflect.InvocationTargetException) { assertTrue(e.cause is IllegalArgumentException) }
            i.runOnMainSync {
                val spinner=MainActivity::class.java.getDeclaredField("profiles").apply { isAccessible=true }.get(a) as Spinner
                spinner.setSelection(profiles.indexOfFirst { it.id==pb })
                MainActivity::class.java.getDeclaredMethod("activate",java.lang.Boolean.TYPE).apply { isAccessible=true }.invoke(a,false)
            }
            val busy=MainActivity::class.java.getDeclaredField("busy").apply { isAccessible=true };val end=android.os.SystemClock.elapsedRealtime()+10000
            var running=true
            while(running && android.os.SystemClock.elapsedRealtime()<end) { i.runOnMainSync { running=busy.getBoolean(a) };if(running) Thread.sleep(20) }
            assertFalse(running);check.invoke(a,profiles.single { it.id==pb })
            try { check.invoke(a,profiles.single { it.id==pa });fail("Different profile must fail") } catch(e: java.lang.reflect.InvocationTargetException) { assertTrue(e.cause is IllegalArgumentException) }
            i.runOnMainSync { a.finish() }
            a=i.startActivitySync(Intent(c,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            check.invoke(a,profiles.single { it.id==pb });assertEquals(pb,v.list().single { it.id==id }.activeProfiles[com.jarvis.voice.identity.PocketIdentity.bindingKey])
        } finally {
            i.runOnMainSync { a.finish() };v.removeIdentity(id,true);if(previous!=null) v.select(previous)
            if(before==null) choice.delete() else choice.writeBytes(before)
        }
    }
}
