# ADR-0033 — The design system is LumeUIComposer, consumed by path until its first tag

- **Status**: Accepted — decided by the author on 2026-10-06; **not implemented** (`tareas/PENDING/0013`)
- **Date**: 2026-10-06
- **Related**: ADR-0002 §UI (the kit or the `designkit` fallback), ADR-0013 (input, amended the same
  day), ADR-0018 (dependencies), ADR-0024 (structure export), §5 and §13 of this constitution.
  The audit this rests on lives in the kit: `../LumeUIComposer/docs/audits/lumemedlink-fit-2026-10-06.md`.

## Context

ADR-0002 left the design system to one of two roads: LumeUIComposer, the Compose twin of
LumeUIKit, "if its Slice 0 survives", or an internal `designkit` module. S0.3 has been parked on that
verdict since 2026-08-20.

On 2026-10-06 the author took the device check with a screen reader out of the kit's priorities and
out of this app's way (the kit's `CLAUDE.md` §0.1). The kit has 67 components, its toolchain is
identical to this repo's (Kotlin 2.4.10, Compose MP 1.11.1, AGP 9.2.1, min SDK 26, compile SDK 36),
and every surface this app plans maps to a component that exists. The fallback would now be
duplicating a kit that is alive.

The audit found that what does not fit is not the visual contract but the security one, and that is
what this ADR exists to pin down before the first styled screen.

## Decision

1. **The design system is LumeUIComposer.** No `designkit` module is built. A component two screens
   need goes to the kit, never inline (§5).
2. **Consumed by path, as a Gradle composite build**, `includeBuild("../LumeUIComposer")`, the way
   LumeMed consumes LumeUIKit. This consumes a branch, which the kit's own §12 forbids for releases,
   so it is declared as the **pre-1.0 arrangement and it ends at the kit's first tag** (its Slice 24):
   from then on this app consumes a published version.
3. **The kit's one new dependency group is admitted**: `org.jetbrains.compose.components`
   (components-resources), one exact line in `config/dependency-allowlist.txt`, landed in the same
   change as the lockfile that first carries it (§13 asks for this ADR).
4. **The Android app copies the kit's compose resources itself.** AGP 9.2's KMP-library plugin ships
   no assets, so without a copy task in `androidApp` the first `LumeIcon.painter()` throws
   `MissingResourceException` (the kit's `docs/Porting.md`, integration facts; reference task in the
   kit's `sampleAndroid/build.gradle.kts`). iOS is covered by `embedAndSignAppleFrameworkForXcode`.
5. **Kit text inputs are wrapped, never called by a screen.** `core/input/SensitiveTextField` wraps the
   kit's fields and decides their keyboard; `check-input-surfaces.sh` refuses `LumeTextField`,
   `LumeSearchField`, `LumeOTPField` and `LumeRichTextEditor` outside `core/input/` (ADR-0013,
   amendment of 2026-10-06). That rule landed with this ADR, before any kit field exists here, with its
   bait in `rehearse-gates.sh`.
6. **The shell's tab bar comes from the kit** (`LumeTabBar`, the kit's `tareas/PENDING/0006`). Compose's
   `NavigationBar` is Material, which the kit refuses as a visual language, and a bar built here would
   be a component living in a screen.

## Consequences

- S0.3 is no longer parked. It is the wiring: the composite build, the allowlist line, the resources
  copy task, `LumeTheme` at the root, and `SensitiveTextField` re-dressed over the kit.
- **The kit has to grow one thing before the first personal-data field**: a way to ask for verbatim
  free text — no autocorrect, no capitalization — because the kit's `Default` asks for both, and a
  name or an address typed into it reaches the keyboard's learned vocabulary. Half of it already exists
  in LumeUIKit and was never ported (`LumeTextCase.none`, capitalization only); the autocorrect half
  exists in neither kit (the twin's `tareas/PENDING/0005`, LumeUIKit's `tareas/PENDING/0094`). Until
  both land, `SensitiveTextField` keeps its own `BasicTextField` for `PERSONAL_DATA` free text.
- `no_hardcoded_style` (§9, still [manual]) can become a gate once screens are written against kit
  tokens; the kit's `Scripts/lint-layout.sh` is the model.
- **Not verified, and named so it is not mistaken for safe**: the kit's popups (`LumeAlert`,
  `LumeMenu`, `LumePickerField`) are their own windows on Android, and this app's tapjacking filter and
  autofill exclusion are set on the activity's decor view (`MainActivity.kt`). Whether a popup is under
  either is `tareas/PENDING/0014`, to be measured on the emulator.
- CI for this repo now needs the kit checked out beside it. CI is on `workflow_dispatch`; the job that
  compiles gains a second checkout the day it runs again.
