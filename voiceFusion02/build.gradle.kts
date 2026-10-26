plugins { id("org.jetbrains.kotlin.jvm"); application }
java { sourceCompatibility=JavaVersion.VERSION_17;targetCompatibility=JavaVersion.VERSION_17 }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation(project(":voiceFinalSdk"))
    testImplementation(project(":voiceFinalRuntime"))
    testImplementation(project(":voice04Runtime"))
    testImplementation(project(":pocketRuntime"))
    testImplementation(project(":identityRuntime"))
    testImplementation("org.json:json:20250517")
    testImplementation("com.microsoft.onnxruntime:onnxruntime:1.27.0")
    testImplementation("junit:junit:4.13.2")
}
application { mainClass.set("com.jarvis.voice.fusion02.ProxyDemoKt") }
tasks.test { maxHeapSize="2g";testLogging { events("passed","skipped","failed") } }
