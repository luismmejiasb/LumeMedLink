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
            // The platform on the developer's computer (ADR-0062). The emulator's name for it by default; a physical
            // phone passes -Plume.localBackend=http://localhost:3000 after `adb reverse tcp:3000 tcp:3000`. Release
            // has no such field, and its BuildEnvironment.kt has no URL.
            val localBackend = providers.gradleProperty("lume.localBackend").orElse("http://10.0.2.2:3000").get()
            buildConfigField("String", "LUME_LOCAL_BACKEND", "\"$localBackend\"")
        }
    }

    buildFeatures {
        compose = true
        // Only for the debug build's local backend above (ADR-0062).
        buildConfig = true
    }

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

// ── The kit's Compose resources, carried by the app (task 0013, ADR-0033) ───────────────────────
//
// AGP 9.2's KMP-library plugin cannot ship assets (probed by the kit: its variant exposes
// `sources.assets = null`), so LumeUIComposer's Android artifact carries no `composeResources/`, and
// without this copy the first `LumeIcon.painter()` throws MissingResourceException. iOS needs nothing:
// the Xcode embed flow syncs them. Ported from the kit's own reference consumer,
// `../LumeUIComposer/sampleAndroid/build.gradle.kts`, with one difference: the kit is an INCLUDED
// build here, so its task is reached through `gradle.includedBuild` and its output by path — a
// composite build does not hand out another build's `project(...)`.
abstract class CopyKitComposeResourcesIntoAssets @javax.inject.Inject constructor(
    private val fs: FileSystemOperations,
) : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val preparedResources: ConfigurableFileCollection

    @get:Input
    abstract val resourcePackage: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun copy() {
        fs.sync {
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
            preparedResources.forEach { dir -> from(dir) }
            into(outputDirectory.dir("composeResources/${resourcePackage.get()}"))
        }
    }
}

val copyKitComposeResourcesIntoAssets =
    tasks.register<CopyKitComposeResourcesIntoAssets>("copyKitComposeResourcesIntoAssets") {
        val kit = gradle.includedBuild("LumeUIComposer")
        preparedResources.from(
            kit.projectDir.resolve(
                "lumeuicomposer/build/generated/compose/resourceGenerator/preparedResources/commonMain/composeResources",
            ),
        )
        dependsOn(kit.task(":lumeuicomposer:prepareComposeResourcesTaskForCommonMain"))
        resourcePackage.set("cl.lume.uicomposer.generated.resources")
    }

// ── Firebase's options, without Google's plugin (ADR-0052 §8) ──────────────────────────────────────
//
// `google-services.json` stays out of git (.gitignore), and the `google-services` Gradle plugin stays out of the
// build: a plugin runs with the build's privileges (ADR-0018). Its one job here is small enough to do in the open —
// turn the file into the string resources `FirebaseOptions.fromResource` reads. Without the file this writes nothing,
// `fromResource` answers null, and core/telemetry never starts Firebase.
abstract class FirebaseOptionsResources : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val config: ConfigurableFileCollection

    @get:Input
    abstract val applicationId: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val out = outputDirectory.get().asFile
        out.deleteRecursively()
        val file = config.files.firstOrNull { it.isFile } ?: return

        @Suppress("UNCHECKED_CAST")
        fun Any?.map() = this as Map<String, Any?>

        @Suppress("UNCHECKED_CAST")
        fun Any?.list() = this as List<Any?>
        val json = groovy.json.JsonSlurper().parse(file).map()
        val project = json["project_info"].map()
        val client = json["client"].list().map { it.map() }.firstOrNull {
            it["client_info"].map()["android_client_info"].map()["package_name"] == applicationId.get()
        } ?: throw GradleException("google-services.json has no client for ${applicationId.get()}")
        val apiKey = client["api_key"].list().first().map()["current_key"]
        val values = linkedMapOf(
            "google_app_id" to client["client_info"].map()["mobilesdk_app_id"],
            "gcm_defaultSenderId" to project["project_number"],
            "google_api_key" to apiKey,
            "google_crash_reporting_api_key" to apiKey,
            "project_id" to project["project_id"],
            "google_storage_bucket" to project["storage_bucket"],
        ).filterValues { it != null }
        val xml = values.entries.joinToString("\n") { (name, value) ->
            val escaped = value.toString().replace("&", "&amp;").replace("<", "&lt;").replace("'", "\\'")
            "    <string name=\"$name\" translatable=\"false\">$escaped</string>"
        }
        File(out, "values").mkdirs()
        File(out, "values/firebase_options.xml").writeText("<resources>\n$xml\n</resources>\n")
    }
}

val firebaseOptionsResources = tasks.register<FirebaseOptionsResources>("firebaseOptionsResources") {
    config.from(layout.projectDirectory.file("google-services.json"))
    applicationId.set(android.defaultConfig.applicationId)
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(
            copyKitComposeResourcesIntoAssets,
        ) { it.outputDirectory }
        variant.sources.res?.addGeneratedSourceDirectory(firebaseOptionsResources) { it.outputDirectory }
    }
}
