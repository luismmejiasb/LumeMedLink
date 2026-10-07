# 0046 — Las tareas que no esperaban al autor, y tres golpes en el camino

**Tipo:** `feature` + `fix` + `medición` · 2026-10-07

## Qué llega

El autor pidió construir todo lo pendiente que no necesitara una decisión suya, guiándose por la constitución. Se cerraron
ocho tareas: `0005`, `0009`, `0013`, `0015`, `0017`, `0018`, `0020`, `0024`. Cada una dice en su archivo qué se hizo y cómo se
verificó; aquí va lo que cruza entre ellas y lo que se aprendió.

- **S0.3: el kit, cableado.** Build compuesto con `../LumeUIComposer`, `org.jetbrains.compose.components` en el allowlist,
  la copia de recursos en `androidApp`, `LumeTheme` en la raíz, `SensitiveTextField` sobre los campos del kit (credencial →
  `LumePasswordField`; dato personal → `LumeTextField` con `Verbatim`), y las pantallas placeholder con `LumeContainer` +
  `LumeEmptyState`. En iOS se ve el ícono del kit con su tema (captura del simulador); en Android la app arranca sin
  `MissingResourceException`, con 93 dibujos del kit en el APK y los textos en el árbol de UI (FLAG_SECURE ennegrece la
  captura, así que se lee el árbol).
- **Una sola entrada de sesión** (ADR-0037, tareas `0005` y `0020`).
- **El estado del shell sobrevive a una rotación** (ADR-0039, tarea `0009`). Sin login no se puede rotar una sesión: no
  medido de punta a punta.
- **El lock emite sus eventos** (tarea `0018`). Cebo: sin los dos `emit`, tres tests rojos.
- **El Keychain de iOS corre en la suite** (tarea `0015`).
- **iOS cubre la grabación y AirPlay** (tarea `0017`), sin medir en dispositivo.
- **FCM acotado por artefacto** (ADR-0038, tarea `0024`).

## El hueco del gate de campos

`check-input-surfaces.sh` rechazaba cuatro campos del kit por nombre. El kit tiene nueve, y `LumePasswordField`,
`LumePhoneField`, `LumeMultilineField`, `LumeDocumentField` y `LumeDatePickerTextField` pasaban desde una pantalla. Medido con
cebo: verde con el gate viejo, rojo con el nuevo, que rechaza **por defecto** todo `Lume…Field(` / `Lume…Editor(` salvo los
dos que no reciben texto. Una lista de lo prohibido envejece el día que el kit agrega un campo; una lista de lo permitido no.

## El Keychain: dos causas, no una

Seis tests estaban ignorados porque «el runner no tiene keychain». Los códigos de error dijeron que eran dos cosas:
`--standalone` → `-25291`; en un simulador arrancado sin entitlements → `-34018`; con las dos → verde. Ahora el binario de
test lleva sus entitlements de simulador (grupo inventado, nunca el de la app) y la tarea corre en el simulador que nombra
`LUME_IOS_TEST_DEVICE`; sin él, la clase se excluye y la corrida lo dice. Se agregó un séptimo test, el único que ve un wipe
que deja de filtrar por servicio — `wipeClearsTheWholeService` sigue verde con ese defecto. Cebo hecho: rojo exactamente ése.

## La captura en el simulador

Con NSLogs temporales en el host, dos veces: `simctl io recordVideo` **no** dispara `capturedDidChange`. Los controles sí
aparecen (el log de arranque, y la cubierta al salir a Ajustes). El simulador no sirve para medir esta mitad.

## Golpe 1 — el build compuesto compila el árbol de trabajo del hermano

A mitad de sesión el build se cayó en `LumeSectionPicker` del kit: la sesión del kit estaba escribiendo `LumeTabBar`.
`settings.gradle.kts` acepta ahora `-Plume.kit.path`, y `Scripts/kit-snapshot.sh` exporta el último commit del kit con
`git archive` — sin tocar su árbol, su índice ni su `.git`. CI hace checkout del kit como hermano (con el token de la
familia, `LUME_ECOSYSTEM_TOKEN`, que este repo tiene que tener configurado).

## Golpe 2 — Gradle fallaba dentro de Xcode y el build salía verde

La primera captura de iOS mostró la pantalla **vieja**, sin ícono. Al agregar la dependencia del ViewModel faltó escribir el
lockfile; Gradle falló dentro del build phase, el script siguió (corre sin `-e`, y lo último que hace es comparar un sello)
y Xcode enlazó el framework anterior en verde. Tercera puerta de la misma obsolescencia silenciosa de ADR-0028 y ADR-0030.
Cerrado con `|| { echo "error: …"; exit 1; }` tras la llamada, **medido**: con el mismo lockfile faltante, xcodebuild sale
65 con «refusing to link a stale one». Gate en `check-ios-host.sh` y cebo.

## Golpe 3 — el primer build tras un cambio de Kotlin falla al firmar

Con el Kotlin nuevo y los objetos Swift iguales, Xcode planificó sin enlazar, el script borró el `.debug.dylib` (el relink
forzado de ADR-0030) y `CodeSign` no lo encontró. El segundo build enlaza y sale verde. Falla ruidoso, no silencioso; queda
como tarea `0025`.

## Verificación

`detekt`, `ktlint`, `build`, iOS compilado y probado en los dos targets (iOS con el Keychain alojado), todos los gates,
el ensayo de cebos y los instrumentados en el emulador — la corrida final está en el commit. Nada se empujó.

## Lo que queda para el autor

`0016`, `0019`, `0021` y `0022` (direcciones de falla y posturas que la constitución deja al autor), `0011` (la mitad
positiva de los deep links espera un dominio de producción), `0012` (espera el contrato del backend), y el secreto de CI
para el checkout del kit.

Técnicas y abiertas, sin decisión del autor: `0014` (medir los popups del kit contra el endurecimiento de ventana: necesita
un arnés de medición con una ventana superpuesta y un volcado de autofill, su propia tajada) y `0025`.
