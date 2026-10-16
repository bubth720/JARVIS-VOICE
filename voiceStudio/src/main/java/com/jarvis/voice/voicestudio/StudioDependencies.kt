package com.jarvis.voice.voicestudio
import android.content.Context
import android.net.Uri
import com.jarvis.voice.identity.*
import com.jarvis.voice.expression.*
import com.jarvis.voice.pocket.*
import com.jarvis.voice.studio.StudioRendering
import java.io.*

/** Dependency boundaries allow instrumentation without putting private model weights in CI. */
interface StudioBackend {
    fun modelAvailable(pack: File): Boolean
    fun encode(vault: VoiceIdentityVault,id: VoiceIdentityId,rid: String,pack: File,cancel: CancellationToken): String
    fun prepare(pack: File,plan: VoiceExpressionPlan): LocalProsodyPlan
    fun render(pack: File,voice: PersonalVoiceProfile,plan: LocalProsodyPlan,cancel: CancellationToken): ExpressionRenderResult
}
object OfficialStudioBackend: StudioBackend {
    override fun modelAvailable(pack: File)=File(pack,"bundle.json").isFile
    override fun encode(vault: VoiceIdentityVault,id: VoiceIdentityId,rid: String,pack: File,cancel: CancellationToken)=IdentityEncoding.official(vault,id,rid,pack,cancel)
    override fun prepare(pack: File,plan: VoiceExpressionPlan)=ExpressionRenderer.prepare(pack,plan)
    override fun render(pack: File,voice: PersonalVoiceProfile,plan: LocalProsodyPlan,cancel: CancellationToken)=ExpressionRenderer.render(pack,voice,plan,cancel)
}
interface StudioDocuments { fun input(uri: Uri): InputStream;fun output(uri: Uri): OutputStream;fun remove(uri: Uri) }
class LocalStudioDocuments(private val context: Context): StudioDocuments {
    override fun input(uri: Uri)=requireNotNull(context.contentResolver.openInputStream(uri))
    override fun output(uri: Uri)=requireNotNull(context.contentResolver.openOutputStream(uri,"w"))
    override fun remove(uri: Uri) { android.provider.DocumentsContract.deleteDocument(context.contentResolver,uri) }
}
