# 0028 — El logout que no borraba la clave

**2026-09-21.** Segundo punto de la auditoría. ADR-0014 dice, desde el 2026-08-21, que el logout
borra cinco cosas. La tercera es **la clave del Keystore**, y es la que convierte «borrado» en
«irrecuperable». Nunca ocurrió.

## Qué hacía de verdad

```
App.endSession() → sessionManager.logout() → tokenStore.clear()
                 → secureStore.remove("session_tokens_v1") → File.delete()
```

Un archivo, desenlazado, con el booleano de `delete()` sin mirar. El alias `lume_session_tier1`
seguía vivo en AndroidKeyStore. El método que sí borra el namespace **y** la clave —
`SecureStore.wipe()`— tenía **un solo llamador en producción**, y era el sentinel de instalación.

En simple: el médico cierra sesión y vende el teléfono. Quedan (a) la clave AES-256 viva en el
hardware y (b) un bloque de ciphertext apenas desenlazado. Si ese bloque se recupera de una imagen
forense, la misma clave lo descifra. Es exactamente el escenario que el punto 3 dice haber cerrado.

## Lo que lo hace un caso de estudio, no un bug

**Dos tests verdes lo cubrían.**

- `wipeRemovesTheFilesAndTheKeystoreKeyItself` assertea el alias y los archivos — y llama a `wipe()`,
  el camino que producción no tomaba.
- `logoutLeavesNothingReadableAndNothingOnDisk` sí tomaba el camino real, y **no assertea ni los
  archivos ni el alias**, pese a prometer las dos cosas en su nombre.

Cada test es correcto por separado. El par desinforma. Es la variante «pareja» del documento que
miente, aplicada a los tests, y el octavo verde-por-razón-equivocada de este repo.

**Y la afirmación falsa vivía en tres ADRs**: 0014, 0009 y 0022. Las tres corregidas hoy.

**La pista estaba escrita en la propia ADR-0014**, en su última línea: «la wipe borra un namespace
entero en vez de una lista de llaves, así que una llave olvidada no puede sobrevivirla — el enum
existe para el *test*, no para dirigir el borrado». El código dirigía el borrado desde el enum, y
desde **una sola entrada** de él. El documento describía el diseño correcto; nadie comparó.

## Qué se construyó

`core/session/LogoutContract.kt`, con la forma que este repo ya usa para `enforceInstallBoundary`:
una función de nivel superior que un test alcanza entera. La secuencia estaba **inline dentro de la
composable** del shell, que es precisamente donde ninguna prueba llegaba.

Tres cosas más, todas encontradas por la misma auditoría:

1. **Borra el namespace y la clave**, no una entrada.
2. **Cada paso se intenta aunque el anterior lance.** El primer `throw` saltaba el resto: un store
   que fallara dejaba vivos la clave tier-2 y la ventana de bloqueo **mientras la UI ya había vuelto
   a Login**. Ahora `LogoutOutcome` nombra el paso que falló — un borrado a medias reportado como
   éxito es peor que un fallo reportado.
3. **`NonCancellable`.** Corría en el `rememberCoroutineScope()` del shell, que muere con la
   composición. Mandar la app al fondo a mitad de logout truncaba el borrado justo cuando el teléfono
   tiene más probabilidad de estar saliendo de las manos de su dueño.

## Medido, con control en vivo

- `LogoutContractTest`: 4 tests. **Tres cebos, tres rojos, cada uno por el test correcto** — volver a
  borrar una sola llave, abortar al primer fallo, y quitar `NonCancellable` (este último rompe
  exactamente un test, que es la señal de que el control mide lo que dice).
- **En emulador real**: `theProductionLogoutPathLeavesNoFileAndNoKey`, 4/4 verdes. Y el control que
  lo hace significar algo: **restaurado el defecto, el test de device falla** con «UNLOCK_CHALLENGE
  survived the production logout».
- Gate nuevo `check-logout-contract.sh` — cada paso declarado en el enum debe intentarse (dirigido
  por el enum, como el test del wipe, así que un paso futuro queda cubierto al declararse), el
  borrado debe ser por namespace, debe correr bajo `NonCancellable`, y el shell debe **llamar** al
  contrato en vez de re-inlinearlo. **4 cebos** en `rehearse-gates.sh`, que pasa de 15 a 19.

## Lo que NO se cambió, y por qué

El rechazo de un refresh (`SessionManager.refreshLocked`) sigue borrando sólo la entrada de tokens.
Es un final de sesión igual de real. **No se toca aquí porque es política, no implementación**: que
un refresh rechazado destruya la clave tier-1 y el material tier-2 es una decisión del autor, y una
enmienda no puede tomarla sola.

Y el shell **sabe** que el borrado quedó incompleto, pero todavía no se lo dice a nadie: hoy sólo lo
registra (`LogEvent.LOGOUT_INCOMPLETE`). Decírselo a la persona es del slice de login — y decir
«sesión cerrada» sobre un token que sobrevivió es el fallo de vocabulario del §8.7 con las apuestas
invertidas.

ADR-0014 (enmendada), ADR-0009 y ADR-0022 corregidas.
