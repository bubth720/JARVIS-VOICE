plugins { id("org.jetbrains.kotlin.jvm") }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation(project(":voiceFinalSdk"))
    compileOnly("com.microsoft.onnxruntime:onnxruntime:1.27.0")
    implementation(project(":pocketRuntime"))
    implementation(project(":voice04Runtime"))
    implementation(project(":identityRuntime"))
    implementation(project(":expressionRuntime"))
    compileOnly("org.json:json:20250517")
    testImplementation("org.json:json:20250517")
    testImplementation("com.microsoft.onnxruntime:onnxruntime:1.27.0")
    testImplementation("junit:junit:4.13.2")
}
tasks.test { maxHeapSize="2g"; testLogging { events("passed","skipped","failed") } }

// Instrument a generated copy, never edit the qualified engine or its arithmetic.
val observed by tasks.registering {
    val source=rootProject.file("voice04Runtime/src/main/kotlin/com/jarvis/voice/speed/ObservedQualityEngine.kt")
    inputs.file(source)
    val target=layout.buildDirectory.file("generated/observed/FinalObservedQualityEngine.kt")
    outputs.file(target)
    doLast {
        var text=source.readText()
        fun replaceOnce(old: String,new: String) {
            check(text.windowed(old.length).count { it==old }==1) { "Instrumented source changed" }
            text=text.replace(old,new)
        }
        replaceOnce("package com.jarvis.voice.speed", "package com.jarvis.voice.final01\nimport com.jarvis.voice.speed.*")
        replaceOnce("class ObservedQualityEngine(","class FinalObservedQualityEngine(")
        replaceOnce("private val trace: SpeedTrace = SpeedTrace())", "private val trace: SpeedTrace = SpeedTrace(), private val profiler: StageProfiler = StageProfiler(), private val frontendOverride: FrenchFrontend? = null)")
        replaceOnce("FrenchFrontend(JSONObject(File(root, \"tokenizer.json\").readText()))", "frontendOverride ?: FrenchFrontend(JSONObject(File(root, \"tokenizer.json\").readText()))")
        replaceOnce("val prepared = frontend.prepare(raw)","val prepared = profiler.timed(Stage.TOKENIZATION) { frontend.prepare(raw) }")
        replaceOnce("text.run(mapOf(\"token_ids\" to ids), options).use", "profiler.timed(Stage.TEXT_ENCODING) { text.run(mapOf(\"token_ids\" to ids), options) }.use")
        replaceOnce("maxOnnxNs = maxOf(maxOnnxNs, elapsed)", "profiler.add(when(stage) { 0 -> Stage.FLOW_LM; 1 -> Stage.FLOW_SAMPLER; else -> Stage.PCM_DECODER }, elapsed / 1e6)\n                maxOnnxNs = maxOf(maxOnnxNs, elapsed)")
        target.get().asFile.apply { parentFile.mkdirs();writeText(text) }
    }
}
kotlin.sourceSets.main { kotlin.srcDir(layout.buildDirectory.dir("generated/observed")) }
tasks.named("compileKotlin") { dependsOn(observed) }
