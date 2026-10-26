pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "JARVIS VOICE"
include(":app")
include(":pocketRuntime", ":pocketProbe")
include(":voicePrototype")

include(":voiceQuality")
include(":identityRuntime", ":voiceIdentity")

include(":expressionRuntime")
include(":voiceExpression")

include(":studioRuntime", ":voiceStudio")
include(":voiceSdk")

include(":voiceBridgeContract", ":voiceBridgeHarness")

// Isolated fixture only; historical applications do not depend on the pilot.
if (providers.gradleProperty("includeVoicePilot").orNull == "true") {
    include(":voiceBridgePilot", ":voiceBridgePilotClient")
}

// VOICE-04 is isolated; historical application IDs stay unchanged.
include(":voice04Runtime", ":voice04Studio")

include(":voiceFinalSdk", ":voiceFinalRuntime", ":voiceFinalStudio")

include(":voiceFusion02")
