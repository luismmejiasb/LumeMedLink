// Critical manifest: not to be rewritten as a side effect of another task.

rootProject.name = "LumeMedLink"

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

// The KMP module: all product code lives here, in commonMain's ADR-0008 tree.
include(":composeApp")

// The runnable Android shell. A plain Android app, deliberately NOT a KMP module: since AGP 9.0
// the application plugin refuses to load alongside Kotlin Multiplatform, so the shell depends on
// `:composeApp` instead of being it.
include(":androidApp")

// No empty modules: the tree grows with the slices.
