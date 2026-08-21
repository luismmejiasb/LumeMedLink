// Critical manifest: not to be rewritten as a side effect of another task.

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    // The JDK, pinned where the BUILD can see it instead of in each machine's PATH. Gradle
    // provisions 17 itself, so "it built locally" and "it built in CI" are the same statement.
    jvmToolchain(libs.versions.jvmTarget.get().toInt())

    // §3: Kotlin publishes everything by default; with this, `public` is a choice, not an accident.
    explicitApi()

    // NOT `com.android.library` + a top-level `android { }`: since AGP 9.0 that plugin refuses to
    // load alongside Kotlin Multiplatform. Same block name, different place.
    android {
        namespace = "com.luismejias.lumemedlink"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()

        // Without this, `commonTest` compiles for iOS and is silently skipped on Android.
        withHostTest {}

        // Instrumented tests (F4): some security properties can only be confirmed by a real
        // Android runtime — a JVM host has no AndroidKeyStore, so KeyInfo cannot be asked there
        // whether the tier-2 key really requires authentication.
        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    // TWO iOS targets, not three: Compose Multiplatform 1.11.1 publishes no artifacts for the
    // Intel-Mac simulator (`iosX64`), and declaring it fails dependency resolution.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "LumeMedLink"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.jetbrains.lifecycle.runtime.compose)
            // core/networking — the hardened stack (ADR-0004). Ktor never leaks past core/:
            // detekt's ForbiddenImport bans io.ktor.* outside it.
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }

        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.biometric)
        }

        getByName("androidDeviceTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.ext.junit)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }

    compilerOptions {
        // §2.9: no warnings tolerated.
        allWarningsAsErrors.set(true)
    }
}

// UPSTREAM BUG WORKAROUND, scoped to one task and stated rather than hidden: Compose
// Multiplatform 1.11.1 registers a resource-copy task for the AGP KMP *deviceTest* variant
// without configuring its output directory, so merely configuring that task graph fails
// ("property 'outputDirectory' doesn't have a configured value"). This module declares NO Compose
// resources at all, so the task has nothing to copy. Disabled for the deviceTest variant only —
// the main and host-test variants keep theirs. Revisit when CMP is bumped.
tasks.matching { it.name == "copyAndroidDeviceTestComposeResourcesToAndroidAssets" }
    .configureEach { enabled = false }
