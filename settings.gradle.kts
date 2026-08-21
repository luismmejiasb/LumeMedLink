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

// The PLUGIN classpath, locked. It was the highest-privilege surface in the build and had no
// control at all: a Gradle plugin runs arbitrary code with the developer's privileges and can
// rewrite the output APK, yet `allprojects { dependencyLocking { … } }` covers project
// configurations only — the plugin classpath appeared in no lockfile, so
// Scripts/check-dependency-allowlist.sh was structurally unable to see any of it (F20, ADR-0018).
//
// It must sit AFTER pluginManagement: Gradle rejects a `buildscript` block before it.
buildscript {
    configurations.classpath {
        resolutionStrategy.activateDependencyLocking()
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
