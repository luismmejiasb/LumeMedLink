# 0007 · `core/session`: el tier 1 en ambos almacenes de hardware, y la sesión con single-flight

**Fecha:** 2026-08-21 · **Encargo:** la mitad sin UI de S1.1, autorizada por el autor mientras el
backend responde el pedido 0001.

## Qué existe ahora

- **`SecureStore`** (interface común) con sus dos implementaciones de plataforma — sin
  `expect/actual` para las clases (interface común + clase por source set es más simple y más
  testeable; el `expect/actual` quedó donde rinde: el reloj y la clase de accesibilidad):
  - **iOS `KeychainSecureStore`**: `kSecClassGenericPassword`, delete-then-add serializado tras
    mutex (el patrón del wrapper de LumeMed), `kSecUseDataProtectionKeychain` en toda query,
    jamás synchronizable, wipe SÓLO del servicio propio. La clase de accesibilidad va **por
    target compilado**: device = `WhenPasscodeSetThisDeviceOnly` (el piso de ADR-0005; quitar el
    passcode borra el ítem y eso es fail-closed aceptado), simulador =
    `AfterFirstUnlockThisDeviceOnly` — la desviación se compila fuera, no se decide en runtime.
  - **Android `KeystoreSecureStore`** (ADR-0009 nueva, cierra lo que ADR-0005 dejó al cableado):
    clave AES-256-GCM DENTRO de AndroidKeyStore cifrando archivos privados, cero dependencias
    nuevas. Las dos piezas del piso: `setUnlockedDeviceRequired(true)` (API 28+, degradación
    declarada en 26/27) y `put` que REHÚSA sin lock screen (`isDeviceSecure == false`).
- **`SessionManager`**: implementa el seam `TokenProvider` del stack. **Single-flight por
  construcción** (todo bajo un mutex: N llamadas concurrentes con access vencido → UN refresh,
  pinneado por test). Direcciones de fallo de la tabla familiar: refresh **rechazado** por el
  servidor → la sesión muere (memoria + disco); refresh que **lanza** (transporte) → estado
  intacto, se reintenta en la próxima llamada. `tokenWasRejected()` del stack fuerza refresh
  aunque el access luzca vigente. **Logout es contrato**: memoria + disco en una llamada, con
  test que lo pinnea (§8.13).
- **`InactivityLock`**: lógica pura con reloj inyectado. **Nace bloqueado** (fail closed) y
  `recordActivity` NO desbloquea — tocar la pantalla no es re-autenticación; sólo `unlock()`
  (el gate biométrico futuro, o el login) reabre la ventana. La amenaza §8.17 (teléfono
  compartido) es exactamente la que esa distinción cierra.
- **`TokenStore`**: el par serializado bajo UNA clave del store; entrada corrupta = `null` = sin
  sesión. `SessionTokens.toString()` redacta por tipo (test lo afirma).
- **Tests: 21 comunes × 2 targets** (single-flight, skew de expiración, rechazo, muerte por
  refresh rechazado, wipe, sobrevivir reinicio de proceso, fallo de transporte sin daño,
  inactividad completa) — todos verdes en Android host y simulador iOS.

## Las dos asimetrías de verificación, dichas en voz alta

1. **iOS**: el runner de tests de Kotlin/Native lanza un proceso SIN app host en el simulador y
   securityd no le concede keychain alguno — todo responde `-25291 errSecNotAvailable`, incluso
   el data protection keychain. El roundtrip real quedó como **spec ejecutable `@Ignore`** (sale
   como *skipped* en el reporte: el silencio del gate está etiquetado, no escondido). Se corre
   hosted cuando exista el shell; la semántica del passcode se verifica en device en S1.2.
2. **Android**: AndroidKeyStore no existe en el host test JVM y este repo rehúsa meter
   Robolectric para eso (§8.8). El store se ejercita en device con el shell; TODA la lógica
   encima (sesión, tokens, candado) está testeada contra fakes.

## Lo que queda de S1.1

El `RefreshClient` HTTP (espera el contrato del backend — pedido 0001), el tier 2 biométrico
(sus parámetros ya son contrato en ADR-0005; necesita Activity/UIViewController), el sentinel de
instalación iOS (espejo de ADR-0037 de LumeMed; se cablea en el arranque del shell), y la UI de
login (espera S0.3 o nace sin estilo).
