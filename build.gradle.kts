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
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

// Both lint plugins are applied to SUBPROJECTS, not just the root: applied at the root only, they
// analyse the root's (permanently empty) source sets while every line of Kotlin lives in the
// modules — a green task that read nothing. (Lesson inherited from LumeUIComposer's root manifest.)
subprojects {
    apply(plugin = rootProject.libs.plugins.ktlint.get().pluginId)
    apply(plugin = rootProject.libs.plugins.detekt.get().pluginId)

    // ktlint must not police generated code (Compose writes Kotlin under build/generated in a
    // style nobody here chose).
    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        filter { exclude { element -> element.file.path.contains("/build/generated/") } }
    }

    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = false
        config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
        parallel = true

        // WITHOUT THIS, detekt reads nothing at all: its default source layout is the JVM
        // convention (src/main/kotlin), which a KMP module does not have — the task reports
        // NO-SOURCE and stays green while violations sit in the tree. (Same inherited lesson.)
        source.setFrom(files("src"))
    }

    // detekt defaults its jvmTarget to the DAEMON's Java (26 on this machine), which 1.23.8 does
    // not recognize and fails on with a bare "26.0.1". The analyzed code targets 17; say so.
    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        jvmTarget = libs.versions.jvmTarget.get()
    }
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
