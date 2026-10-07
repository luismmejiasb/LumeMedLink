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

    // THE KEYCHAIN SPEC RUNS HOSTED (task 0015). The Kotlin/Native test binary is a bare process, and
    // securityd grants a bare process no keychain. Measured on 2026-10-07, both halves needed:
    // spawned `--standalone` (Kotlin's default) every SecItem call answers -25291 errSecNotAvailable,
    // and spawned inside a booted simulator WITHOUT entitlements it answers -34018
    // errSecMissingEntitlement. With the simulator entitlements linked in — what Xcode does for an
    // app — and a booted device, the six pass.
    iosSimulatorArm64().binaries.matching { it is org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable }
        .configureEach {
            linkerOpts(
                "-sectcreate",
                "__TEXT",
                "__entitlements",
                project.file("src/iosTest/keychain-tests.entitlements").absolutePath,
            )
        }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.jetbrains.lifecycle.runtime.compose)
            implementation(libs.jetbrains.lifecycle.viewmodel.compose)
            // The design system (ADR-0033): every screen draws with LumeTheme's tokens and the kit's
            // components. A text field of the kit is reached only through core/input (ADR-0013).
            implementation(libs.lumeuicomposer)
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
            // ApplicationProvider: the device tests need a real Context to reach the real store.
            implementation(libs.androidx.test.core)
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

// The booted simulator the hosted half needs (task 0015). LUME_IOS_TEST_DEVICE names its UDID; CI
// boots one and sets it, and so does a local verification run. Without it the iOS tests run as
// before — standalone — and the Keychain spec cannot: it is EXCLUDED and the run says so out loud,
// because a class that disappears from the count without a word is how six tests stayed skipped
// for six weeks.
tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest>().configureEach {
    val hostDevice = providers.environmentVariable("LUME_IOS_TEST_DEVICE").orNull
    if (hostDevice != null) {
        standalone.set(false)
        device.set(hostDevice)
    } else {
        filter.excludeTestsMatching("*.KeychainSecureStoreTest")
        doFirst {
            logger.warn(
                "KeychainSecureStoreTest NOT RUN: set LUME_IOS_TEST_DEVICE to a booted simulator's UDID " +
                    "(task 0015). A bare test process has no keychain.",
            )
        }
    }
}
