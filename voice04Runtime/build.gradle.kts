plugins { id("org.jetbrains.kotlin.jvm") }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    compileOnly("com.microsoft.onnxruntime:onnxruntime:1.27.0")
    implementation(project(":pocketRuntime"))
    implementation(project(":identityRuntime"))
    implementation(project(":expressionRuntime"))
    compileOnly("org.json:json:20250517")
    testImplementation("org.json:json:20250517")
    testImplementation("com.microsoft.onnxruntime:onnxruntime:1.27.0")
    testImplementation("junit:junit:4.13.2")
}
tasks.test { maxHeapSize="2g"; testLogging { events("passed","skipped","failed") } }
