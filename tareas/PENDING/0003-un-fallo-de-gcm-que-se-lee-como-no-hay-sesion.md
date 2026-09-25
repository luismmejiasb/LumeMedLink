# 0003 · Un fallo de autenticación GCM se lee como «no hay sesión»

## De dónde sale

Auditoría del 2026-09-20/21, dimensión cripto; nombrado y no arreglado en la bitácora 0033.

`KeystoreSecureStore.get()` (`composeApp/src/androidMain/.../core/session/KeystoreSecureStore.kt:68`)
devuelve `null` cuando el archivo no existe **y también** cuando el descifrado falla
(`catch (_: GeneralSecurityException)`, línea 90). Un blob manipulado —`AEADBadTagException`, la etiqueta
de GCM que no cierra— es indistinguible de «nunca hubo entrada».

## Por qué importa

Falla **cerrado**, y eso está bien: sin sesión. Pero **nunca reporta `SECURE_STORE_UNREADABLE`**, que
es exactamente la señal que el §8.16 existe para llevar. Una manipulación del almacén cifrado es un
evento de seguridad, y hoy desaparece como si fuera un primer arranque.

## Por qué no es una edición

Distinguir los dos casos cambia la **dirección de fallo** de `SecureStore.get()` —o su firma— para
sus llamadores (`TokenStore.load()` y, sólo en Android, el reto del tier 2 en `BiometricUnlockGate`;
el sentinel no lee, sólo llama `wipe()`), y en esta familia las
direcciones de fallo no se re-derivan por intuición. **Necesita su ADR**, que decida: ¿`get()` lanza
ante un descifrado fallido y `probeSession` lo reporta (ya sabe hacerlo), o `get()` devuelve un tipo
que distinga «ausente» de «ilegible»?

## Qué NO hacer

- No convertir el fallo en un crash: hoy falla cerrado y la app sigue usable, y eso se conserva.
- No inventar un kind de evento nuevo sin nombre del lado de la plataforma: el contrato acepta un
  conjunto cerrado (ver `PlatformSecurityEventKind`), y un kind sin traducción no se envía.

## Cómo se verifica

Un test de device que escribe una entrada, corrompe un byte del archivo cifrado, y exige que la
lectura (a) no devuelva el valor, (b) no lance hacia la UI, y (c) produzca la señal. Con cebo: sin el
arreglo, (c) debe fallar.
