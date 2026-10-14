plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.jarvis.voice.voiceprototype"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.jarvis.voice.voiceprototype005e"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.1.1-mission005e-import-diagnostics"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += providers.gradleProperty("prototypeTestAbi").getOrElse("arm64-v8a") }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging { jniLibs.useLegacyPackaging = false }
}
dependencies {
    implementation(project(":pocketRuntime"))
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.27.0")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
