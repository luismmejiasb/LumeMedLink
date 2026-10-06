# 0013 · S0.3 — cablear LumeUIComposer como el design system de la app

> **Estado:** PENDING · Abierta el 2026-10-06, con la decisión del autor de consumir el kit (ADR-0033). **Cero datos
> personales** en todo lo que toca.
>
> **Bloqueo:** ninguno para empezar. Partes concretas esperan tareas del kit, nombradas abajo.

## De dónde sale

- La auditoría de encaje kit ↔ app, que vive en el kit: `../LumeUIComposer/docs/audits/lumemedlink-fit-2026-10-06.md`.
  Léela entera antes de empezar: trae la evidencia de cada punto, con `archivo:línea` a los dos lados.
- ADR-0033 (la decisión) y la enmienda de ADR-0013 del mismo día (copiar dentro de campos; los campos del kit pasan
  por `SensitiveTextField`).

## Lo que ya está

- **El gate.** `Scripts/check-input-surfaces.sh` rechaza `LumeTextField`, `LumeSearchField`, `LumeOTPField` y
  `LumeRichTextEditor` fuera de `core/input/`. Antes no los veía (su patrón de campo crudo no tiene frontera de palabra
  entre `Lume` y `TextField`): medido con un cebo que pasaba en verde, y el cebo quedó en `rehearse-gates.sh`. Control
  negativo hecho: un campo del kit dentro de `core/input/` pasa.

## Lo que hay que hacer, en este orden

1. **Re-verificar en la máquina** (`tareas/PENDING/0001`) si no se hizo: un verde de otra máquina no viaja con el clon.
2. **El build compuesto.** `includeBuild("../LumeUIComposer")` en `settings.gradle.kts`, con la sustitución de
   dependencia que haga falta, y `:composeApp` dependiendo del módulo `:lumeuicomposer`. Las versiones ya coinciden
   (Kotlin, Compose MP, AGP, min/compile SDK): si un bump llega a un lado, llega al otro en el mismo cambio.
3. **El allowlist.** Una línea exacta, `org.jetbrains.compose.components`, en el mismo commit que el lockfile que la trae
   por primera vez. Si aparece otro grupo nuevo, se para y se pregunta: ADR-0033 admite sólo ése.
4. **Los recursos en Android.** Sin una tarea de copia en `androidApp`, el primer `LumeIcon.painter()` lanza
   `MissingResourceException` (AGP 9.2 no empaqueta assets en una librería KMP). La implementación de referencia es
   `../LumeUIComposer/sampleAndroid/build.gradle.kts`. iOS no la necesita (`embedAndSignAppleFrameworkForXcode`).
5. **`LumeTheme` en la raíz** (`app/`), una vez.
6. **`SensitiveTextField` sobre los campos del kit.** `CREDENTIAL` → el campo de contraseña del kit (ya trae el
   autocompletado de credenciales); correo y teléfono → los teclados `Email` y `Phone` del kit, que ya van sin
   autocorrector ni mayúsculas. **El texto libre personal (nombre, dirección) se queda en el `BasicTextField` propio**
   hasta que el kit tenga su teclado *verbatim* (`../LumeUIComposer/tareas/PENDING/0005`): el `Default` del kit pide
   mayúscula de oración y el autocorrector del sistema, que es justo lo que ADR-0013 cierra.
7. **Las pantallas placeholder de S1.1** (login, bloqueo, home) vestidas con el kit, dentro de `LumeContainer`.

## Lo que espera del kit, y en qué tarea suya

| Necesidad de esta app | Tarea del kit |
| --- | --- |
| Teclado verbatim para nombre y dirección | `0005` |
| Barra de pestañas del shell (Agenda / Contactos / Perfil) | `0006` |
| Fila con estado **y** ••• (una cita), y el callout que llena su región | `0007` |
| Columna de fecha que cabe una fecha chilena; el sello sube sobre el título en letra grande | `0002` (P7) |
| Un campo no recorta lo escrito (perfil, dirección) | `0003` |
| Si `LumeContainer` desplaza por defecto — **bloqueada en el autor** | `0010` |

## Qué NO hacer

- **No construir un módulo `designkit`.** ADR-0033 lo descarta.
- **No llamar un campo del kit desde una pantalla**, ni aflojar el gate para que pase: el campo va por `core/input/`.
- **No construir una barra de pestañas en la app** mientras el kit no tenga la suya: sería un componente viviendo en
  una pantalla (§5). Si la pantalla la necesita antes, se pide al kit, no se improvisa.
- **No corregir un componente del kit desde afuera** (un `.padding`, un `Modifier.background` encima): si no hace lo que
  el diseño pide, es una petición al kit.
- No copiar al portapapeles **datos mostrados**: la enmienda de ADR-0013 sólo abre los campos editables.

## Cómo se verifica

- `./gradlew build` y `compileKotlinIosSimulatorArm64` verdes; todos los gates de `Scripts/` y
  `Scripts/rehearse-gates.sh` con todos sus cebos rojos.
- **En el emulador:** un ícono del kit se dibuja (prueba de la copia de recursos) y la pantalla de login se ve con el
  tema. En el simulador de iOS, lo mismo, con el enlace fresco (ADR-0030).
- Con `check-dependency-allowlist.sh` verde y el grupo nuevo presente en el lockfile.

## Lo que queda fuera y por qué

- `no_hardcoded_style` como gate: posible ahora que las pantallas se escriben contra tokens del kit, pero es su propia
  tajada (el modelo es `../LumeUIComposer/Scripts/lint-layout.sh`).
- Los popups del kit frente al endurecimiento de ventana: `0014`.
