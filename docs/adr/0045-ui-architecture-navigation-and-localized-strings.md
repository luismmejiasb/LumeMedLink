# ADR-0045 — How a screen is built: layers, navigation, and every string localized in Spanish and English

- **Status**: Accepted (2026-10-11) — the author approved the proposal in session.
- **Related**: ADR-0008 (the feature tree), 0013, 0033, 0039, 0044; constitution §3–§7.

## Context

The UI build starts (the design handoff, ADR-0044). The constitution fixes the tree and MVVM but not three
things a tabbed app needs: how navigation works, what owns a screen's state across rotations, and where text
lives. Until now copy was Kotlin constants (`AuthCopy`) and only Spanish existed.

## Decision

**Per layer:**

| Layer | Pattern |
| --- | --- |
| `app/` | Composition root, constructor injection, no DI framework. The one place a concrete implementation (unwired, scripted, HTTP) is chosen. |
| Navigation | A coordinator per flow — a route stack and an exhaustive back policy, pure Kotlin — rendered by a host. The tabbed app is **one stack per tab over `LumeTabBar`**, same pattern. No navigation library. |
| Screen | Stateless composable: state in, intents out, UI only from LumeUIComposer. |
| State | A **`androidx.lifecycle.ViewModel` per tab root** (and per flow), exposing `StateFlow` of an immutable UI state with derived properties and a redacted `toString`. |
| Use cases | One operation per class, **only where there is a rule** — no pass-through wrappers around a gateway. |
| `core/` | Interfaces with interchangeable implementations (`Unwired*`, `Scripted*`, `Http*`); `expect/actual` adapters; facades as choke points. Sealed results, never exceptions, across the boundary. |
| `shared/` | Value objects validated at construction, redacted `toString`. |

**Every user-facing string is a Compose Multiplatform string resource** (`composeResources/values/strings.xml`
and `values-en/strings.xml`), reached through the generated `Res.string.*` — no literal user text in Kotlin,
including content descriptions. **Spanish is the base** (`values/`), the fallback for any locale without its
own folder; **English** is the second. The device's language decides. Placeholders are positional
(`%1$s`, `%1$d`) so a translation can reorder them; plurals use `plurals`.

`org.jetbrains.compose.components:components-resources` becomes a direct dependency of `:composeApp`. It is
not a new artifact: it already resolves through LumeUIComposer, same group and version, already allowlisted.

## Consequences

- A screen's ViewModel cannot read a string resource (it is not composable): it emits **which** message —
  an enum or a sealed key — and the screen resolves it. That keeps ViewModels testable without resources.
- The deviceTest resource-copy workaround in `composeApp/build.gradle.kts` assumed this module had no
  resources; it is re-checked when the first resource lands.
- Strings the gates scan today (the pre-auth surfaces, ADR-0012) move to resources; the gates follow.
- Adding a language is a folder, not a code change.
