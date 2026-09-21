// Critical manifest: not to be rewritten as a side effect of another task.
// The runnable Android shell — a plain Android app, deliberately NOT a KMP module: since AGP 9.0
// the application plugin refuses to load alongside Kotlin Multiplatform, so this depends on
// `:composeApp` instead of being it.

plugins {
    alias(libs.plugins.androidApplication)
    // No `org.jetbrains.kotlin.android`: since AGP 9.0 Kotlin support is built in, and applying it
    // is a hard error rather than a redundancy.
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "com.luismejias.lumemedlink.android"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.luismejias.lumemedlink"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    // Declared, not inherited (F21, ADR-0021). There was no buildTypes block at all, so `debug`
    // was debuggable by AGP default and NOTHING asserted that `release` is not — while the debug
    // build is the one sideloaded onto a device for verification, and `run-as`/`adb pull` reach
    // its private data directory.
    buildTypes {
        getByName("release") {
            isDebuggable = false
            // isMinifyEnabled stays OFF and that is a decision, not an omission: R8 on a KMP +
            // Compose app needs keep rules this project has never exercised, and shipping an
            // untested shrinker is a bigger risk than the reverse-engineering it would slow down.
            // It is registered as owed work rather than switched on blind.
            isMinifyEnabled = false
        }
        getByName("debug") {
            // Explicit so nobody reads the absence as a claim. A debug build IS debuggable, IS
            // reachable by run-as, and must never carry a real doctor's token.
            isDebuggable = true
        }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Same reason as the KMP module: pinned in the build, not in the PATH.
    kotlin { jvmToolchain(libs.versions.jvmTarget.get().toInt()) }
}

dependencies {
    implementation(project(":composeApp"))
    implementation(libs.androidx.activity.compose)
    // The shell hosts BiometricPrompt, which requires a FragmentActivity (F4, ADR-0011).
    implementation(libs.androidx.fragment)
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
}

// §2.9 says "sin warnings, CI verde o no se mergea", and until 2026-09-21 that was true of
// :composeApp and false here — this module compiled with warnings tolerated while two documents
// claimed otherwise (audit). The shell is small, but it is where FLAG_SECURE, the tapjacking guard
// and the structure-export calls live: a deprecation warning on any of those is exactly the notice
// that must not scroll past.
//
// Scoped to this module's own compilations; the root build already exempts Kotlin METADATA tasks
// for a Compose Multiplatform 1.11 classpath duplication that is not ours to fix.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    compilerOptions.allWarningsAsErrors.set(true)
}
