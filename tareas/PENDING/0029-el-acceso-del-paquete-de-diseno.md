# 0029 · El acceso del paquete de diseño, contra una simulación y sin sesión

> **Estado:** PENDING (abierta 2026-10-11) · ADR-0044, ADR-0045

## De dónde sale

El autor entregó el paquete de diseño completo en `tareas/PENDING/Handoff/` y decidió, el 2026-10-11:
construir primero **Auth**, con **las pantallas del paquete tal como vienen**, contra **simulaciones provisionales
fieles al diseño**, y dejar para después qué modelo de acceso del paciente gana. El backend publica otra forma
(enrolamiento mediado, TOTP, sin SMS, sin vincular médicos): ADR-0044 lo deja escrito.

## Qué se hace

1. **Textos (ADR-0045).** Recursos de Compose en `composeResources/values/` (español, base) y `values-en/`
   (inglés). Todo texto visible sale de `Res.string.*`, también los de las pantallas que se quedan
   (`unlock/`, la portada, el marcador de inicio). `AuthCopy` desaparece.
2. **Las interfaces** `core/auth/AccessGateway.kt` (ya escrita) y su guion, `ScriptedAccessGateway`, con el
   `Clock` inyectado para las cuentas regresivas.
3. **Las rutas y el coordinador** (`AccessRoute`, `AccessFlowModel`) dentro de un `ViewModel` del flujo, y el
   host que las dibuja a pantalla completa (el diseño no usa la tarjeta focal).
4. **Las pantallas**, cada una con su captura junto a la del diseño enviada al autor:
   - Paciente: 1b, 1c, 2a, 2b, 2c, 2d, 3a, 3b, 3c, 3d y los errores 1b-e1, 1b-e2, 1b-e3, 1c-e1, 2a-e1, 2a-e2,
     2b-e1, 2c-e1, 2c-e2.
   - Médico: 4a (sólo código), 4b, 4c, 4a-e1, 4b-e1.
   - 1a con el `LumeCarousel` del kit (entregado el 2026-10-11).
   - **Esperan:** 4a-e2 + el escáner QR (ADR de cámara).
5. **El final**: el flujo termina en un marcador «app principal (pendiente)», por rol. No hay sesión.

## El guion de la simulación (datos sintéticos)

| Se escribe | Resultado |
| --- | --- |
| Cualquier RUT válido + `Clave1234!` | Ingresa → 1c |
| Otra contraseña | 1b-e1 con los intentos que quedan; al quinto, 1b-e2 (15 min) |
| RUT `11.111.111-1` en cualquier paso | Sin conexión (1b-e3 / banner) |
| RUT `33.333.333-3` al ingresar | 1c → 1c-e1 (biometría no configurada) |
| RUT `22.222.222-2` al crear cuenta | 2a-e2 (RUT ya registrado) |
| Código SMS `123456` | Correcto; otro → 2c-e1, y al tercer error o a los 10 min → 2c-e2 |
| Contraseña nueva igual a `Clave1234!` | 3c rechaza (debe ser distinta) |
| Código de vínculo `K7Q2M9XA` (se acepta en minúsculas, con espacios o «·») | 4b → 4c |
| Código de vínculo `PEND1NG0` | 4b → 4b-e1 (en verificación) |
| Otro código de vínculo | 4a-e1 |

Nombres de ejemplo del propio paquete (ficticios). **Un dato real en el guion es un incidente** (§9).

## Avance (2026-10-11)

Hecho: recursos es/en, interfaces y guion, coordinador + `AccessFlowViewModel`, las 24 pantallas, cableado en `App()`
con la marca azul, 28 tests verdes en Android host y simulador iOS, `./gradlew build` verde, detekt y ktlint limpios.
Falta:
- **Capturas** lado a lado con el paquete, en Android y en iOS.
- **Tres verificadores en dispositivo que dependían del login borrado**, sin re-ejecutar y por eso sin parchear
  a ciegas (ADR-0029):
  - `Scripts/verify-accessibility-sensitivity.sh` inyecta en `features/auth/login/LoginScreen.kt`;
  - `Scripts/verify-popup-hardening.sh` inyecta en `AuthFlowHost.kt` y su sonda toca «Mostrar contraseña» en
    la pantalla de arranque, que ahora es la bienvenida;
  - `Scripts/verify-ios-link-freshness.sh` busca como marcador un literal de `AuthCopy.kt`, y los textos ahora
    son recursos que no viven en el binario de Kotlin: necesita otro marcador.

## Qué NO hacer

- No escribir UI propia en este repo: un faltante del kit se recomienda al autor y se pide al kit.
- No llamar `establishSession`, no tocar el `SecureStore`, no guardar nada en disco.
- No presentar el guion como el backend: su nombre y su KDoc dicen que simula un contrato que no existe.
- No reintroducir «Ahora no», «Seguir con contraseña» ni «Contáctanos» (ADR-0044, desviaciones 1 y 2).

## Cómo se verifica

- Tests del coordinador y de cada ViewModel contra el guion, con reloj falso: cada fila de la tabla.
- `./gradlew build` y los gates verdes (`check-input-surfaces`, `check-preauth-surfaces`, `check-data-boundary`).
- Captura de cada pantalla en Android y en iOS, lado a lado con el PNG del paquete, enviada al autor.
