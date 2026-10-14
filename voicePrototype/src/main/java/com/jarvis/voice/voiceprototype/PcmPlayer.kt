package com.jarvis.voice.voiceprototype

import android.content.Context
import android.media.*
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.jarvis.voice.pocket.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

/** Complete delayed PCM: STATIC avoids STREAM's device-dependent start threshold. */
class PcmPlayer(context: Context, private val token: CancellationToken,
    private val diagnostic: (String) -> Unit = {}, private val onStarted: () -> Unit = {}) : AutoCloseable {
    private val manager=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val active=AtomicReference<AudioTrack?>()
    private val attributes=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    @Volatile private var focusLost=false
    private var focus="non demandé"
    private val focusRequest=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(attributes).setOnAudioFocusChangeListener({ change ->
            if(change==AudioManager.AUDIOFOCUS_LOSS || change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                focusLost=true; stopTrack()
            }
        },Handler(Looper.getMainLooper())).build()
    var submitted=0L; private set
    var zeroWrites=0; private set
    var writes=0; private set
    var lastHead=0L; private set
    var completed=false; private set
    var evidence="aucune"; private set
    var lastSnapshot="AudioTrack : pas encore créé"; private set
    var underruns=0; private set
    private var announcedStart=false
    private var launchNs=0L
    private fun checkRunning() {
        if(focusLost) throw PlaybackFailure("FOCUS_PERDU")
        token.check()
    }
    private fun snapshot(phase: String) {
        val track=active.get()
        if(track!=null) {
            lastHead=Integer.toUnsignedLong(track.playbackHeadPosition)
            underruns=track.underrunCount
        }
        val device=track?.routedDevice
        lastSnapshot="AudioTrack $phase : mode STATIC PCM16 ; état ${track?.state ?: -1} ; playState ${track?.playState ?: -1} (3=play, 2=pause, 1=stop) ; fréquence ${track?.sampleRate ?: 24000} Hz ; volume piste 1,0 ; volume média ${manager.getStreamVolume(AudioManager.STREAM_MUSIC)}/${manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)}, muet ${manager.isStreamMute(AudioManager.STREAM_MUSIC)} ; focus $focus ; route type ${device?.type ?: -1}, id ${device?.id ?: -1} ; écritures $writes, nulles $zeroWrites ; submitted $submitted ; playbackHeadPosition $lastHead ; underruns $underruns ; preuve $evidence."
        diagnostic(lastSnapshot)
    }
    fun write(pcm: FloatArray) {
        checkRunning()
        require(pcm.size in 1..480000 && pcm.all { it.isFinite() }) { "PCM non fini, vide ou supérieur à 20 secondes" }
        check(active.get()==null) { "Une seule phrase complète par AudioTrack STATIC" }
        val track=AudioTrack.Builder().setAudioAttributes(attributes)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(24000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(pcm.size*2).setTransferMode(AudioTrack.MODE_STATIC).build()
        active.set(track)
        try {
            snapshot("créé")
            if(track.setVolume(1f)!=AudioTrack.SUCCESS) throw PlaybackFailure("VOLUME_REFUSE")
            val converted=ShortArray(pcm.size) { i ->
                if(i%4096==0) checkRunning()
                (pcm[i].coerceIn(-1f,1f)*32767).roundToInt().toShort()
            }
            checkRunning()
            val count=track.write(converted,0,converted.size,AudioTrack.WRITE_NON_BLOCKING)
            writes++; if(count==0) zeroWrites++
            if(count>0) submitted=count.toLong()
            snapshot("chargé")
            if(count<0) throw PlaybackFailure("ECRITURE_$count")
            if(count!=pcm.size) throw PlaybackFailure(if(count==0) "ECRITURE_NULLE" else "ECRITURE_INCOMPLETE")
            if(track.state!=AudioTrack.STATE_INITIALIZED) throw PlaybackFailure("NON_INITIALISE")
        } catch(error: Throwable) { snapshot("échec chargement"); throw error }
    }
    fun drain() {
        checkRunning()
        val track=active.get() ?: throw PlaybackFailure("AUCUN_PCM_CHARGE")
        check(submitted>0)
        val result=manager.requestAudioFocus(focusRequest)
        focus=if(result==AudioManager.AUDIOFOCUS_REQUEST_GRANTED) "accordé" else "refusé ($result)"
        if(result!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { snapshot("focus refusé"); throw PlaybackFailure("FOCUS_REFUSE") }
        try {
            checkRunning(); track.play(); launchNs=System.nanoTime()
            snapshot("play demandé")
            val monitor=PlaybackEvidence(submitted,SystemClock.elapsedRealtime())
            var nextSnapshot=0L
            val timestamp=AudioTimestamp()
            while(true) {
                checkRunning()
                val now=SystemClock.elapsedRealtime()
                val head=Integer.toUnsignedLong(track.playbackHeadPosition)
                val hasTimestamp=track.getTimestamp(timestamp)
                // Only a timestamp already presented, within this track's launch epoch, may prove completion.
                val presented=if(hasTimestamp && timestamp.nanoTime in launchNs..System.nanoTime()) timestamp.framePosition else null
                val phase=monitor.observe(now,track.state==AudioTrack.STATE_INITIALIZED,track.playState==AudioTrack.PLAYSTATE_PLAYING,head,presented)
                if(phase!=PlaybackStage.WAITING && !announcedStart) { announcedStart=true; onStarted() }
                if(now>=nextSnapshot) {
                    snapshot("progression ${monitor.progressedFrames}/$submitted, timestamp ${presented ?: -1}")
                    nextSnapshot=now+100
                }
                if(phase==PlaybackStage.COMPLETED) {
                    token.check(); completed=true; evidence=monitor.evidence; snapshot("terminé"); return
                }
                Thread.sleep(5)
            }
        } catch(error: Throwable) { snapshot(if(token.isCancelled()) "annulé" else "échec drain"); throw error }
    }
    @Synchronized private fun stopTrack() { active.get()?.let { runCatching { it.pause(); it.flush() } } }
    fun stop() { token.cancel(); stopTrack() }
    override fun close() {
        active.getAndSet(null)?.let { runCatching { it.stop() }; it.release() }
        manager.abandonAudioFocusRequest(focusRequest)
    }
}
