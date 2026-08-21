# 0009 · El shell mínimo, y F1 verificado en un dispositivo de verdad

**Fecha:** 2026-08-21 · **Encargo:** el autor eligió construir el shell mínimo antes de seguir con
Fase A, para que los slices que lo esperan (F4, F7, F17) tengan dónde enchufarse y todo se pueda
**verificar en pantalla** en vez de acumular IOUs.

## El shell, y por qué es "mínimo" y no un placeholder vacío

- **Máquina de navegación real**: `resolveDestination(hasSession, locked)` → `Login` / `Locked` /
  `Home`. Es una función pura, toda la política de navegación en un solo lugar testeable (3 tests ×
  2 targets). El orden importa y está pinneado: sin sesión gana sobre bloqueado (no te pueden
  bloquear de una sesión que no existe), y bloqueado gana sobre home.
- **Store real cableado**: `rememberSecureStore()` es un `expect/actual @Composable` en
  `core/session` — la construcción del store de plataforma (Keystore con `LocalContext` en Android,
  Keychain en iOS) por el punto idiomático de CMP. Es la única vez que `core/session` toca Compose,
  y es un seam de construcción, no UI. `App()` arma el `SessionManager` real encima.
- **`UnwiredRefreshClient`**: el `RefreshClient` real (HTTP) es un slice cercano ahora que el
  backend respondió el pedido 0001 (`ADR-0036 del backend`). Hasta entonces devuelve `null`, que el
  `SessionManager` lee como "refresh imposible → la sesión termina". Fail-closed: un shell sin flujo
  de auth jamás debe comportarse como si una sesión pudiera continuar sola.
- **Screens placeholder sin estilo**: `BasicText` a propósito — S0.3 (design system) está diferido y
  un placeholder con estilo sería el primer literal prohibido. Llevan estructura, no apariencia.
- `locked` se queda en `false`: engancharlo sin el desbloqueo biométrico (F4) atraparía al usuario
  en una pantalla bloqueada sin salida. El destino existe y está testeado; simplemente no se entra
  todavía.

## F1 verificado en un dispositivo, no solo declarado

Aproveché el shell corrible para **probar F1 de verdad** en el emulador (AVD `Pixel_9`):

1. Instalé el APK, lancé la app, confirmé `MainActivity` al frente.
2. **La trampa que casi me engaña a mí:** `adb exec-out screencap` es un proceso privilegiado. En
   Android viejo se saltaba FLAG_SECURE; el screenshot salió negro y **por poco lo reporto como
   prueba** — pero un negro también puede ser "la app no renderizó". No es prueba por sí solo.
3. **El A/B que sí prueba**: capturé el launcher (no-seguro) → luma media **82.5**, 2.6M píxeles con
   contenido. Capturé mi app al frente → **luma 0.0, cero píxeles con contenido**. Como el
   `screencap` demostrablemente funciona en el launcher, el negro puro de mi app confirmada al
   frente es **FLAG_SECURE bloqueando la captura**. Verificación real de plataforma.

Honestidad sobre el alcance de la prueba: precisamente porque FLAG_SECURE funciona, **no pude
capturar el contenido del Login** para verlo — el pipeline de render (build + install + foreground
sin crash + el test de navegación) es la evidencia de que renderiza. Y la miniatura de recientes /
el screenshot de usuario comparten el mismo mecanismo; el `screencap` privilegiado es el caso más
fuerte, y quedó bloqueado.

## Lo que el shell desbloquea

- **F4** (bloqueo por inactividad + biométrico): el destino `Locked` ya existe; falta el gate
  biométrico sobre material de clave, que necesita `BiometricPrompt` (Activity) / `LAContext`.
- **La UI de login** (S1.1): la pantalla existe como placeholder; el flujo real espera el cliente
  HTTP y el flujo de Identity Platform (backend ya respondió).
- **F17** (deep links): las intent-filters tienen dónde vivir.

## Lo que sigue sin existir, declarado

- **El host iOS (proyecto Xcode)**: el framework compila y `MainViewController()` es el entry, pero
  no hay proyecto Xcode que lo embeba, así que la app **no corre en iOS** todavía. El cover iOS de
  host (F1) y el sentinel de instalación (F7) lo esperan. Construirlo a ciegas (sin abrir Xcode) es
  riesgoso; es su propio slice, con el autor.
- La verificación en device fue **solo Android**; iOS espera su host.
