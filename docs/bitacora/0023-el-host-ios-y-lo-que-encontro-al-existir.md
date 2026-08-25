# 0023 · El host iOS, y lo que encontró con sólo existir

**Fecha:** 2026-08-25 · **Origen:** «Movida 1» — construir el host que bloqueaba seis ítems de la
lista de fortificación. Es una **carpeta** en este mismo repo, al lado de `androidApp/`; no un
segundo proyecto de GitHub.

## Lo que costó levantarlo (cuatro tropiezos, todos con causa)

1. **`Unknown iOS simulator arch: 'x86_64'`.** El destino genérico de simulador pide arm64 **y**
   x86_64; este proyecto declara sólo `iosSimulatorArm64`, decisión ya tomada en
   `build.gradle.kts` (CMP no publica artefactos para el simulador Intel). Ahora está declarado
   también en Xcode con `EXCLUDED_ARCHS[sdk=iphonesimulator*]`, no descubierto cada vez.
2. **`cannot find 'MainViewControllerKt' in scope`.** Faltaba el `import`, y no se podía escribir:
   el módulo Swift se llamaba igual que el framework Kotlin. Un módulo no puede importar otro con
   su mismo nombre. Se separó (`PRODUCT_MODULE_NAME = LumeMedLinkHost`).
3. **Compose se niega a arrancar** sin `CADisableMinimumFrameDurationOnPhone` en el plist. No es
   una clave de seguridad; está porque el framework aborta sin ella, verificado por el crash.
4. **El emulador de Android, de paso,** quedó `RUNNING_LOCKED` tras un arranque en frío y todo test
   instrumentado moría con `not encryption aware`. Y un `Ucamera001` fantasma en `adb devices`
   rompía todo comando sin `-s`. Ambos anotados porque volverán.

## Lo que el host encontró en su PRIMER arranque

**Crasheaba al iniciar.** `App()` preguntaba por la sesión dentro de un `LaunchedEffect` y dejaba
escapar la excepción: el Keychain respondió error, `KeychainSecureStore.get` lanzó —correctamente— y
el proceso murió.

**La parte honesta:** el disparador que observé fue mi propia build **sin firmar**
(`CODE_SIGNING_ALLOWED=NO` ⇒ sin entitlements ⇒ `-34018`). Eso **no es** una condición de
producción, y lo verifiqué: con firma ad-hoc la app arranca sin el arreglo. Así que no puedo decir
«arreglé un crash de producción».

**Lo que sí puedo decir:** la *clase* es alcanzable, y el propio KDoc del store ya nombra un caso —
con `WhenPasscodeSetThisDeviceOnly`, el keychain DP responde `errSecNotAvailable` antes del primer
desbloqueo del dispositivo. Una app que muere en vez de mostrar la pantalla de inicio de sesión en
esa ventana es peor, no más segura.

**Y el arreglo se hizo asertable.** Salió de la composable a `app/probeSession`, con tres tests y
tres cebos: fallar abierto (rojo), tragarse la cancelación (rojo), reportar siempre (rojo). El
vocabulario `SECURE_STORE_UNREADABLE` existía desde F22/F23 **sin ningún llamador**; éste es el
llamador.

## El cover de privacidad iOS: sigue SIN verificar, y el simulador no puede

Intenté tres métodos:

| Método | Resultado |
| --- | --- |
| Snapshots `.ktx` en `Library/SplashBoard` | Tamaños **idénticos** con y sin cover |
| Compresibilidad (gzip) de esos snapshots | **Idéntica** |
| Screenshot del conmutador de apps | Tarjeta **vacía** en los dos casos |

Y el control decisivo: **desactivé los DOS covers** (el de host y el de Compose). El resultado no
cambió. Un control positivo que no se puede hacer fallar no prueba nada.

Causa probable: Compose renderiza por Metal y el snapshot del sistema no captura esa capa en el
simulador. **Este control necesita un dispositivo real.** Queda escrito para que la próxima sesión
no gaste las mismas horas.

Nota relacionada: el entitlement de keychain está escrito y, en una build ad-hoc, **no está en
efecto** — `codesign -d` devuelve un diccionario vacío porque no hay development team y
`$(AppIdentifierPrefix)` no resuelve. Un archivo que existe no es un control que corre.

## El punto ciego que yo mismo creé, cerrado en el mismo commit

Todos los gates escanean `composeApp/src androidApp/src`. En el instante en que apareció `iosApp/`,
era una carpeta llena de decisiones de seguridad que **ningún gate podía ver**.
`Scripts/check-ios-host.sh` la cubre: 12 cebos, todos rojos, más el control inverso de que un
comentario nombrando una API prohibida no dispara.

El más importante de esos 12: **un grupo de keychain compartido**. LumeMed está firmada por el mismo
equipo y guarda la ficha clínica; compartir grupo disolvería, en una línea de plist, la frontera por
la que existe este repositorio.
