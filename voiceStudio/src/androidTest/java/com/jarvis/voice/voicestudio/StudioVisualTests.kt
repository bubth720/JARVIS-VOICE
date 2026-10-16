package com.jarvis.voice.voicestudio
import android.content.*
import android.provider.MediaStore
import android.view.*
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jarvis.voice.studio.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudioVisualTests {
    @Test fun lightAndDarkEmptyScreensHaveAccessibleActionsAndSyntheticOnlyScreenshots() {
        val i=InstrumentationRegistry.getInstrumentation();val app=i.targetContext.applicationContext as StudioTestApplication;val c=app.controller;val before=c.state
        assertFalse(c.busy);assertTrue(c.vault.list().isEmpty())
        try {
            for((mode,name) in listOf(AppCompatDelegate.MODE_NIGHT_NO to "005j-empty-light.png",AppCompatDelegate.MODE_NIGHT_YES to "005j-empty-dark.png")) {
                i.runOnMainSync { AppCompatDelegate.setDefaultNightMode(mode);c.update { StudioState() };c.notice("Tout reste sur votre téléphone. Aucune émotion native simulée.");app.fixture.available=false }
                val a=i.startActivitySync(Intent(i.targetContext,StudioActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as StudioActivity
                try {
                    i.waitForIdleSync()
                    i.runOnMainSync {
                        val talk=a.findViewById<View>(R.id.talk);assertTrue(talk.height>=48*a.resources.displayMetrics.density);assertEquals("Parler",(talk as TextView).text.toString())
                        assertFalse(talk.isEnabled)
                        val input=a.findViewById<View>(R.id.phrase);assertFalse(input.isSaveEnabled);assertEquals(View.IMPORTANT_FOR_AUTOFILL_NO,input.importantForAutofill)
                        // Only this instrumentation window, with an empty synthetic library, may be captured.
                        a.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    };Thread.sleep(400);i.waitForIdleSync()
                    val screenshot=requireNotNull(i.uiAutomation.takeScreenshot())
                    val values=android.content.ContentValues().apply { put(MediaStore.Downloads.DISPLAY_NAME,name);put(MediaStore.Downloads.MIME_TYPE,"image/png");put(MediaStore.Downloads.RELATIVE_PATH,"Download") }
                    val uri=requireNotNull(i.targetContext.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values))
                    requireNotNull(i.targetContext.contentResolver.openOutputStream(uri)).use { assertTrue(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)) };screenshot.recycle()
                } finally { i.runOnMainSync { a.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);a.finish() };i.waitForIdleSync() }
            }
        } finally { i.runOnMainSync { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);c.update { before } } }
    }
}
