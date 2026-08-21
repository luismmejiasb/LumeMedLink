// Critical manifest: not to be rewritten as a side effect of another task.

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
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
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }

    compilerOptions {
        // §2.9: no warnings tolerated.
        allWarningsAsErrors.set(true)
    }
}
