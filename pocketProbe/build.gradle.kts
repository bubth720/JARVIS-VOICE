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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = 3
        versionName = "0.3.0-reference"
        ndk { abiFilters += "arm64-v8a" }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    sourceSets.getByName("androidTest").assets.srcDir("build/generated/androidTestAssets")
    packaging { jniLibs.useLegacyPackaging = false }
}
dependencies {
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    implementation(project(":pocketRuntime"))
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.27.0")
}
