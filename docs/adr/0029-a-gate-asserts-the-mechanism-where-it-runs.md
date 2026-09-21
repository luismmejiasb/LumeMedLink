# ADR-0029 — A gate asserts the mechanism, in the place where it runs

- **Status**: Accepted
- **Date**: 2026-09-21
- **Supersedes**: nothing. Amends the technique of the gates written in S0.2, F1, F3, F6, F12 and ADR-0025.

## Context

An external audit walked through two gates with bait their authors had not thought of. Both gates
were green with their control **deleted**:

1. `check-network-posture.sh` and `check-backup-posture.sh` filtered XML comments line by line
   (`grep -v '<!--'`). A comment spanning four lines carries the marker only on its first line, so
   the four posture attributes — `allowBackup`, `dataExtractionRules`, `usesCleartextTraffic`,
   `networkSecurityConfig` — were moved inside one comment, the file stayed well-formed, the
   `<application>` element lost all four, and both gates passed. That is F6 and F12 — two closed
   slices — removable with CI green.

2. `check-screen-security.sh` and `check-input-surfaces.sh` asserted presence with
   `grep -r <token> androidApp/src`. `androidApp/src/test` is inside that path. Deleting
   `FLAG_SECURE`, `filterTouchesWhenObscured`, `denyAutofillExport()` and `denyContentCapture()`
   from `MainActivity` and putting those strings in a `listOf` inside a test file — dead code that
   never runs — left both gates green. That is F1 and F3.

Neither was a regression. **Both had been blind since birth**, and the rule that should have caught
them — "cada gate se ensaya con archivo-cebo antes de confiar en su verde" (§9) — *was* followed.
It was followed by the person who had just written the gate, which is the weakness: the author of a
gate writes the bait their gate catches, because it is the same head. And the rehearsal was an act,
not an artifact, so nothing repeated it.

The same two techniques were found in four more gates: `check-preauth-surfaces.sh` (a real
declaration with a trailing comment on the same line was dropped by `grep -v '<!--'` entirely) and
`check-ios-host.sh` (its line-based Swift filter only recognised comments whose line *starts* with
a marker, so the inner lines of a `/* … */` block walked through).

## Decision

**1. A gate asserts the MECHANISM, not the token.** The pattern is the call or the assignment —
`window\.decorView\.filterTouchesWhenObscured[[:space:]]*=[[:space:]]*true`, not
`filterTouchesWhenObscured`. An `import` of the same name, a constant, a doc comment and a string
cannot satisfy a call-shaped pattern. The existing exception stands and is now explicit: where the
gate is an ALLOWLIST of values (ADR-0024's `importantForAutofill`), the assignment shape is what is
asserted, so an instrumented test may still NAME a forbidden constant to reproduce the OS.

**1b. Where the mechanism has a direction, the gate asserts the direction.** Bait caught a third
case while this ADR was being written: `check-ios-host.sh` required the presence of
`shouldAllowExtensionPointIdentifier` and stayed green when its body was changed to `return true`.
A hook that permits everything is the default with extra steps. The gate now requires the refusal.

**2. A gate asserts in the PLACE where the mechanism runs.** Presence checks are scoped to the
shipped path — `androidApp/src/main`, never `src/test` or `src/androidTest`. A control that only
exists in a test protects nobody.

**3. Comments and string literals are removed by a TOKENIZER, never by a line filter.**
`Scripts/lib/uncomment.py` blanks them in place (line numbers survive, so failure messages still
point at the real line), handles nested Kotlin and Swift block comments, and respects string
escapes. String literals are dropped for presence checks only; an absence check keeps them,
because a forbidden API named in a string is still worth a look.

**4. XML configuration is PARSED, not grepped.** `Scripts/lib/xmlattr.py` reads an attribute off a
named element. A parser cannot see a comment, so that whole class of bait becomes impossible by
construction rather than by a filter someone has to keep ahead of. It closes a second hole the grep
never covered: `android:allowBackup` on an `<activity>` is not the application's backup posture,
but it is the same text.

**5. The bait rehearsal becomes an artifact that RUNS.** `Scripts/rehearse-gates.sh` deletes or
disguises each control, in the spellings that actually fooled these gates, and requires the gate to
turn red. It runs in CI, last, after the gates it rehearses. Fifteen baits today.

A note on its own instrument, recorded because it is the same defect one level up: the first draft
of the rehearsal built a `git worktree` of HEAD while the fixes sat uncommitted, and reported
eleven undetected baits against gates that were already repaired. It now measures the working tree.

## Consequences

- Six gates were rewritten; the real code passes all of them and fifteen baits are caught.
- Two shared helpers exist under `Scripts/lib/`, the first shared code in `Scripts/`. The
  duplication they remove is the reason: the same defect appeared in six gates because the
  technique was copy-pasted, and a fix applied six times is a fix that will be applied five times
  next year.
- CI gains a step. It is cheap (seconds, ubuntu) and it is the only step that can fail because
  *another step cannot fail*.
- **What this does NOT fix.** ~~The audit found the same family in gates outside this change:
  `check-data-boundary.sh` sees only `val`/`var` declarations and does not know the word «motivo»;
  `check-wrapper.sh` asserts `distributionSha256Sum` by presence, not by value; the CI action pin
  check accepts `@latest`; a package named `core` under `androidApp` disables `ForbiddenImport`.
  Each needs its own bait and its own fix.~~ **CLOSED 2026-09-21, second pass — see below.** They
  are kept struck through rather than deleted because the list is the evidence that naming what you
  did not fix is what gets it fixed.
- The rehearsal is not proof of completeness. It catches the baits someone thought of — which is
  the same limit as before, with one difference that is the whole point: **it keeps catching them.**

---

## Segunda pasada — 2026-09-21: los gates que esta ADR dejó nombrados

Cada uno reproducido con cebo ANTES de tocarlo, y cada arreglo vuelto a cebar después. Seis gates.

**`check-data-boundary.sh` — veía una propiedad y nada más.** Sólo hacía match después de `val`,
`var` o `const val`. Pasaron cuatro cebos, los cuatro Kotlin corriente: un **parámetro de función**
(`fun render(motivoClinico: String)`), una **entrada de enum**, un **nombre de tipo**
(`class AllergyBanner`) y un **typealias**. Enumerar posiciones sintácticas es el mismo juego perdido
que nombrar grafías malas (F20, ADR-0024). Ahora la regla es total: una palabra clínica no aparece
como identificador **en ninguna posición, en ningún archivo** — verificada verde contra el árbol de
hoy antes de adoptarla, que es lo que hace segura una regla así de estricta. Y **`motivo` faltaba del
vocabulario**, que es el campo que §1.0 nombra a mano: «Nunca el motivo clínico».

**`check-wrapper.sh` — afirmaba por presencia lo que debía afirmar por valor.**
`grep -q '^distributionSha256Sum='` pasaba con la línea vacía y con un hash de ceros. Ahora el
checksum está **fijado por valor** en el script (obtenido de
`services.gradle.org/distributions/gradle-9.7.1-bin.zip.sha256` y comparado hoy) y debe coincidir con
el del archivo: dos lugares que tienen que concordar. Y se cerró un hueco que nadie había nombrado:
**el HOST de la distribución no se verificaba**. La comprobación de versión sólo mira el nombre del
archivo, así que `https://evil.test/distributions/gradle-9.7.1-bin.zip` la satisfacía — este build
descargaría y **ejecutaría** un Gradle de otro sitio. Más `http://` en claro y
`validateDistributionUrl`. Cinco cebos.

**El pineo de acciones de CI — una denylist de formas de tag.** `v?[0-9]…|main|master`, así que
`@latest`, `@develop`, `@release`, `@HEAD` y cualquier tag no numérico entraban. Invertido a
**allowlist**: SHA de 40 caracteres, con el tag opcional como comentario al final, o falla — incluidas
las formas que todavía no existen.

**Un paquete `core` bajo `androidApp` — exento de todo sin ser core.** La regla I5 de
`check-feature-isolation.sh` sólo recorría `composeApp/src`, y P3/P4 de
`check-forbidden-patterns.sh` excluían **cualquier** ruta con `/core/`. Medido: un
`getSharedPreferences(...).putString("t", token)` en
`androidApp/src/main/kotlin/com/luismejias/lumemedlink/core/` pasaba los dos. I5 ahora recorre los dos
módulos y la exención está **anclada** al core canónico, que es un directorio en un módulo.

**`check-logging.sh` — las dos mitades apagadas justo donde viven los tokens.** detekt exime `core/`
del `ForbiddenImport` —tiene que hacerlo, ahí viven Ktor y OkHttp legítimamente— así que
`import android.util.Log` estaba permitido, y entonces la llamada se escribe `Log.d(...)`, que el
patrón **cualificado** de este gate no veía. Medido con cebo: un token de sesión a logcat desde
`core/session`, con CI en verde. Ahora se afirma la **forma corta** (sin disparar con `LumeLog.`) y el
**import**, sin exención: este gate es el total, y el carve-out de detekt es sobre red, no sobre logs.

**`check-preauth-surfaces.sh` — la mitad iOS no estaba escrita.** Todo el gate recorría
`composeApp/src androidApp/src`, así que una `UNUserNotificationCenter`, un widget, un `NSUserActivity`
o un `UserDefaults` **en Swift** pasaban sin tocar nada — y el `UserDefaults` de Swift tampoco lo veía
P3, que es Kotlin. Escrita, con el tokenizador para que un comentario siga siendo prosa.

El ensayo pasa de 15 cebos a **35**.
