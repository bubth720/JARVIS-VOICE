plugins { id("org.jetbrains.kotlin.jvm") }
java { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation(project(":voiceBridgeContract")); implementation(project(":studioRuntime"))
    implementation(project(":expressionRuntime")); implementation(project(":identityRuntime")); implementation(project(":pocketRuntime"))
    implementation("org.json:json:20250517"); implementation("com.microsoft.onnxruntime:onnxruntime:1.27.0")
    testImplementation("junit:junit:4.13.2")
}
tasks.test { maxHeapSize="2g"; testLogging { events("passed","skipped","failed") } }
tasks.register<JavaExec>("benchmark") { classpath=sourceSets["main"].runtimeClasspath;mainClass.set("com.jarvis.voice.bridge.harness.BridgeBenchmarkKt"); maxHeapSize="2g" }
