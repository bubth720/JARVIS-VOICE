package com.jarvis.voice.studio
import com.jarvis.voice.identity.*
import com.jarvis.voice.expression.*
import com.jarvis.voice.pocket.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.nio.file.Files

class StudioParityTests {
    @Test fun officialPublicProfileKeepsExact005iPcmWithAndWithoutPausesAndIntents() {
        val pack=System.getenv("POCKET_EXPERIMENTAL_MODEL_DIR").orEmpty();val audio=System.getenv("IDENTITY_PUBLIC_ALBA_WAV").orEmpty()
        assumeTrue("Existing private pack and licensed public Alba only",pack.isNotBlank() && audio.isNotBlank())
        val root=Files.createTempDirectory("studio-public-parity-").toFile();val c=CancellationToken()
        try {
            val v=VoiceIdentityVault(File(root,"v"));val source=File(audio)
            assertEquals("46264e83cb99115c3d210260e029117566d9c64f20266d10daa78107759ede3e",VoiceIdentityVault.sha(source))
            val id=v.create("Alba publique CC BY 4.0","licensed public fixture",true)
            val rid=v.addReference(id,IdentityWav.decode(source,0,5000,c),"WAV",VoiceIdentityVault.sha(source),0,ReferenceLabel.NEUTRE,"public test","licensed public",true,true,false,c)
            val pid=IdentityEncoding.official(v,id,rid,File(pack),c);val profile=v.list().single().profiles.single();val voice=v.binding().resolve(id,pid,c)
            val cases=listOf(StudioState(text="Bonjour à tous.",settings=StudioSettings(seed=7)),StudioState(text="Demain, je prendrai le train pour Paris.",settings=StudioSettings(steps=3,intent=VoiceExpressionIntent.CONVERSATION)),StudioState(text="Bonjour à tous.\nNous sommes prêts.",settings=StudioSettings(steps=3,pauses=true,pauseMs=250)),StudioState(text="Bonjour à tous.\nNous sommes prêts.\nLe train arrive.",settings=StudioSettings(steps=3,pauses=true,pauseMs=500)))
            val rows=JSONArray()
            for(state in cases) {
                val req=CoreVoiceRequestV1(state.text,id,expressionIntent=state.settings.intent.name,pauseMs=if(state.settings.pauses) state.settings.pauseMs else 0,allowSentencePauses=state.settings.pauses)
                val referencePlan=ExpressionPolicy.plan(req,ExpressionVoiceBinding.from(profile),state.settings.sampling(),state.settings.seed)
                val reference=ExpressionRenderer.render(File(pack),voice,ExpressionRenderer.prepare(File(pack),referencePlan),c)
                val plan=StudioRendering.plan(state,profile);assertEquals(referencePlan,plan)
                val actual=StudioRendering.render(File(pack),voice,plan,c);assertArrayEquals(reference.pcm,actual.pcm,0f)
                rows.put(JSONObject().put("text",state.text).put("steps",state.settings.steps).put("seed",state.settings.seed).put("pauseMs",if(state.settings.pauses) state.settings.pauseMs else 0).put("intent",state.settings.intent.name).put("bitIdenticalTo005i",true).put("maxAbsError",0).put("samples",actual.pcm.size).put("stats",PcmStatistics.measure(actual.pcm).summary()).put("referenceWallMs",reference.wallMs).put("studioWallMs",actual.wallMs))
            }
            val result=JSONObject().put("publicReference","Alba CC BY 4.0, pinned SHA256").put("personalDataUsed",false).put("modelRebuilt",false).put("physicalXiaomiTest",false).put("results",rows)
            System.getenv("STUDIO_VALIDATION_OUT")?.let { File(it).apply { parentFile.mkdirs();writeText(result.toString(2)+"\n") } }
        } finally { root.deleteRecursively() }
    }
}
