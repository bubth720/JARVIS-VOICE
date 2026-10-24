import java.security.MessageDigest
plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace="com.jarvis.voice.bridgepilot"
    compileSdk=35
    defaultConfig {
        applicationId="com.jarvis.voice.bridgepilot";minSdk=26;targetSdk=35;versionCode=1;versionName="0.1-v1-fixture-only"
        testInstrumentationRunner="androidx.test.runner.AndroidJUnitRunner"
        val digest=MessageDigest.getInstance("SHA-256")
        for((name,path) in sortedMapOf("CONTRACT.md" to "contract/CONTRACT.md","IVoicePilotFixture.aidl" to "src/main/aidl/com/jarvis/voice/bridgepilot/ipc/IVoicePilotFixture.aidl")) {
            digest.update(name.toByteArray());digest.update(0.toByte());digest.update(file(path).readBytes());digest.update(0.toByte())
        }
        buildConfigField("String","CONTRACT_SHA","\""+digest.digest().joinToString(""){"%02x".format(it.toInt() and 255)}+"\"")
    }
    buildFeatures { aidl=true;buildConfig=true }
    compileOptions { sourceCompatibility=JavaVersion.VERSION_17;targetCompatibility=JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget="17" }
    testOptions {
        unitTests.isIncludeAndroidResources=true
        unitTests.all {
            providers.gradleProperty("pilotRobolectricDir").orNull?.let { directory ->
                // Cloud fallback: public SDK fixture jars, no writes to a fixed HOME cache.
                it.systemProperty("robolectric.offline","true")
                it.systemProperty("robolectric.dependency.dir",directory)
            }
        }
    }
    val key=providers.environmentVariable("VOICE_PILOT_KEYSTORE").orNull
    signingConfigs { if(key!=null) create("pilot") { storeFile=file(key);storePassword=providers.environmentVariable("VOICE_PILOT_KEY_PASSWORD").orNull;keyAlias="voice-pilot";keyPassword=storePassword } }
    buildTypes.getByName("debug") { signingConfig=if(key!=null) signingConfigs.getByName("pilot") else null }
}
dependencies {
    implementation(project(":voiceBridgeClient"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation(project(":voiceBridgeContract"))
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
