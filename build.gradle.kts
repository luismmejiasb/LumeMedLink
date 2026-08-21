// Critical manifest: not to be rewritten as a side effect of another task.

// Every plugin both modules use is loaded HERE, `apply false`, so they share one classloader.
// Without this, `:composeApp` and `:androidApp` each load the Kotlin plugin in a sibling
// classloader and the shared KotlinNativeBundleBuildService fails task creation outright.
plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.androidKmpLibrary) apply false
    alias(libs.plugins.androidApplication) apply false
}

// -Werror stays on for every REAL compilation — Android, iOS binaries, tests — and comes off only
// for shared-metadata compilations. Those hit a warning that is not ours to fix: Compose
// Multiplatform 1.11 is mid-transition to androidx's own KMP publications, so the JetBrains
// artifacts and the androidx ones they re-export both land in the metadata classpath and the KLIB
// loader reports every module twice. (Inherited from LumeUIComposer's root manifest, where a
// consuming module first made the task run.)
subprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>()
        .matching { it.name.contains("Metadata") }
        .configureEach { compilerOptions.allWarningsAsErrors.set(false) }
}

// §0 "version catalog + lockfile": the catalog pins what we CHOSE; the lockfile pins what actually
// RESOLVED, transitives included, so a hijacked minor of a transitive cannot ride in unnoticed.
// Regenerate deliberately with `./gradlew build --write-locks` when a pin changes.
allprojects {
    dependencyLocking { lockAllConfigurations() }
}
