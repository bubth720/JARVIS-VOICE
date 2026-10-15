package com.jarvis.voice.identity

import com.jarvis.voice.pocket.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.*
import java.nio.file.Files

class IdentityWavTests {
    @Test fun stereo48kSelectedRegionBecomesMono24kAndInvalidSegmentsFail() {
        val root=Files.createTempDirectory("identity-wav-").toFile()
        try {
            val file=File(root,"synthetic.wav");val rate=48000;val samples=rate*6
            val bytes=ByteBuffer.allocate(44+samples*4).order(ByteOrder.LITTLE_ENDIAN)
            bytes.put("RIFF".toByteArray()).putInt(bytes.capacity()-8).put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(2)
                .putInt(rate).putInt(rate*4).putShort(4).putShort(16).put("data".toByteArray()).putInt(samples*4)
            repeat(samples) { val value=(kotlin.math.sin(it*2*kotlin.math.PI*440/rate)*4000).toInt().toShort();bytes.putShort(value).putShort(value) };file.writeBytes(bytes.array())
            assertEquals(2,IdentityWav.inspect(file).channels);val pcm=IdentityWav.decode(file,1000,5000,CancellationToken());assertEquals(120000,pcm.size)
            assertTrue(pcm.all { it.isFinite() });assertTrue(ReferenceSelection.assess(pcm).peak>.1)
            SyntheticVault.reject { IdentityWav.decode(file,2000,5000,CancellationToken()) }
            SyntheticVault.reject { IdentityWav.decode(file,0,4000,CancellationToken()) }
            file.appendBytes(byteArrayOf(1));SyntheticVault.reject { IdentityWav.inspect(file) }
        } finally { root.deleteRecursively() }
    }
}
