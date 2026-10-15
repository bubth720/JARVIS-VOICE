package com.jarvis.voice.pocket

import kotlin.math.*
import java.util.Locale

data class ReferenceAssessment(val rmsDb: Double,val peak: Double,val clippedPercent: Double,
    val quietFramePercent: Double,val leadingQuietSeconds: Double,val trailingQuietSeconds: Double,val warning: String) {
    fun summary()=String.format(Locale.FRENCH,"Référence : RMS %.1f dBFS, pic %.3f, écrêtage %.2f %%, fenêtres de 20 ms silencieuses (< −45 dBFS) %.1f %%, silence début/fin %.2f/%.2f s. %s\nAnalyse d'énergie uniquement : ni reconnaissance de parole, ni preuve d'absence de bruit/musique/voix multiples.",rmsDb,peak,clippedPercent,quietFramePercent,leadingQuietSeconds,trailingQuietSeconds,warning)
}
object ReferenceSelection {
    fun assess(pcm: FloatArray): ReferenceAssessment {
        require(pcm.size in 120000..360000 && pcm.all { it.isFinite() })
        val level=20*log10(max(sqrt(pcm.sumOf { it.toDouble()*it }/pcm.size),1e-12))
        val quiet=(pcm.indices step 480).map { begin ->
            val end=minOf(begin+480,pcm.size)
            sqrt((begin until end).sumOf { pcm[it].toDouble()*pcm[it] }/(end-begin)) < 10.0.pow(-45.0/20)
        }
        val clipped=100.0*pcm.count { abs(it)>=.999f }/pcm.size
        val fraction=100.0*quiet.count { it }/quiet.size
        val warnings=mutableListOf<String>()
        if(clipped>1) warnings.add("Écrêtage important : choisir un autre extrait.")
        if(level < -35) warnings.add("Niveau faible : préférer un enregistrement plus audible.")
        if(fraction>35) warnings.add("Silences longs : choisir une portion parlée continue.")
        if(level > -8) warnings.add("Niveau élevé : vérifier saturation ou traitement agressif.")
        if(warnings.isEmpty()) warnings.add("Niveau exploitable ; écouter la parole et son environnement.")
        return ReferenceAssessment(level,pcm.maxOf { abs(it).toDouble() },clipped,fraction,
            quiet.takeWhile { it }.size*.02,quiet.asReversed().takeWhile { it }.size*.02,warnings.joinToString(" "))
    }
    /** Suggest a contiguous five-second window; do not splice speech or alter gain. */
    fun suggestFiveSeconds(pcm: FloatArray): IntRange {
        assess(pcm)
        var bestStart=0;var best=Double.POSITIVE_INFINITY
        for(start in 0..pcm.size-120000 step 4800) {
            val assessment=assess(pcm.copyOfRange(start,start+120000))
            val score=assessment.quietFramePercent+10*assessment.clippedPercent+maxOf(0.0,-35-assessment.rmsDb)
            if(score<best) { best=score;bestStart=start }
        }
        return bestStart until bestStart+120000
    }
}
