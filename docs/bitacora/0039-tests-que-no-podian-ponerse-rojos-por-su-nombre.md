# 0039 — Tests que no podían ponerse rojos por la razón de su nombre

**Tipo:** `test` · 2026-10-07

## Qué llega

- **Tarea `0010` cerrada**; su punto 1 (correr `KeychainSecureStoreTest` dentro de un host) pasa a la tarea `0015`.
- `AndroidInstallSentinelTest` en un source set nuevo, `androidHostTest`.
- `PromptErrorMappingTest` (Android host) y `KeychainStatusMappingTest` (iOS): el mapeo de errores biométricos, probado en
  la capa que lo decide.
- `UnlockKeyContractTest` corregido para API 26–29.

## La forma común

Los tres tests tenían nombres correctos y no podían fallar por lo que su nombre dice. Uno corría en dos targets y aceptaba
cualquier rama: con el sentinel de Android purgando en cada arranque, seguía verde, porque «la purga purgó» era una de
sus ramas aceptadas. Otro probaba «cancelar no cuesta» con un doble del gate, por encima del único lugar donde eso se
decide — el código de error que el sistema entrega. El tercero se salía con `return` en las versiones donde no podía
medir, y el reporte lo contaba como aprobado.

El arreglo fue el mismo en los tres: que cada test pueda ponerse rojo por la razón de su nombre, **visto** con un cebo.
Para el del sentinel, además, el control del revés: el mismo cebo contra la versión vieja la dejó en verde, que es el
defecto reproducido y no supuesto.

## Lo que salió al fijar el mapeo

Al escribir la lista de códigos de `BiometricPrompt` que son gratis apareció uno que no lo es: **`ERROR_TIMEOUT`**. Un
prompt que expira porque nadie lo tocó cuenta como intento fallido. Por la intención de ADR-0020 de LumeMed («un médico no
pierde un intento por dejar el teléfono») debería ser gratis; por su corrección («lo que nadie clasificó cuenta») el mapeo
actual es defendible. No lo cambié: es una dirección de falla, y está en `PROGRESS.md` para el autor. Tampoco está medido
qué dispositivos lo emiten.

## Y un KDoc que mentía

`UnlockKeyContractTest` decía no necesitar huella enrolada. Su propia primera corrida (bitácora 0010) falló porque
Android no crea una clave por-uso sin biometría. Corregido.

## Lo que no se verificó

El lado API 26–29 de `UnlockKeyContractTest`: la única imagen en esta máquina es API 37.
