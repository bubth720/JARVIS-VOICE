package com.jarvis.voice.identity

import com.jarvis.voice.pocket.*
import java.io.File
import java.io.RandomAccessFile

/** Selected contiguous PCM16/float WAV region only; bounded mono/stereo -> existing 24 kHz resampler. */
object IdentityWav {
    private data class Header(val rate: Int,val channels: Int,val bits: Int,val format: Int,val position: Long,val frames: Long)
    private fun header(f: RandomAccessFile): Header {
        require(f.length() in 44..33554432)
        fun tag()=ByteArray(4).also(f::readFully).toString(Charsets.US_ASCII)
        fun u32()=Integer.toUnsignedLong(Integer.reverseBytes(f.readInt()))
        fun u16()=java.lang.Short.toUnsignedInt(java.lang.Short.reverseBytes(f.readShort()))
        require(tag()=="RIFF");val end=u32()+8;require(end==f.length() && tag()=="WAVE")
        var rate=0;var channels=0;var bits=0;var format=0;var position=-1L;var size=0L
        while(f.filePointer+8<=end) {
            val name=tag();val n=u32();val next=f.filePointer+n+n%2;require(next<=end)
            if(name=="fmt ") {
                require(format==0 && n in 16..65536);format=u16();channels=u16();rate=u32().toInt()
                val bytesPerSecond=u32();val align=u16();bits=u16()
                require(channels in 1..2 && rate in 8000..192000 && (format==1 && bits==16 || format==3 && bits==32))
                require(align==channels*bits/8 && bytesPerSecond==rate.toLong()*align)
            } else if(name=="data") { require(position<0);position=f.filePointer;size=n }
            f.seek(next)
        }
        require(rate>0 && position>=0 && size%(channels*bits/8)==0L)
        val frames=size/(channels*bits/8);ReferenceInfo(frames*1000/rate,rate,channels).validate()
        return Header(rate,channels,bits,format,position,frames)
    }
    fun inspect(file: File): ReferenceInfo=RandomAccessFile(file,"r").use { val h=header(it);ReferenceInfo(h.frames*1000/h.rate,h.rate,h.channels) }
    fun decode(file: File,startMs: Long,durationMs: Long,cancel: CancellationToken): FloatArray=RandomAccessFile(file,"r").use { f ->
        val h=header(f);require(startMs>=0 && durationMs in 5000..15000 && (startMs+durationMs)*h.rate/1000<=h.frames)
        val count=(durationMs*h.rate/1000).toInt();f.seek(h.position+startMs*h.rate/1000*h.channels*h.bits/8)
        val pcm=FloatArray(count) { i ->
            if(i%2048==0) cancel.check();var sum=0f
            repeat(h.channels) { sum+=if(h.bits==16) java.lang.Short.reverseBytes(f.readShort())/32768f else Float.fromBits(Integer.reverseBytes(f.readInt())) }
            (sum/h.channels).also { require(it.isFinite()) }
        }
        ReferenceAudio.resample24k(pcm,h.rate,cancel::isCancelled)
    }
}
