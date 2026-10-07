# 0001 · Re-verificar todo en el equipo nuevo

**Prioridad: la primera, antes de tocar código.** Un verde en la máquina vieja **no es** un verde en la
nueva. Este repo lo pagó dos veces en un mes —el framework Kotlin rancio (ADR-0028) y el enlace de iOS
que no se refrescaba (ADR-0030)— y LumeMed una tercera el mismo día (un `Info.plist` generado que no
honraba su ajuste; este repo no genera el suyo). Las tres veces **el ajuste estaba bien y el artefacto
no**.

## De dónde sale

El autor cambia de equipo el 2026-09-24. Lo de abajo corrió verde en el equipo viejo — el build, los
gates, el ensayo y los verificadores de iOS entre el 2026-09-20 y el 2026-09-21; **los verificadores de
Android, en la fecha de su tajada** (`verify-tier2-invalidation` y `verify-no-backup` el 2026-08-21,
`verify-install-sentinel` el 2026-09-07), no durante la auditoría. **Ese verde no viaja con `git clone`.**

## Lo que el equipo nuevo necesita

El detalle por herramienta vive en el vault (`LumeBrain/LumeMed/Stack Lume y KMP en equipo nuevo.md`),
no aquí. Lo mínimo para que lo de abajo corra: JDK 17 (el daemon está pineado:
`gradle/gradle-daemon-jvm.properties`), Android SDK con un AVD **con PIN de pantalla y una huella
enrolada** (`verify-tier2-invalidation.sh` la exige), **la app instalada** en ese emulador
(`./gradlew :androidApp:installDebug`, que `verify-no-backup.sh` necesita), **un simulador de iOS
booteado** (los dos verificadores de iOS y `verify-install-sentinel.sh`), Xcode con SDK de iOS 26 o
superior (CMP 1.11 lo exige), `python3` (los gates lo usan desde ADR-0029), y los repos hermanos en
`~/Documents/iOS/Projects/` — la constitución y varios gates los citan por ruta relativa.

## Qué se corre, en este orden

```bash
./gradlew --no-daemon detekt ktlintCheck build :composeApp:compileKotlinIosSimulatorArm64
for g in Scripts/check-*.sh; do bash "$g" || echo "FAIL $g"; done
sh Scripts/rehearse-gates.sh
python3 Scripts/numbered-docs-have-no-collisions.py
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
sh Scripts/verify-ios-link-freshness.sh     # 4 builds; debe decir PRESENT / ABSENT
sh Scripts/verify-ios-privacy-cover.sh      # necesita un simulador booteado; 3 builds
sh Scripts/verify-install-sentinel.sh
sh Scripts/verify-no-backup.sh              # emulador Android
sh Scripts/verify-tier2-invalidation.sh     # emulador Android
```

Y los instrumentados (`androidDeviceTest`), que en el equipo viejo **no** corrían con
`connectedAndroidDeviceTest` por un dispositivo fantasma de `adb` (`Ucamera001 offline`): se
instala el APK y se corre directo.

```bash
adb -s emulator-5554 shell locksettings set-pin 1234   # sin PIN el store se niega, y está bien
./gradlew :composeApp:assembleAndroidTest
adb -s emulator-5554 install -r -t composeApp/build/outputs/apk/androidTest/composeApp-androidTest.apk
adb -s emulator-5554 shell am instrument -w -r \
  -e class com.luismejias.lumemedlink.core.session.LogoutWipeOnDeviceTest,com.luismejias.lumemedlink.core.session.UnlockKeyContractTest,com.luismejias.lumemedlink.core.input.StructureExportTest \
  com.luismejias.lumemedlink.test/androidx.test.runner.AndroidJUnitRunner
```

**Con `-e class`, no el paquete entero**: `UnlockKeyInvalidationTest` tiene una fase B que **falla por
diseño** si no se enroló una huella nueva entre las dos fases — es el control de la invalidación. Esa
clase se corre sólo a través de `verify-tier2-invalidation.sh`, que orquesta las dos fases.

## Los valores de referencia del equipo viejo (2026-09-21)

Para comparar, **no para copiar como hechos**: 18 `check-*.sh` verdes · 39 cebos, todos rojos ·
130 tests iOS en el total, de los cuales 6 se saltan a propósito (corren 124; ver tarea 0010) /
124 en Android host · `LogoutWipeOnDeviceTest`
4/4 en emulador · `verify-ios-privacy-cover` con el snapshot plano en ~1–2 KB y el control en ~7–10 KB.

## Qué NO hacer

- **No dar por buena una diferencia de números sin entenderla.** Si un conteo baja, primero se
  sospecha del instrumento (un `--tests` que no matchea, un source set que no corrió), después del
  código. Este repo ya lleva al menos nueve verdes-por-razón-equivocada (dos incidentes distintos se
  llamaron «el octavo» en su momento, y ADR-0029 sumó dos gates ciegos después).
- **No correr `verify-ios-privacy-cover` y leer sólo el OK.** Si dice INCONCLUSIVE, el control no
  reprodujo el agujero y la corrida no probó nada.

## Advertencias específicas de la migración

- **Xcode cambió de versión en el equipo viejo mismo**: los builds del 2026-09-21 enlazaron contra el
  SDK de iOS 26.5; el 2026-09-24 la máquina ya tenía Xcode 27.0. El paso de CI `Select Xcode 26` busca
  `/Applications/Xcode_26*.app` a propósito — si el equipo nuevo trae sólo Xcode 27, localmente no
  importa, pero **los hechos de ciclo de vida de iOS (ADR-0031) se midieron sobre simuladores de iOS 26 y
  18.6**, no sobre 27: re-medirlos con `verify-ios-privacy-cover.sh` es parte de esta tarea.
- La memoria de Claude Code de este repo es un symlink
  (`~/.claude/projects/-Users-luis-mejias1-Documents-iOS-Projects-LumeMedLink/memory` →
  `LumeBrain/LumeMed/LumeMedLink/Memorias`). **El nombre de esa carpeta sale de la ruta absoluta del repo**: si
  el usuario o la ruta cambian en el equipo nuevo, el symlink hay que recrearlo con el nombre nuevo.

## Cierre

Se mueve a `DONE/` cuando todo lo de arriba corrió verde en el equipo nuevo **y** cada diferencia con
los valores de referencia quedó explicada por escrito en esta sección.

## Cierre — 2026-10-07

**Dónde:** la misma Mac corporativa del equipo viejo, con los repos movidos a `~/Documents/Lume/` y Xcode 27.0 (SDK de
iOS 27.0). El método —simuladores propios, `ANDROID_SERIAL`, el turno de build para los `xcodebuild` de los scripts— y
la historia de cada arreglo están en la bitácora 0036.

| Qué | Resultado |
| --- | --- |
| Gradle: detekt, ktlint, `build`, `compileKotlinIosSimulatorArm64` | verde · 124 en Android host · 130 en iOS, 6 saltados, 124 corren |
| Los 18 `check-*.sh`, y la mitad del de red sobre el manifiesto fusionado | verdes |
| `rehearse-gates.sh` | 40 cebos, todos rojos |
| `numbered-docs-have-no-collisions.py` | sin colisiones |
| Host iOS (`xcodebuild`, DerivedData propio fuera del repo) | BUILD SUCCEEDED |
| `verify-ios-link-freshness.sh` | PRESENT con la guarda / ABSENT sin ella |
| `verify-ios-privacy-cover.sh` | OK en iOS 27.0, 26.5 y 18.6 |
| `verify-install-sentinel.sh --with-live-control` | OK, después de arreglar el instrumento |
| `verify-no-backup.sh --with-live-control` | probado; el control vuelve a extraer por D2D |
| `verify-tier2-invalidation.sh` | probado, después de arreglar el instrumento |
| Instrumentados (las tres clases, con `am instrument`) | 12 de 12 |

**Cada diferencia con los valores de referencia:**

- **40 cebos, no 39.** El cuadragésimo llegó con la S0.3 el 2026-10-06 (bitácora 0035).
- **Los tamaños del snapshot del cover**: plano 2 225–2 316 B y control 12 434–12 471 B, contra «~1–2 KB» y «~7–10 KB».
  Los absolutos dependen del modelo de dispositivo: la referencia se midió en un iPhone 17 Pro y un iPad Pro, y acá en un
  iPhone 17 (27.0 y 26.5) y un iPhone 16 (18.6). El mismo modelo dio lo mismo en las tres versiones de iOS, y la
  proporción —que es lo que el script exige— se mantiene en unas cinco veces. **Los hechos de ciclo de vida de ADR-0031 se
  sostienen también en iOS 27.0**, que era la duda de esta tarea.
- **Instrumentados**: la referencia nombra sólo `LogoutWipeOnDeviceTest` 4/4; las tres clases son 4 + 4 + 4.
- **Tres verificadores no podían decir la verdad en este entorno**, y se arreglaron antes de creerles: el del sentinel
  (su ancla de inyección no existe desde ADR-0031, su `trap` dejaba el proyecto roto y tomaba el `.app` del DerivedData
  compartido), los dos de iOS que elegían «el primer simulador encendido», y el del tier 2, que manejaba el asistente de
  huella con `sleep` fijos. Bitácora 0036.
- **El AVD llegó sin PIN ni huella.** Se fijó el PIN `1234` y se enroló una huella; cada corrida de
  `verify-tier2-invalidation.sh` agrega otra, y el emulador admite cinco.
- **La memoria**: el symlink ya existe con el nombre que sale de la ruta nueva (`-Users-luis-mejias1-Documents-Lume-LumeMedLink`).
- **Los hermanos** no están en `~/Documents/iOS/Projects/` sino todos juntos en `~/Documents/Lume/`: las rutas relativas
  (`../LumeMed`, `../lumemed-cloud-platform`, `../LumeUIComposer`) resuelven igual.
- **El árbol de hoy compila**, incluida la S0.3 (`1d2ddd1`), que se escribió en una sesión sin red y nunca había compilado.

**Fuera, declarado:** API 26–29 no se midió (no hay imagen de esas versiones en esta máquina); un warning del enlazador
de iOS sobre un objeto de ICU compilado para iOS-simulator 18.5 frente al deployment target 16.0 quedó observado y sin
investigar.
