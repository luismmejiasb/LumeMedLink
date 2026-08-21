# ADR-0018 — Dependencies under control: exact names, the plugin classpath, and the trust root

- **Status:** Accepted · 2026-08-21 (fortification slice F20)
- **Related:** §8.8 and §13; threat model T6; ADR-0004 (the stack whose transitives these are).

## Context

This repo pinned every version in a catalog, locked every project configuration, and gated the
result against an allowlist with a named denylist. It looked closed. An adversarial review, plus
this session's own measurements, found that the most privileged surface had **no control at all**,
that the gate could be walked past by a name that merely resembled an allowed one, and that the
trust root everything else hangs from was the wrong file.

## What was wrong, each measured

**1. Prefix matching had no namespace boundary.** `case "$group" in "$p"*)` admitted anything
merely *beginning* with an allowed string. Measured: `androidx.health.connect` — a **clinical-data
API**, in the app whose entire identity is ADR-0001 — passed green, along with `io.ktorexploit` and
`org.jetbrainsevil`. The earlier bait rehearsal missed this because its bait names were unrelated
(`net.evil.tracker`); a hostile name that *shares a namespace* with a real one is precisely the one
a human waves through, and it was the one the gate could not see.

**2. The Gradle plugin classpath was invisible.** `allprojects { dependencyLocking { … } }` covers
project configurations only, so the plugin classpath appeared in no lockfile and the gate was
structurally unable to see any of it. That is the **highest-privilege** code in the build: a plugin
runs with the developer's full privileges and can rewrite the output APK. Locking it surfaced **24
groups nobody had ever reviewed**, including `org.tensorflow`, `com.google.crypto.tink` and
`com.android.tools.utp`.

**3. The wrapper jar was the wrong one.** `distributionSha256Sum` pins the Gradle distribution —
but the code that *enforces* that pin is `gradle-wrapper.jar` itself, the trust root and the only
binary this repo tracks. Verified against Gradle's published checksums: the committed jar was
authentic Gradle **9.4.1** while the properties pinned **9.7.1**, inherited by copying a sibling
repo's wrapper. Not an attack — but a *swapped* jar would have been exactly as invisible, because
nothing looked at it.

**4. The allowlist parser word-split.** An indented comment such as
`# transitive io.evil.beacon pulled in by ops` injected that group as an entry. The file's header
is prose full of dotted group names, so the exploit shape is the documented practice.

**5. Deleting a lockfile left the gate green.** It failed only when *zero* lockfiles existed, so
removing the module that holds the whole product dependency set was invisible.

**6. Two doctrines had no enforcement.** §7 forbids network outside the stack, yet
`org.apache.http.*` was importable; §8.1 says one redacting logging facade, yet `org.slf4j` ships
in the release APK (dragged by `kotlinx-coroutines-slf4j`) and no rule banned it.

**7. CI actions were pinned by mutable tag**, with no `permissions:` block.

## Decision

- **Exact group names, never prefixes**, and the allowlist is parsed line by line with comments
  stripped. Adding a dependency costs one deliberate line.
- **The plugin classpath is locked** (`buildscript.configurations.classpath`), in both
  `settings.gradle.kts` and the root build — and the gate reads `buildscript-gradle.lockfile`.
- **The denylist is classpath-aware.** A denied group **fails** when it reaches a shipped
  configuration and is **reported as a visible note** when it is build-time only. Blocking the
  build over AGP's own lint toolchain would get this gate switched off, and a gate that gets
  switched off protects nothing. This also makes "never shipped" claims checkable for the first
  time — one of them was already false.
- **`androidx.health` is denied by name**, with the reason written out. A refusal that explains
  itself beats an absence.
- **The wrapper jar is gated** against Gradle's published checksum, together with the requirement
  that jar and distribution name the same version.
- **CI actions are pinned by commit SHA** with `permissions: contents: read`. Note the trap: the
  `gradle/actions` v4 tag is *annotated*, so the tag object's SHA is not the commit SHA —
  dereferencing it was necessary and pinning the wrong one would simply have failed to resolve.
- **`.gitignore` covers credentials and signing material.** Every commit here is `git add -A`, so
  a keystore dropped in the tree would have been committed on the next one; the audit found the
  history clean, and this keeps the second half true.

## Gradle dependency verification: NOT adopted, and why

`gradle/verification-metadata.xml` is the only thing that pins **bytes** rather than coordinates,
and this ADR does not adopt it. Locking pins what resolved; an artifact re-published under the same
version passes an enforced lockfile green — the build script's comment claiming otherwise was
corrected in this change.

Reasons for waiting, stated so the decision can be revisited rather than forgotten:

- The entry count for a KMP + AGP + Compose project is in the hundreds, and the maintenance cost
  lands on every dependency bump — a cost that gets a control disabled if it is paid before the
  threat is real.
- It does **not** cover the two largest downloads in this build: the Kotlin/Native prebuilt
  toolchain (~497 MB of LLVM) and the Android SDK.
- **What would reverse this:** the app reaching a store, or CI producing the shipped artifact.
  Today the build is a developer's machine, which is a bigger hole than any artifact swap.

## Consequences

- The gate went from 441 to 500 modules checked — the difference is the plugin classpath that was
  never examined.
- **Not closed, recorded:** the Kotlin/Native toolchain and Android SDK are trusted downloads
  outside any verification; the JetBrains Compose dev repository is still declared and serves
  nothing this build needs; there is no repository content filtering; and the whole supply chain
  ends at one laptop, which is the largest residual risk of all.
