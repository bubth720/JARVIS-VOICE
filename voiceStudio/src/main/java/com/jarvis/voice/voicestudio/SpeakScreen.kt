package com.jarvis.voice.voicestudio
import android.view.*
import android.widget.LinearLayout
import androidx.core.widget.doAfterTextChanged
import com.jarvis.voice.studio.*
import com.jarvis.voice.expression.*

class SpeakScreen(private val a: StudioActivity) {
    private val u=a.ui;private val c=a.controller
    fun view(): View {
        val page=u.column();u.text(page,"Une voix. Votre JARVIS.",30f)
        val voice=c.confirmedProfile();val identity=c.currentIdentity()
        val card=u.card(page,identity?.name ?: "Votre voix",if(voice==null) "Aucune voix active confirmée. Importer une archive privée ou créer votre voix." else "Profil ${voice.id.take(8)} · choisi par vous\nTout reste hors ligne.")
        u.button(card,if(voice==null) "Choisir ma voix" else "Changer de voix") { c.navigate(StudioScreen.VOICES) }
        val model=u.text(page,if(c.hasModel()) "● Modèle FP32 disponible" else "○ Modèle absent · importer le ZIP FP32 existant",14f,true)
        if(!c.hasModel()) u.button(page,"Installer mon modèle") { a.choose(200) }
        val input=u.input(page,"Que voulez-vous faire dire à JARVIS ?",c.state.text,300,R.id.phrase,true)
        input.doAfterTextChanged { c.text(it.toString()) }
        u.button(page,"Parler",R.id.talk,true) { a.guard { c.generate() } }.isEnabled=voice!=null && c.hasModel() && !c.busy
        if(c.hasReplay()) u.button(page,"Réécouter",R.id.replay) { a.guard { c.replay() } }
        val options=LinearLayout(a).apply { orientation=LinearLayout.VERTICAL;visibility=View.GONE }
        u.button(page,"Options · pauses et contexte") { options.visibility=if(options.visibility==View.VISIBLE) View.GONE else View.VISIBLE }
        page.addView(options)
        val pause=u.toggle(options,"Pauses entre phrases",c.state.settings.pauses,R.id.pause_switch)
        val durations=u.spinner(options,"Silence entre phrases (ms)",listOf("0","250","500","1000","1500"),listOf(0,250,500,1000,1500).indexOf(c.state.settings.pauseMs))
        u.text(options,"Option explicite : écrire 2 ou 3 phrases complètes, une par ligne. Pas de découpage automatique. Désactiver si le texte est ambigu.",14f,true)
        val contexts=u.spinner(options,"Contexte (intention uniquement)",VoiceExpressionIntent.entries.map { it.name },c.state.settings.intent.ordinal)
        u.text(options,"Intention enregistrée ; effet émotionnel indisponible avec ce moteur. Aucune modification cachée de la voix ou du sampler.",14f,true)
        u.button(options,"Appliquer ces options") { a.guard {
            c.update { it.copy(settings=it.settings.copy(pauses=pause.isChecked,pauseMs=durations.selectedItem.toString().toInt(),intent=VoiceExpressionIntent.valueOf(contexts.selectedItem.toString()))) }
            c.notice(if(pause.isChecked) "Pauses demandées explicitement. Fournir 2–3 phrases complètes, une par ligne ; aucun changement silencieux du texte." else "Pauses désactivées. Rendu entier 005I conservé.")
        } }
        u.text(page,"Synthèse expérimentale · 005I reste votre référence de repli.",12f,true)
        return u.page(page)
    }
}
