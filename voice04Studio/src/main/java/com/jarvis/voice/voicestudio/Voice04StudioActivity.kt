package com.jarvis.voice.voicestudio
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.*
import android.text.InputType
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.jarvis.voice.studio.*
import com.jarvis.voice.identity.VoiceIdentityId

/** Navigation and SAF only. Screens and voice work live in separate components. */
class StudioActivity: AppCompatActivity() {
    lateinit var ui: StudioUi;private set
    lateinit var controller: StudioController;private set
    private var ready=false
    fun guard(action: ()->Unit) { try { action() } catch(t: Exception) { controller.error(t) } }
    fun confirm(title: String,body: String,action: ()->Unit) {
        MaterialAlertDialogBuilder(this).setTitle(title).setMessage(body).setNegativeButton("Annuler",null).setPositiveButton("Confirmer") { _,_->guard(action) }.showPrivate()
    }
    fun speedExport() { guard {
        require(!controller.busy && controller.speedReport!=null) { "Terminer une mesure d’abord." }
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { type="application/json";addCategory(Intent.CATEGORY_OPENABLE);putExtra(Intent.EXTRA_TITLE,"JARVIS-VOICE04-compteurs.json") },205)
    } }
    fun choose(code: Int) { guard {
        require(!controller.busy) { "Attendre la fin du travail ou appuyer sur Arrêter." }
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type=when(code){200->"application/zip";201->"audio/*";else->"application/octet-stream"};addCategory(Intent.CATEGORY_OPENABLE) },code)
    } }
    fun export(id: VoiceIdentityId) { guard {
        controller.pendingExport=id
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { type="application/octet-stream";addCategory(Intent.CATEGORY_OPENABLE);putExtra(Intent.EXTRA_TITLE,"JARVIS-VOICE-STUDIO.jvi") },203)
    } }
    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved);window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);ui=StudioUi(this)
        try { controller=(application as StudioApplication).controller } catch(_: Exception) {
            setContentView(TextView(this).apply { text="Coffre ou brouillon privé incompatible. Aucun effacement automatique. Garder 005I et ses sauvegardes ; ne pas désinstaller pour diagnostiquer.";setPadding(32,48,32,32) });return
        }
        ready=true;setContentView(R.layout.studio_shell)
        val light=(resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK)!=android.content.res.Configuration.UI_MODE_NIGHT_YES
        androidx.core.view.WindowCompat.getInsetsController(window,window.decorView).apply { isAppearanceLightStatusBars=light;isAppearanceLightNavigationBars=light }
        findViewById<MaterialToolbar>(R.id.toolbar).menu.add(0,R.id.advanced,0,"Réglages avancés").apply { setIcon(R.drawable.ic_settings);setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS) }
        findViewById<MaterialToolbar>(R.id.toolbar).setOnMenuItemClickListener { controller.navigate(StudioScreen.ADVANCED);true }
        findViewById<BottomNavigationView>(R.id.navigation).setOnItemSelectedListener { controller.navigate(when(it.itemId){R.id.voices->StudioScreen.VOICES;R.id.create->StudioScreen.CREATE;else->StudioScreen.SPEAK});true }
        findViewById<View>(R.id.stop).setOnClickListener { controller.stop() }
        render();status()
    }
    private fun render() {
        val content=when(controller.state.screen) {
            StudioScreen.SPEAK->SpeakScreen(this).view()
            StudioScreen.VOICES->VoicesScreen(this).view()
            StudioScreen.CREATE->WizardScreen(this).view()
            StudioScreen.ADVANCED->AdvancedScreen(this).view()
        }
        val frame=findViewById<FrameLayout>(R.id.content);frame.removeAllViews();frame.addView(content,FrameLayout.LayoutParams(if(resources.configuration.screenWidthDp>=720) ui.dp(720) else -1,-1,Gravity.CENTER))
        fun enabled(v: View) { v.isEnabled=!controller.busy;if(v is ViewGroup) for(i in 0 until v.childCount) enabled(v.getChildAt(i)) }
        if(controller.busy) enabled(content)
        val nav=findViewById<BottomNavigationView>(R.id.navigation)
        val id=when(controller.state.screen) { StudioScreen.VOICES->R.id.voices;StudioScreen.CREATE->R.id.create;else->R.id.speak }
        nav.menu.findItem(id).isChecked=true
    }
    private fun status() {
        findViewById<TextView>(R.id.status).text=controller.message
        findViewById<View>(R.id.stop).visibility=if(controller.busy) View.VISIBLE else View.GONE
        findViewById<LinearProgressIndicator>(R.id.progress).visibility=if(controller.busy) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.live_diagnostics)?.text=controller.diagnostics+"\n"+controller.metrics
    }
    @Deprecated("SAF for min26")
    override fun onActivityResult(code: Int,result: Int,data: Intent?) {
        super.onActivityResult(code,result,data);if(result!=Activity.RESULT_OK || !ready) return
        val uri=data?.data ?: return
        guard {
            controller.requireLocal(uri)
            when(code) {
                205->controller.exportSpeed(uri)
                200->controller.importPack(uri)
                201->controller.addSource(uri)
                204->password(false,uri,null)
                203->password(true,uri,requireNotNull(controller.pendingExport) { "Choisir de nouveau la voix à sauvegarder." })
            }
        }
    }
    private fun password(export: Boolean,uri: Uri,id: VoiceIdentityId?) {
        val col=ui.column();ui.text(col,if(export) "Cette sauvegarde sera chiffrée. Conserver le mot de passe séparément." else "Archive locale 005H/005I/005J. Aucun réencodage ; confirmer le profil après restauration.",14f)
        val entry=ui.input(col,"Mot de passe personnel (12–128 caractères)","",128).apply { inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        val repeat=if(export) ui.input(col,"Confirmer le mot de passe","",128).apply { inputType=entry.inputType } else null
        val legal=if(!export) ui.check(col,"J’ai le droit explicite et légal de cloner les voix de cette archive.",id=R.id.consent) else null
        val wav=if(export) ui.check(col,"Inclure aussi les extraits WAV encore conservés.") else null
        val dialog=MaterialAlertDialogBuilder(this).setTitle(if(export) "Sauvegarde privée" else "Restaurer ma voix").setView(ui.page(col)).setNegativeButton("Annuler",null).setPositiveButton("Continuer",null).create()
        dialog.setOnShowListener { dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val chars=CharArray(entry.length()) { entry.text!![it] };val confirmation=repeat?.let { v->CharArray(v.length()) { v.text!![it] } }
            val valid=chars.size in 12..128 && (confirmation==null || chars.contentEquals(confirmation)) && (legal==null || legal.isChecked);confirmation?.fill('\u0000')
            if(!valid) { chars.fill('\u0000');entry.error="12–128 caractères, confirmation identique et consentement requis." }
            else { entry.text?.clear();repeat?.text?.clear();dialog.dismiss();try { if(export) controller.export(uri,chars,wav?.isChecked==true,requireNotNull(id)) else controller.restore(uri,chars,true) } catch(t: Exception) { chars.fill('\u0000');controller.error(t) } }
        } }
        dialog.setOnDismissListener { entry.text?.clear();repeat?.text?.clear() };dialog.show();dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
    @Deprecated("Back compatible min26") override fun onBackPressed() {
        if(!ready) { super.onBackPressed();return }
        if(controller.state.screen==StudioScreen.CREATE && controller.state.wizard.step!=VoiceStep.CHOOSE) { controller.draft { it.copy(step=WizardPolicy.back(it.step)) };controller.navigate(StudioScreen.CREATE) }
        else if(controller.state.screen!=StudioScreen.SPEAK) controller.navigate(StudioScreen.SPEAK)
        else { controller.stop();super.onBackPressed() }
    }
    override fun onStart() { super.onStart();if(ready) { controller.observe(this) { rebuild -> if(!isFinishing) { if(rebuild) render();status() } };render();status() } }
    override fun onStop() { if(ready) { controller.flush();if(!isChangingConfigurations && controller.busy) controller.stop() };super.onStop() }
    override fun onDestroy() { if(ready) controller.unobserve(this);super.onDestroy() }
}
