# 0004 · El espejo de LumeNetworking: `core/networking` con sus tests en los dos lados

**Fecha:** 2026-08-20 · **Encargo:** tercer paso del handoff — la doctrina de LumeNetworking
re-implementada sobre Ktor (ADR-0004), con tests de contrato en `commonTest` corriendo en Android
host y en simulador iOS. `lume-security` cargado antes del primer edit (§0.0: el slice toca el lado
iOS del target).

## Lo que existe ahora (todo `internal`; `public` sigue siendo sólo el borde del módulo)

- **`shared/AppError`** — la taxonomía única que cruza capas: `AuthExpired` (401) / `Validation`
  (400/422, con el `type` opaco del problema) / `NotFound` (404 — que el backend usa para «no es
  tuyo» tanto como para «no existe», su ADR-0009) / `Retryable` (408/425/429/5xx) / `Unexpected`.
  **No transporta prosa del servidor**: `detail` y `title` mueren en la capa de red.
- **`core/networking/lumeHttpClient`** — la única fábrica de `HttpClient`: https-only **fail-closed
  dos veces** (base `http://` lanza al construir; URL absoluta `http://` lanza por-request),
  `followRedirects = false` (un 3xx es anomalía que se reporta, no se persigue), timeouts
  explícitos, retry acotado **sólo GET** (2 reintentos; POST jamás — T13: sin idempotencia no se
  inventa), rid de correlación por request lógico (`X-Lume-Request-Id`), y `HttpResponseValidator`
  que convierte todo non-2xx en `AppErrorException` ya mapeada.
- **Seams**: `TokenProvider` (el bearer se adjunta; `tokenWasRejected()` en 401 — el refresh
  single-flight es de S1.1, aquí nace la costura) y `NetworkLogSink` (recibe una `NetworkLogEntry`
  de **cuatro campos**: método/path/status/rid — redactado POR TIPO: ni headers, ni body, ni
  query; el path va sin query por construcción).
- **Engines** por `expect/actual` en `core/` (ADR-0008): Android → OkHttp con
  `ConnectionSpec` TLS 1.2/1.3 explícito y sin spec cleartext; iOS → Darwin (el piso TLS es ATS,
  sin excepciones — S1.2 lo verifica en device).
- **13 tests × 2 targets** (`testAndroidHostTest` + `iosSimulatorArm64Test`, ambos `failures=0`):
  http lanza al construir · el 302 termina la llamada (una sola request al engine) · 401→AuthExpired
  y el provider notificado · 422 problem+json→Validation **y el `detail` no aparece ni en el error
  ni en el message** · 404→NotFound · 500→Retryable · GET reintenta acotado (3 requests exactas) y
  para al primer éxito · POST nunca reintenta · bearer adjunto y el log sin token/query/rut · sin
  provider no viaja Authorization · rids frescos por request · problem+json malformado degrada a
  mapeo por status.

## Lo que los gates hicieron durante el slice (funcionando, no estorbando)

- **El allowlist paró el build dos veces, bien**: primero exigió admitir `io.ktor` +
  `com.squareup.okhttp3/okio` conscientemente (entraron con su comentario citando ADR-0004);
  después el **lock enforcement** de `:androidApp` rechazó resolver Ktor que su lockfile no
  conocía — el relock es un acto deliberado, no un side effect.
- **detekt me cazó a mí**: el primer parser tenía `catch (Exception)` — exactamente
  `TooGenericExceptionCaught`. Quedó en dos catches específicos (`SerializationException`,
  `IllegalArgumentException`).
- **Un test fabricó JSON ilegal y el parser hizo lo correcto**: el fixture del 422 llevaba `\d` en
  el `detail` (escape inválido en JSON estricto) y el parser degradó a mapeo por status — el test
  falló por su fixture, no por el código. Kotlin tolerante habría escondido esto.

## Deuda declarada del slice

- Las **excepciones de transporte** (timeout, IO) salen crudas del stack — sólo los status HTTP
  mapean a `AppError`. Se envuelven cuando el primer use case las necesite tipadas (YAGNI hoy).
- ~~El log registra **la respuesta final** de una llamada reintentada, no cada intento.~~ **FALSO, corregido el 2026-08-21 (F12/ADR-0016): registra CADA intento** — medido, tres entradas para un GET reintentado dos veces. Se conserva ese comportamiento (es el más útil) y ahora un test fija el número, que es contra lo que un lector presupuesta volumen de log y superficie de datos.
- `networkSecurityConfig` (Android) y la verificación ATS en device son de **S1.2**, como el
  WORKPLAN ya decía.
- El nombre del header de correlación (`X-Lume-Request-Id`) es decisión local: si el contrato
  define otro al cablear el cliente generado, se alinea ahí.
