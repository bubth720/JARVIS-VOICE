plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.jarvis.voice.voicestudio"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.jarvis.voice.voicestudio005j"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.6.0-mission005j-experimental"
        testInstrumentationRunner = "com.jarvis.voice.voicestudio.StudioTestRunner"
        ndk { abiFilters += providers.gradleProperty("prototypeTestAbi").getOrElse("arm64-v8a") }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    sourceSets.getByName("androidTest").assets.srcDir("build/generated/androidTestAssets")
    packaging {
        jniLibs.useLegacyPackaging = false
        // Kotlin coroutine debugger probe, not used by this application. Keep the
        // strict delivery audit that rejects every packaged binary/model asset.
        resources.excludes += "DebugProbesKt.bin"
    }
    // Private local key only. Public CI uses ephemeral debug keys for instrumentation, not stable delivery.
    val privateKey=providers.environmentVariable("JARVIS_PROTOTYPE_KEYSTORE").orNull
    if(privateKey!=null) {
        val passwordFile=providers.environmentVariable("JARVIS_PROTOTYPE_PASSWORD_FILE").get()
        val stable=signingConfigs.create("privateStable") {
            storeFile=file(privateKey)
            storePassword=file(passwordFile).readText().trim()
            keyAlias="jarvis-prototype"
            keyPassword=storePassword
        }
        buildTypes.getByName("debug").signingConfig=stable
    }
}
dependencies {
    implementation(project(":pocketRuntime"))
    implementation(project(":identityRuntime"))
    implementation(project(":expressionRuntime"))
    implementation(project(":studioRuntime"))
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.27.0")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
