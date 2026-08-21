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
