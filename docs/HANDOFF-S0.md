# Handoff · S0 + el espejo de red — para la primera sesión de código de este repo

> Escrito el 2026-08-20 por la sesión de LumeMed que creó este repo, al pasar el testigo: el autor
> decidió que el código de LumeMedLink se construye en sesiones DE este repo. Todo lo de abajo está
> **verificado en esta máquina hoy** — no es suposición. Léelo junto con `CLAUDE.md` (manda ella),
> el tablero (`../lumemed-cloud-platform/docs/ECOSYSTEM-STATUS.md` §3.1 ya tiene nuestra fila) y el
> vault (`~/Documents/LumeBrain/LumeMed/`).

## El encargo, en orden (del autor, 2026-08-20)

**S0.1 (esqueleto KMP) → S0.2 (gates, ensayados con cebo) → el espejo de LumeNetworking en
`core/networking`** (ADR-0004). El autor preguntó si había cimiento previo y la respuesta fue este
orden — código antes que gates es cómo una constitución se queda en prosa. **S0.3 (design system) NO
se toca**: espera el veredicto del autor sobre LumeUIComposer (WORKPLAN).

Decisión ya tomada por el autor (no re-litigar): el espejo de red vive **dentro de la app**, no en
repo propio ni en el repo Swift de LumeNetworking — se gradúa a repo propio cuando exista un segundo
consumidor Kotlin. Razones registradas en la bitácora de LumeMed (0119, si existe) y en la
conversación del 2026-08-20: SPM por versión exige manifest en la raíz y tags semver del repo entero;
dos constituciones en un repo es la ambigüedad que la familia evita; entre Swift y Kotlin se
comparten cero líneas — sólo doctrina, que son documentos.

## Toolchain — verificado hoy, no adivinado

- **`../LumeUIComposer` compila en esta máquina** (`./gradlew help` → verde). Es tu donante de
  infraestructura: copia su **wrapper** (`gradlew`, `gradlew.bat`, `gradle/wrapper/` — Gradle 9.7.1
  pinneado por `distributionSha256Sum`) y espeja su `gradle.properties`.
- **JDK**: el default de la máquina es 26; el gemelo pinnea 17 **en el build** con
  `jvmToolchain(libs.versions.jvmTarget.get().toInt())` — copia esa disciplina, jamás dependas del
  PATH (su comentario lo explica: «it built locally» y «it built in CI» deben ser la misma frase).
- **Android SDK**: presente en `~/Library/Android/sdk`.

## Versiones — resueltas, con su procedencia

Del catálogo del gemelo (**resueltas contra Maven/Google en esta máquina el 2026-08-20**, sus
comentarios explican cada pin): Kotlin **2.4.10** · Compose Multiplatform **1.11.1** · AGP **9.2.1**
(⚠️ techo del IDE: Android Studio del autor rechaza 9.3.1 — no subir sin abrir el IDE) · detekt
**1.23.8** · ktlint plugin **14.2.0** · minSdk **26** · compile/targetSdk **36** · jvmTarget **17**.

Resueltas por esta sesión contra Maven Central (2026-08-20, `<release>` de maven-metadata):
Ktor **3.5.2** · kotlinx-coroutines **1.11.0** · kotlinx-serialization-json **1.11.0**.

## Las minas de AGP 9 que el gemelo ya pisó (sus comentarios son la fuente)

1. Un módulo KMP usa **`com.android.kotlin.multiplatform.library`** con el bloque `android {}`
   **DENTRO de `kotlin {}`** — `com.android.library` + `android {}` top-level rehúsa cargar junto a
   KMP desde AGP 9.
2. El shell corrible Android es un **módulo app aparte** (plain `com.android.application`) que
   depende del módulo KMP — el plugin de aplicación no convive con KMP.
3. En ese módulo app, **NO** apliques `org.jetbrains.kotlin.android`: desde AGP 9 es error duro, el
   soporte Kotlin viene integrado.
4. **`withHostTest {}`** dentro del bloque `android {}` del módulo KMP, o `commonTest` compila para
   iOS y **se salta Android en silencio**.
5. **Dos targets iOS, no tres**: `iosArm64()` + `iosSimulatorArm64()`. CMP 1.11.1 no publica
   artefactos para `iosX64` y declararlo rompe la resolución.
6. Dependencias de Compose **por coordenada** (`org.jetbrains.compose.runtime:runtime`…): los
   accessors `compose.runtime` están deprecados en 1.11 y con `allWarningsAsErrors` eso es build
   roto, no aviso.
7. `explicitApi()` + `allWarningsAsErrors.set(true)`: alineados con §3 y §2.9 de nuestra
   constitución. Plantillas completas: `../LumeUIComposer/lumeuicomposer/build.gradle.kts` (módulo
   KMP) y `../LumeUIComposer/spikeAndroid/build.gradle.kts` (shell app + manifest al lado).

## S0.1 — el esqueleto (DoD del WORKPLAN)

`settings.gradle.kts` (root `LumeMedLink`; módulos `:composeApp`, `:androidApp` — sin módulos
vacíos: el árbol crece con los slices, regla del gemelo §14) · catálogo con TODO lo de arriba ·
namespace/applicationId **`com.luismejias.lumemedlink`** (espejo de `com.luismejias.lumemed`) ·
árbol ADR-0008 en `commonMain` (`app/`, `features/`, `shared/`, `core/` — nacen las carpetas que el
primer código llena, no antes) · framework iOS `baseName = "LumeMedLink"`, `isStatic = true`.
**DoD**: `./gradlew build` verde (incluye Android) **y** `./gradlew :composeApp:compileKotlinIosSimulatorArm64` verde.

## S0.2 — los gates (DoD: cada uno ensayado con cebo)

detekt + ktlint pinneados del catálogo. Las reglas custom de la constitución §9, con honestidad
sobre el mecanismo:

| Regla | Mecanismo hoy |
| --- | --- |
| `no_globalscope` | detekt `coroutines.GlobalCoroutineUsage` (built-in) |
| `no_raw_networking` | detekt `ForbiddenImport` (okhttp3.*, java.net.HttpURLConnection, io.ktor.client.HttpClient fuera de `core/networking` — la excepción por path se configura en el yml) |
| `secrets_gate` | `ForbiddenMethodCall` sobre `getSharedPreferences` + `ForbiddenImport` de DataStore fuera de `core/` |
| `no_document_delivery` | `ForbiddenImport`/`ForbiddenMethodCall` sobre share intents (`Intent.ACTION_SEND`, `FileProvider`) — afinar con cebo |
| `no_mutable_object` / `no_hardcoded_style` | **[manual] todavía** — exigen regla detekt custom (módulo con detekt-api) o esperar al design system; se declaran como deuda en el yml y en §13, NO se fingen |

**Ensayo con cebo obligatorio** (la lección fundante, LumeMed §9): archivo con `GlobalScope.launch`,
`import okhttp3.OkHttpClient` y un `getSharedPreferences` → detekt debe ponerse rojo → se borra el
cebo. Un gate que no se vio fallar no existe.

## El espejo de red — `core/networking` (ADR-0004, la doctrina de LumeNetworking)

Piezas, todas en `commonMain` salvo los engines:

- **`LumeHttpStack`**: fábrica de UN `HttpClient` — base URL **https-only fail-closed** (un `http://`
  lanza al construir, no al llamar), `followRedirects = false`, timeouts explícitos,
  ContentNegotiation JSON, **retry acotado SOLO en GET** (jamás POST/PATCH — la regla de
  LumeNetworking; `Idempotency-Key` es asunto app+backend), header de correlación (rid UUID por
  request, espejo del formato de log de LumeMed: `GET path status=… rid=…`).
- **Logging redactado**: seam `NetworkLogSink` que recibe método/path/status/rid y **nada más** —
  ni headers, ni body, ni query. El test lo afirma sobre la salida capturada.
- **RFC 9457**: parser de `application/problem+json` → taxonomía `AppError` en `shared/` (espejo
  LumeMed §7.2): `authExpired` (401 → señal de re-auth) / `retryable` / `validation` / mensaje
  seguro al usuario — el `detail` del servidor JAMÁS se muestra crudo (amenaza 2: alguien mira la
  pantalla).
- **Seam `TokenProvider`** (interface: `suspend token(): String?` + `tokenWasRejected`): el plugin
  adjunta el bearer; el refresh single-flight es del slice de sesión (S1.1), el seam nace ahora.
- **Engines** por `expect/actual` en `core/` (jamás en features, ADR-0008): Android → OkHttp engine
  con TLS ≥ 1.2 explícito; iOS → Darwin (ATS manda).
- **Tests en `commonTest` con MockEngine** (corren en Android host test —gracias a `withHostTest`—
  y en `iosSimulatorArm64Test`): https-only lanza · redirect no se sigue · 401→authExpired,
  422→validation, 500→retryable · GET reintenta acotado y POST nunca · el log no contiene token ni
  query · el `detail` de un problem+json no llega al mensaje de usuario.

## Reglas de siempre (constitución §11)

Commits `Luis Mejias <luismmejiasb@gmail.com>`, conventional commits en inglés, **sin trailers de
IA**, iterativos (no acumular). Docs (bitácora/PROGRESS/WORKPLAN) **en el mismo cambio**. **Jamás
push** — el autor pushea (los 12 commits actuales están locales). Español venezolano con el autor,
tuteo. El tablero ya tiene nuestra fila (§3.1) — si este trabajo cambia el estado declarado en
`PROGRESS.md`, el tablero cita línea: avisar al autor, no editarlo sin su autorización.

## Al cerrar

Bitácora del slice + `PROGRESS.md` (S0 deja de estar ⬜) + WORKPLAN si se desvió + nota al vault si
hubo golpe nuevo con causa raíz. Y decir en voz alta lo que quedó sin verificar.
