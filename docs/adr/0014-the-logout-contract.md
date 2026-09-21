# ADR-0014 — The logout contract: what it erases, and the one thing it cannot

- **Status:** Accepted · 2026-08-21 (fortification slice F5)
- **Related:** §8.13 (logout = hard wipe) and §8.7 (T16 vocabulary) of this constitution; threat
  model T3 (later persistence); ADR-0005 (the tiers being erased); ADR-0011 (tier-2 material).

## Context

If closing the session does not truly erase the token, the next person holding the phone is inside
without credentials — and "truly" is doing a lot of work in that sentence. A logout that clears
memory but leaves ciphertext on disk is recoverable by a forensic dump; one that deletes files but
keeps the Keystore key alive leaves any stray copy still decryptable.

Until this slice, every test of `core/session` ran against a **fake in-memory map**. The contract
was proven as logic and merely asserted as behaviour — a fake cannot tell you whether
AndroidKeyStore persisted anything, whether the file left the disk, or whether the key was really
deleted (bitácora 0007 declared this gap; this ADR closes it).

## Decision

**Logout erases, in one call, everything a session leaves behind:**

1. **Memory** — the in-process token pair.
2. **Disk** — the ciphertext files, *deleted*, not merely overwritten or orphaned.
3. **The Keystore key itself** — so that if any copy of the ciphertext survives anywhere (a
   backup that predates `allowBackup=false`, a forensic image), it is permanently undecryptable.
   Deleting the key is what turns "erased" into "unrecoverable".
4. **The tier-2 unlock material** — the biometric key and its challenge (ADR-0011).
5. **The lock state** — the next session starts born-locked.

**The set of secrets is enumerable by construction.** `SecureStoreKey` is an enum, not loose
constants, and `SecureStoreWipeTest` iterates it: a secret added by a future slice is covered by
the wipe test the moment it is declared. Nobody has to remember to extend that test, and
remembering is exactly what fails.

## What logout does NOT do, said plainly

**This is a LOCAL logout. It does not revoke anything server-side.** The app has no revocation
call wired, and a refresh token exfiltrated *before* logout would remain valid to the backend until
it expires. Two consequences, neither of them cosmetic:

- **The UI may never say "cerraste sesión en todos tus dispositivos"**, or anything implying remote
  effect. This is the same vocabulary discipline as T16 (a local purge is not the right of
  erasure): the app says what it did, which is "cerrar sesión en este dispositivo".
- **Whether the contract offers revocation is an open question**, not an assumption. It gets
  confirmed when the auth flow is wired (F10/F11); if the platform has no such operation, it
  becomes a backend request rather than a silently accepted gap.

## Consequences

- Verified on a real device, not asserted: `LogoutWipeOnDeviceTest` (4 tests, green) exercises the
  actual AndroidKeyStore-backed store — round trip, **the plaintext is not on disk**, logout leaves
  nothing readable, and the wipe removes both the files and the key.
  ⚠️ **Esta viñeta fue la fila tranquilizadora que nadie verificó.** Las dos mitades eran ciertas por
  separado y falsas juntas: el test que borra la clave llamaba a `wipe()`, y el logout no. Ver la
  enmienda al final.
- **iOS is unverified**, as everywhere else in this repo: the hostless Kotlin/Native runner reaches
  no keychain, so the iOS store's wipe is proven by code review and the shared contract tests
  only. It gets its device proof when the iOS host exists.
- Because the wipe clears a whole namespace rather than a list of keys, a forgotten key cannot
  survive it — the enum exists for the *test*, not to drive the erase.

---

## Enmienda — 2026-09-21: el punto 3 no estaba implementado

Una auditoría externa encontró que **el logout nunca borró la clave**. `SessionManager.logout()`
llamaba a `tokenStore.clear()`, que desenlazaba **un** archivo con `File.delete()` y no tocaba ni el
namespace ni el alias `lume_session_tier1`. El método que sí borra las dos cosas — `SecureStore.wipe()` —
tenía **un solo llamador en producción**, y era el sentinel de instalación, no el logout.

De los cinco puntos de arriba ocurrían el 1, el 2 **a medias** (un archivo, no el namespace), el 4 y
el 5. **El punto 3 no ocurrió nunca**, y es justamente el que convierte «borrado» en «irrecuperable».

Peor que el defecto: **dos tests verdes lo cubrían**. `wipeRemovesTheFilesAndTheKeystoreKeyItself`
asserta el alias y los archivos, pero llama a `wipe()` — el camino que producción no tomaba. Y
`logoutLeavesNothingReadableAndNothingOnDisk`, que sí tomaba el camino real, **no asserta ni los
archivos ni el alias pese a prometer ambos en su nombre**. Es el octavo verde-por-razón-equivocada
de este repo, y el primero en el que la afirmación falsa vivía en tres ADRs a la vez (ésta, ADR-0009
y ADR-0022).

Y esta ADR llevaba la pista escrita en su propia última línea: «la wipe borra un namespace entero en
vez de una lista de llaves, así que una llave olvidada no puede sobrevivirla — el enum existe para el
*test*, no para dirigir el borrado». El código dirigía el borrado desde el enum, y desde una sola
entrada de él.

**Qué cambia, sin cambiar la decisión.** La decisión de 2026-08-21 era correcta; faltaba el código.

1. El contrato vive en `core/session/LogoutContract.kt`, en una función que un test alcanza entera —
   la misma forma que `enforceInstallBoundary`. La secuencia estaba **inline en la composable** del
   shell, que es donde ninguna prueba llegaba.
2. **Borra el namespace y la clave** (`secureStore.wipe()`), no una entrada.
3. **Cada paso se intenta aunque el anterior lance.** Antes, el primer fallo saltaba el resto: un
   store que fallara dejaba vivos la clave tier-2 y la ventana de bloqueo mientras la UI ya había
   vuelto a Login. El resultado (`LogoutOutcome`) nombra el paso que falló, porque un borrado a
   medias que se reporta como éxito es peor que un fallo que se reporta.
4. **`NonCancellable`.** Corría en el `rememberCoroutineScope()` del shell, que muere con la
   composición: mandar la app al fondo a mitad de logout truncaba el borrado exactamente cuando el
   teléfono tiene más probabilidad de estar saliendo de las manos de su dueño.

**Verificado, no afirmado:** `LogoutContractTest` (4 tests, y los tres cebos —borrar una llave,
abortar al primer fallo, quitar `NonCancellable`— vistos rojos, cada uno por el test correcto) y
`LogoutWipeOnDeviceTest.theProductionLogoutPathLeavesNoFileAndNoKey`, **4/4 en emulador real con
control en vivo**: restaurado el defecto, el test de device falla con «UNLOCK_CHALLENGE survived the
production logout». Gate nuevo `Scripts/check-logout-contract.sh`, con 4 cebos en `rehearse-gates.sh`.

**Lo que sigue fuera:** el rechazo de un refresh (`SessionManager.refreshLocked`) sigue borrando sólo
la entrada de tokens, no el namespace. Es un final de sesión igual de real, y **no se cambia aquí
porque es política, no implementación**: decidir que un refresh rechazado destruya la clave tier-1 y
el material tier-2 es una decisión del autor, no una que esta enmienda pueda tomar sola.
