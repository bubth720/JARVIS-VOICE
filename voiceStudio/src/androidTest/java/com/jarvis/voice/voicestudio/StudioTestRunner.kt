package com.jarvis.voice.voicestudio
import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.test.runner.AndroidJUnitRunner
import androidx.test.platform.app.InstrumentationRegistry
import com.jarvis.voice.identity.*
import com.jarvis.voice.expression.*
import com.jarvis.voice.pocket.*
import java.io.*
import java.util.concurrent.atomic.AtomicInteger

/** Test APK only. No fixture backend/class/weights are packaged in the delivered APK. */
class StudioTestRunner: AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader,name: String,context: Context): Application = super.newApplication(cl,StudioTestApplication::class.java.name,context)
}
class StudioTestApplication: StudioApplication() {
    val fixture=StudioFixtureBackend()
    override fun createController()=StudioController(this,fixture,FixtureDocuments())
}
class FixtureDocuments: StudioDocuments {
    override fun input(uri: Uri): InputStream=InstrumentationRegistry.getInstrumentation().context.assets.open(requireNotNull(uri.lastPathSegment).substringAfterLast('/'))
    override fun output(uri: Uri): OutputStream=ByteArrayOutputStream()
    override fun remove(uri: Uri) {}
}
class StudioFixtureBackend: StudioBackend {
    @Volatile var available=false
    @Volatile var hold=false
    val calls=AtomicInteger()
    override fun modelAvailable(pack: File)=available
    override fun encode(vault: VoiceIdentityVault,id: VoiceIdentityId,rid: String,pack: File,cancel: CancellationToken)=
        IdentityEncoding.create(vault,id,rid,cancel) { _,c->c.check();SyntheticVault.profile((calls.incrementAndGet()/10f)) }
    override fun prepare(pack: File,plan: VoiceExpressionPlan)=LocalProsodyPlanner.prepareWith(plan) { PreparedText(it,LongArray(it.split(' ').size),3) }
    override fun render(pack: File,voice: PersonalVoiceProfile,plan: LocalProsodyPlan,cancel: CancellationToken): ExpressionRenderResult {
        calls.incrementAndGet();while(hold) { cancel.check();Thread.sleep(10) };cancel.check()
        return ExpressionRenderer.renderWith(plan,cancel) { _,_,sink ->
            sink(FloatArray(12000) { (kotlin.math.sin(it*2*kotlin.math.PI*440/24000)*.05).toFloat() })
            GenerationMetrics(1.0,1.0,1.0,.5,6,3,true)
        }
    }
}
