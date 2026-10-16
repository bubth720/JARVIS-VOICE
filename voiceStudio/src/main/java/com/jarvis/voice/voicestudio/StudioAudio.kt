package com.jarvis.voice.voicestudio
import android.media.MediaExtractor
import android.media.MediaFormat
import com.jarvis.voice.identity.IdentityWav
import com.jarvis.voice.pocket.CancellationToken
import com.jarvis.voice.studio.WizardPolicy
import java.io.*

/** Early metadata only for short-file feedback; authoritative decoder is unchanged after duration check. */
object StudioAudio {
    data class Source(val durationMs: Long,val format: String)
    fun inspect(file: File): Source {
        val wav=file.inputStream().use { input -> ByteArray(4).also { DataInputStream(input).readFully(it) }.toString(Charsets.US_ASCII)=="RIFF" }
        val ms=if(wav) RandomAccessFile(file,"r").use { f ->
            fun tag()=ByteArray(4).also(f::readFully).toString(Charsets.US_ASCII)
            fun u32()=Integer.toUnsignedLong(Integer.reverseBytes(f.readInt()))
            require(tag()=="RIFF");val end=u32()+8;require(end==f.length() && tag()=="WAVE")
            var byteRate=0L;var dataBytes=-1L
            while(f.filePointer+8<=end) { val name=tag();val count=u32();val next=f.filePointer+count+count%2;require(next<=end)
                if(name=="fmt ") { require(count>=16);f.skipBytes(8);byteRate=u32() }
                if(name=="data") dataBytes=count
                f.seek(next)
            }
            require(byteRate>0 && dataBytes>=0);dataBytes*1000/byteRate
        } else {
            val ex=MediaExtractor()
            try { ex.setDataSource(file.absolutePath);val index=(0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)=="audio/mpeg" } ?: error("Choisir un MP3 ou WAV valide.")
                ex.getTrackFormat(index).getLong(MediaFormat.KEY_DURATION)/1000
            } finally { ex.release() }
        }
        WizardPolicy.requireSource(ms)
        val authoritative=if(wav) IdentityWav.inspect(file) else LocalMp3.inspect(file)
        require(authoritative.durationMs==ms)
        return Source(ms,if(wav) "WAV" else "MP3")
    }
    fun decode(file: File,format: String,startMs: Long,durationMs: Long,cancel: CancellationToken)=
        if(format=="WAV") IdentityWav.decode(file,startMs,durationMs,cancel) else LocalMp3.decode(file,startMs,durationMs,cancel)
    fun copy(input: InputStream,out: File,cancel: CancellationToken) {
        out.outputStream().use { sink -> val bytes=ByteArray(65536);var total=0L
            while(true) { cancel.check();val n=input.read(bytes);if(n<0) break;total+=n;require(total<=33554432) { "Choisir un fichier de 32 Mio maximum." };sink.write(bytes,0,n) };sink.fd.sync()
        }
    }
}
