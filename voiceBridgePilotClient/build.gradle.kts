import java.security.MessageDigest
plugins { id("com.android.application");id("org.jetbrains.kotlin.android") }
android {
    namespace="com.jarvis.core.voicepilot.fixture"
    compileSdk=35
    defaultConfig { applicationId="com.jarvis.core.voicepilot.fixture";minSdk=26;targetSdk=35;versionCode=1;versionName="0.1-fixture-only" }
    buildFeatures { aidl=true;buildConfig=true }
    defaultConfig {
        testInstrumentationRunner="androidx.test.runner.AndroidJUnitRunner"
        val digest=MessageDigest.getInstance("SHA-256")
        for((name,path) in sortedMapOf("CONTRACT.md" to "voiceBridgePilot/contract/CONTRACT.md","IVoicePilotFixture.aidl" to "voiceBridgePilot/src/main/aidl/com/jarvis/voice/bridgepilot/ipc/IVoicePilotFixture.aidl")) {
            digest.update(name.toByteArray());digest.update(0.toByte());digest.update(rootProject.file(path).readBytes());digest.update(0.toByte())
        }
        buildConfigField("String","CONTRACT_SHA","\""+digest.digest().joinToString(""){"%02x".format(it.toInt() and 255)}+"\"")
    }
    sourceSets["main"].aidl.srcDir(rootProject.file("voiceBridgePilot/src/main/aidl"))
    compileOptions { sourceCompatibility=JavaVersion.VERSION_17;targetCompatibility=JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget="17" }
    val key=providers.environmentVariable("VOICE_PILOT_KEYSTORE").orNull
    val wrong=providers.environmentVariable("VOICE_PILOT_REFUSED_KEYSTORE").orNull
    signingConfigs {
        if(key!=null) create("pilot") { storeFile=file(key);storePassword=providers.environmentVariable("VOICE_PILOT_KEY_PASSWORD").orNull;keyAlias="voice-pilot";keyPassword=storePassword }
        if(wrong!=null) create("refused") { storeFile=file(wrong);storePassword=providers.environmentVariable("VOICE_PILOT_KEY_PASSWORD").orNull;keyAlias="voice-pilot";keyPassword=storePassword }
    }
    buildTypes.getByName("debug") { signingConfig=null }
    flavorDimensions+="identity"
    productFlavors {
        create("authorized") { dimension="identity";signingConfig=if(key!=null) signingConfigs.getByName("pilot") else null }
        create("wrongPackage") { dimension="identity";applicationId="com.jarvis.core.voicepilot.unknown";signingConfig=if(key!=null) signingConfigs.getByName("pilot") else null }
        create("wrongSignature") { dimension="identity";applicationId="com.jarvis.core.voicepilot.untrusted";signingConfig=if(wrong!=null) signingConfigs.getByName("refused") else null }
    }
}

dependencies {
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
