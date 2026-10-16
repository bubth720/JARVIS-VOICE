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
