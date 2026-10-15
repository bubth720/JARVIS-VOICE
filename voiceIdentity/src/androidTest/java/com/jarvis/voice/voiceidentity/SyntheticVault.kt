package com.jarvis.voice.voiceidentity

import com.jarvis.voice.identity.*

import com.jarvis.voice.pocket.*
import java.io.File
import java.nio.*

internal object SyntheticVault {
    fun pcm(frequency: Double=440.0)=FloatArray(120000) { (kotlin.math.sin(it*2*kotlin.math.PI*frequency/24000)*.1).toFloat() }
    fun profile(value: Float=0f): PersonalVoiceProfile {
        val tensors=linkedMapOf<String,VoiceTensor>()
        for(i in 0..5) {
            val prefix="transformer.layers.$i.self_attn/"
            for((key,v) in listOf("offset" to 64L,"pad" to 0L)) tensors[prefix+key]=VoiceTensor(1,longArrayOf(1),ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(v).array())
            val bytes=ByteBuffer.allocate(2*64*16*64*4).order(ByteOrder.LITTLE_ENDIAN);repeat(bytes.capacity()/4) { bytes.putFloat(value) }
            tensors[prefix+"cache"]=VoiceTensor(0,longArrayOf(2,1,64,16,64),bytes.array())
        }
        return PersonalVoiceProfile(PocketIdentity.packId,tensors)
    }
    fun reference(v: VoiceIdentityVault,id: VoiceIdentityId,keep: Boolean=true,label: ReferenceLabel=ReferenceLabel.NEUTRE)=
        v.addReference(id,pcm(),"WAV","0".repeat(64),0,label,"signal synthétique","test synthétique, aucun locuteur",true,true,keep,CancellationToken())
    fun identity(v: VoiceIdentityVault,name: String="Identité synthétique")=v.create(name,"aucune voix réelle, test synthétique",true)
    fun reject(block: ()->Unit) { try { block();org.junit.Assert.fail("Expected refusal") } catch(t: Exception) { if(t is org.junit.internal.AssumptionViolatedException) throw t } }
}
