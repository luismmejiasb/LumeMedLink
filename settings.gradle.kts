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
    // FAIL_ON_PROJECT_REPOS, and it is a supply-chain control, not tidiness (§8.8, ADR-0018): the
    // default (PREFER_PROJECT) lets ANY module declare its own `repositories { }` and have it win
    // over this list. A module adding one line would then resolve dependencies from somewhere this
    // file never named, and the lockfile would faithfully record the result. The allowlist is only
    // an allowlist if it is the only list. Declared rather than inherited, per §0.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

// The design system, consumed BY PATH until its first tag (ADR-0033, task 0013): a composite build,
// as LumeMed consumes LumeUIKit. The coordinate below is a name, not a download — it resolves to the
// sibling checkout and never to a repository, so nothing about it reaches a lockfile except what the
// kit itself depends on (and that is still held to the allowlist). It ends at the kit's first tag,
// when the coordinate gains a version and this block goes.
//
// `lume.kit.path` points it elsewhere, and the reason is measured: a composite build compiles the
// kit's WORKING TREE, so a parallel session halfway through an edit over there breaks this build here
// (2026-10-07: LumeTabBar half-written, LumeSectionPicker unresolved). Verification runs while the kit
// is being edited point this at an export of the kit's last commit — `Scripts/kit-snapshot.sh` — whose
// directory is still named LumeUIComposer, because the included build's name is its directory's.
includeBuild(providers.gradleProperty("lume.kit.path").getOrElse("../LumeUIComposer")) {
    dependencySubstitution {
        substitute(module("cl.lume:lumeuicomposer")).using(project(":lumeuicomposer"))
    }
}

// The KMP module: all product code lives here, in commonMain's ADR-0008 tree.
include(":composeApp")

// The runnable Android shell. A plain Android app, deliberately NOT a KMP module: since AGP 9.0
// the application plugin refuses to load alongside Kotlin Multiplatform, so the shell depends on
// `:composeApp` instead of being it.
include(":androidApp")

// No empty modules: the tree grows with the slices.
