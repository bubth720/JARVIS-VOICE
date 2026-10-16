package com.jarvis.voice.voicestudio
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.jarvis.voice.studio.*
import java.io.File

class StudioActivity: AppCompatActivity() {
    private lateinit var ui: StudioUi
    private lateinit var stateStore: StudioStateStore
    private var state=StudioState()
    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved);window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        stateStore=StudioStateStore(File(noBackupFilesDir,"studio-state.json"));state=stateStore.load();ui=StudioUi(this)
        setContentView(R.layout.studio_shell)
        findViewById<MaterialToolbar>(R.id.toolbar).menu.add(0,R.id.advanced,0,"Réglages avancés").apply { setIcon(R.drawable.ic_settings);setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS) }
        findViewById<MaterialToolbar>(R.id.toolbar).setOnMenuItemClickListener { navigate(StudioScreen.ADVANCED);true }
        findViewById<BottomNavigationView>(R.id.navigation).setOnItemSelectedListener { navigate(when(it.itemId) { R.id.voices -> StudioScreen.VOICES;R.id.create -> StudioScreen.CREATE;else -> StudioScreen.SPEAK });true }
        render()
    }
    private fun navigate(screen: StudioScreen) { state=state.copy(screen=screen);stateStore.save(state);render() }
    private fun render() {
        val page=ui.column()
        when(state.screen) {
            StudioScreen.SPEAK -> { ui.text(page,"Une voix. Votre JARVIS.",30f);val card=ui.card(page,"Votre voix","Aucune voix active. Importez une archive privée ou créez votre voix.");ui.button(card,"Choisir ma voix") { navigate(StudioScreen.VOICES) };ui.input(page,"Que voulez-vous faire dire à JARVIS ?",state.text,300,R.id.phrase,true);ui.button(page,"Parler",R.id.talk,true) {}.isEnabled=false }
            StudioScreen.VOICES -> { ui.text(page,"Mes voix",30f);ui.text(page,"Votre bibliothèque est vide. Vos profils restent sur ce téléphone.");ui.button(page,"Ajouter ma première voix",primary=true) { navigate(StudioScreen.CREATE) } }
            StudioScreen.CREATE -> { ui.text(page,"Créer une voix",30f);ui.text(page,"Un parcours guidé : choisir, consentir, écouter, créer et sauvegarder.");ui.text(page,"Étape 1 sur 7 · Choisir") }
            StudioScreen.ADVANCED -> { ui.text(page,"Réglages avancés",30f);ui.text(page,"Les réglages techniques seront isolés ici. Le moteur et la qualité 005I restent inchangés.");ui.button(page,"Revenir à Parler") { navigate(StudioScreen.SPEAK) } }
        }
        findViewById<FrameLayout>(R.id.content).apply { removeAllViews();addView(ui.page(page),FrameLayout.LayoutParams(if(resources.configuration.screenWidthDp>=720) ui.dp(720) else -1,-1,Gravity.CENTER)) }
    }
}
