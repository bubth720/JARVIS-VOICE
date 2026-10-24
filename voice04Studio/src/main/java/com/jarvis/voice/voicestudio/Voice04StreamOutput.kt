package com.jarvis.voice.voicestudio
import android.content.Context
import android.media.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.jarvis.voice.pocket.*
import com.jarvis.voice.speed.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

/** MODE_STREAM only for prevalidated experimental jobs. No sample edits beyond historical PCM16 conversion. */
class Voice04StreamOutput(context: Context,private val cancel: CancellationToken,private val trace: SpeedTrace,
    private val diagnostic: (String)->Unit={}) : ProgressiveOutput {
    private val manager=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val attributes=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val active=AtomicReference<AudioTrack?>()
    @Volatile private var focusLost=false
    private var playing=false;private var submitted=0L;private var writes=0;private var zeros=0
    private var baselineUnderruns=0;private var head=0L;private var lastProgress=0L
    private val focus=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener({ change ->
            if(change==AudioManager.AUDIOFOCUS_LOSS || change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT || change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                focusLost=true;cancel.cancel();stop()
            }
        },Handler(Looper.getMainLooper())).build()
    private fun checkLive() { if(focusLost) throw PlaybackFailure("FOCUS_PERDU");cancel.check() }
    private fun snapshot(phase: String) {
        val t=active.get();trace.audio(submitted,head,writes,zeros,t?.underrunCount ?: 0);diagnostic("STREAM $phase · état ${t?.state} · playState ${t?.playState} · fréquence ${t?.sampleRate} · volume piste 1, média ${manager.getStreamVolume(AudioManager.STREAM_MUSIC)} · muet ${manager.isStreamMute(AudioManager.STREAM_MUSIC)} · route type ${t?.routedDevice?.type ?: -1} · écritures $writes/nulles $zeros · submitted $submitted/head $head · underruns ${t?.underrunCount ?: 0}")
    }
    override fun start(preload: FloatArray) {
        checkLive();if(Build.VERSION.SDK_INT<31) throw PlaybackFailure("STREAM_API_NON_QUALIFIEE");require(preload.size in 24000..48000 && preload.all { it.isFinite() })
        val min=AudioTrack.getMinBufferSize(24000,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT)
        if(min<=0 || min>preload.size*2) throw PlaybackFailure("PRELECTURE_INSUFFISANTE")
        val t=AudioTrack.Builder().setAudioAttributes(attributes)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(24000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(preload.size*2).build()
        active.set(t);snapshot("créé")
        if(t.state!=AudioTrack.STATE_INITIALIZED || t.bufferSizeInFrames>preload.size) throw PlaybackFailure("BUFFER_INCOMPATIBLE")
        if(t.setVolume(1f)!=AudioTrack.SUCCESS) throw PlaybackFailure("VOLUME_REFUSE")
        if(Build.VERSION.SDK_INT>=31 && t.setStartThresholdInFrames(preload.size)>preload.size) throw PlaybackFailure("SEUIL_DEMARRAGE")
        if(manager.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED) throw PlaybackFailure("FOCUS_REFUSE")
        baselineUnderruns=t.underrunCount
        write(preload);checkLive();t.play();playing=true;lastProgress=SystemClock.elapsedRealtime();snapshot("play demandé")
    }
    override fun write(pcm: FloatArray) {
        checkLive();require(pcm.isNotEmpty() && pcm.all { it.isFinite() } && submitted+pcm.size<=480000)
        val converted=ShortArray(pcm.size) { (pcm[it].coerceIn(-1f,1f)*32767).roundToInt().toShort() }
        try {
            val t=requireNotNull(active.get());var at=0;var lastWrite=SystemClock.elapsedRealtime()
            while(at<converted.size) {
                checkLive();if(playing) health()
                val n=t.write(converted,at,converted.size-at,AudioTrack.WRITE_NON_BLOCKING);writes++
                if(n<0) throw PlaybackFailure("ECRITURE_$n")
                if(n==0) {
                    zeros++;if(SystemClock.elapsedRealtime()-lastWrite>=2000) throw PlaybackFailure("ECRITURE_BLOQUEE")
                    Thread.sleep(5)
                } else { at+=n;submitted+=n;lastWrite=SystemClock.elapsedRealtime();trace.first(SpeedPoint.FIRST_WRITE) }
            }
        } finally { converted.fill(0) }
    }
    override fun health() {
        checkLive();val t=requireNotNull(active.get())
        if(t.state!=AudioTrack.STATE_INITIALIZED || playing && t.playState!=AudioTrack.PLAYSTATE_PLAYING) throw PlaybackFailure("ETAT_STREAM_INATTENDU")
        val h=Integer.toUnsignedLong(t.playbackHeadPosition);val now=SystemClock.elapsedRealtime()
        if(h<head || h>submitted) throw PlaybackFailure("COMPTEUR_INCOHERENT")
        if(h>head) { head=h;lastProgress=now;trace.first(SpeedPoint.PRESENTATION_ESTIMATED) }
        if(playing && now-lastProgress>=2000) throw PlaybackFailure("COMPTEUR_STAGNANT")
        if(t.underrunCount>baselineUnderruns && h<submitted) throw PlaybackFailure("UNDERRUN_STREAM")
        snapshot("progression")
    }
    override fun finish(totalSamples: Int) {
        checkLive();check(totalSamples.toLong()==submitted && playing)
        val monitor=PlaybackEvidence(submitted,SystemClock.elapsedRealtime())
        val t=requireNotNull(active.get())
        while(true) {
            checkLive()
            // health() owns the previous head and lastProgress. Do not overwrite them before comparison.
            val observedHead=Integer.toUnsignedLong(t.playbackHeadPosition)
            val phase=monitor.observe(SystemClock.elapsedRealtime(),t.state==AudioTrack.STATE_INITIALIZED,t.playState==AudioTrack.PLAYSTATE_PLAYING,observedHead)
            if(phase!=PlaybackStage.WAITING) trace.first(SpeedPoint.PRESENTATION_ESTIMATED)
            if(phase==PlaybackStage.COMPLETED) { head=observedHead;trace.first(SpeedPoint.PLAYBACK_COMPLETED);snapshot("terminé");return }
            health();Thread.sleep(5)
        }
    }
    override fun stop() { active.get()?.let { runCatching { it.pause();it.flush() } } }
    override fun close() {
        try { active.getAndSet(null)?.let { runCatching { it.stop() };it.release() } }
        finally { manager.abandonAudioFocusRequest(focus) }
    }
}
