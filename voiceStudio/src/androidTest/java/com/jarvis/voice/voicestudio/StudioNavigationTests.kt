package com.jarvis.voice.voicestudio
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudioNavigationTests {
    private val i get()=InstrumentationRegistry.getInstrumentation()
    private fun texts(v: View): List<String> = when(v) { is TextView -> listOf(v.text.toString());is ViewGroup -> (0 until v.childCount).flatMap { texts(v.getChildAt(it)) };else -> emptyList() }
    @Test fun firstUseHasHonestEmptyStateAndNoGenerationWithoutVoice() {
        val a=i.startActivitySync(Intent(i.targetContext,StudioActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as StudioActivity
        try { i.runOnMainSync {
            a.findViewById<View>(R.id.speak).performClick()
            assertFalse(a.findViewById<View>(R.id.talk).isEnabled)
            assertTrue(texts(a.window.decorView).any { it.contains("Aucune voix active") })
            assertTrue(a.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE!=0)
            assertEquals(View.GONE,a.findViewById<View>(R.id.stop).visibility)
        } } finally { i.runOnMainSync { a.finish() } }
    }
    @Test fun navigationSeparatesVoicesWizardAndTechnicalScreen() {
        val a=i.startActivitySync(Intent(i.targetContext,StudioActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as StudioActivity
        try { i.runOnMainSync {
            a.findViewById<View>(R.id.voices).performClick();assertTrue(texts(a.window.decorView).contains("Mes voix"))
            a.findViewById<View>(R.id.create).performClick();assertTrue(texts(a.window.decorView).contains("Créer une voix"))
            a.findViewById<View>(R.id.advanced).performClick();assertTrue(texts(a.window.decorView).contains("Réglages avancés"))
        } } finally { i.runOnMainSync { a.finish() } }
    }
}
