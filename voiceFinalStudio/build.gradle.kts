plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
val sharedStudio by tasks.registering(Sync::class) {
    from("../voiceStudio/src/main/java") {
        exclude("**/StudioController.kt", "**/AdvancedScreen.kt", "**/StudioActivity.kt", "**/PcmPlayer.kt", "**/OperationMetrics.kt")
    }
    // Branding only; source files and all archive/profile operations remain unchanged.
    filter { line: String -> line.replace("005J", "FINAL-01") }
    into(layout.buildDirectory.dir("generated/sharedStudio"))
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(sharedStudio) }
android {
    namespace = "com.jarvis.voice.voicestudio"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.jarvis.voice.studio.final01"
        manifestPlaceholders["voiceLabLabel"]="Athéna · VOICE FINAL-01 expérimental"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-final01-experimental"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += providers.gradleProperty("prototypeTestAbi").getOrElse("arm64-v8a") }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    sourceSets.getByName("main") {
        java.srcDir(layout.buildDirectory.dir("generated/sharedStudio"))
        java.srcDir(layout.buildDirectory.dir("generated/finalStudio"))
        res.srcDir("../voiceStudio/src/main/res")
        assets.srcDir("../voiceStudio/src/main/assets")
    }
    packaging {
        jniLibs.useLegacyPackaging = false
        // Kotlin coroutine debugger probe, not used by this application. Keep the
        // strict delivery audit that rejects every packaged binary/model asset.
        resources.excludes += "DebugProbesKt.bin"
    }

}
dependencies {
    implementation(project(":voiceFinalSdk"))
    implementation(project(":pocketRuntime"))
    implementation(project(":identityRuntime"))
    implementation(project(":expressionRuntime"))
    implementation(project(":studioRuntime"))
    implementation(project(":voiceSdk"))
    implementation(project(":voice04Runtime"))
    implementation(project(":voiceFinalRuntime"))
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.27.0")
    androidTestImplementation(project(":voiceFusion02"))
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}

val finalStudio by tasks.registering(Sync::class) {
    from("../voice04Studio/src/main/java")
    filter { line: String ->
        when {
            line=="    private fun admission() {" || line.startsWith("    private fun play(") || line.startsWith("    private fun playPrefix(") -> line+"\n        check(!FinalSafety.quarantined) { \"UNKNOWN_NATIVE_FINAL01\" }"
            line.contains("fun removeProfile(") || line.contains("fun clearAudio(") || line.contains("fun removeIdentity(") || line.contains("library.activate(id,pid,c,neutral);") -> line.replace("audition=null;","FinalAudioCleanup.clear();audition=null;")
            else -> line
        }
    }
    into(layout.buildDirectory.dir("generated/finalStudio"))
}
tasks.named("preBuild") { dependsOn(finalStudio) }
