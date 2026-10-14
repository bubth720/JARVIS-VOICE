plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.jarvis.voice.pocketprobe"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.jarvis.voice.pocketprobe"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-probe"
        ndk { abiFilters += "arm64-v8a" }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { jniLibs.useLegacyPackaging = false }
}
dependencies {
    implementation(project(":pocketRuntime"))
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.27.0")
}
