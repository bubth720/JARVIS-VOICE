plugins { id("org.jetbrains.kotlin.jvm") }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation(project(":pocketRuntime"))
    implementation(project(":identityRuntime"))
    implementation(project(":expressionRuntime"))
    testImplementation("com.microsoft.onnxruntime:onnxruntime:1.27.0")
    testImplementation("org.json:json:20250517")
    testImplementation("junit:junit:4.13.2")
}
tasks.test { testLogging { events("passed", "skipped", "failed") } }
tasks.register<JavaExec>("demo") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.jarvis.voice.sdk.SdkDemoKt")
}
