package com.jarvis.voice.voicestudio
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jarvis.voice.pocket.*
import com.jarvis.voice.speed.*
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.Assert.*
import org.junit.runner.RunWith
import android.content.Intent
@RunWith(AndroidJUnit4::class)
class Voice05AudioTests {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private var activity: StudioActivity?=null
    @Before fun foreground() { val i=InstrumentationRegistry.getInstrumentation();activity=i.startActivitySync(Intent(context,StudioActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as StudioActivity;i.waitForIdleSync() }
    @After fun closeActivity() { InstrumentationRegistry.getInstrumentation().runOnMainSync { activity?.finish() } }
    private fun signal(n: Int)=FloatArray(n) { (kotlin.math.sin(it*2*kotlin.math.PI*440/24000)*.05).toFloat() }
    @Test fun pooledPrefixNeverPresentsTheUnusedTwentySecondTail() {
        val c=CancellationToken();val trace=SpeedTrace();val data=FloatArray(480000) { Float.NaN }
        signal(12000).copyInto(data)
        PcmPlayer(context,c,speed=trace).use { p -> p.write(data,12000);p.drain();assertTrue(p.completed);assertEquals(12000L,p.submitted) }
        assertTrue(trace.has(SpeedPoint.PLAYBACK_COMPLETED))
    }
    @Test fun poolCanServeTwoActualDeferredJobsWithoutNewLargeArrays() {
        PcmBufferPool().use { pool -> repeat(2) {
            val c=CancellationToken()
            PooledPcmCapture(pool).use { capture ->
                capture.append(signal(12000));capture.present(true) { data,count -> PcmPlayer(context,c).use { p -> p.write(data,count);p.drain();assertTrue(p.completed) } }
            }
        };assertEquals(1,pool.arrayCreations) }
    }

}
