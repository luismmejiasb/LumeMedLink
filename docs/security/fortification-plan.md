# Plan de fortificación — LumeMedLink

> El índice maestro del programa de seguridad. Aprobado por el autor el 2026-08-21. Cada slice es
> una **sesión completa**: verifica lo que exista, fortifica lo que falte, y deja un **cimiento que
> no se pudre** — un ADR, un gate visto rojo con cebo, tests, y su entrada en el threat model.
>
> **No promete invulnerabilidad** — ninguna app la tiene, y afirmarla violaría §14. Promete subir el
> costo de cada ataque y encoger la superficie, con cada afirmación respaldada por un test o un gate.
> Se prioriza contra `threat-model.md` (T1–T6 + la violación de frontera, la clase sobre Crítico).
>
> Estado: ⬜ sin empezar · 🟡 en curso · ✅ cerrado · 🔒 bloqueado (shell/backend).

## Fase A — El teléfono en manos equivocadas (T1, T2, T3)

| # | Slice | Estado | Nota |
| --- | --- | --- | --- |
| F1 | Captura de pantalla y multitarea (FLAG_SECURE / cover iOS / tapjacking) | 🟡 2026-08-21 | Núcleo hecho y **verificado en device (Android)**: `screencap` da negro puro sobre la app, normal sobre el launcher (bitácora 0009). Cover iOS robusto (host window) diferido al host Xcode. ADR-0010, bitácora 0008. |
| F2 | Superficies pre-auth (notificaciones sin contenido, widgets, pantalla bloqueada) | ✅ 2026-08-21 | Ninguna superficie existe (decisión, ADR-0012) + `shared/PushSignal` **sin campo de texto** (la fuga es imposible de escribir) + gate `check-preauth-surfaces.sh` con 8 cebos + 6 tests. **Hallazgo: el push de Android choca con el denylist propio (FCM = Firebase) — decisión abierta del autor.** Bitácora 0011. |
| F3 | Portapapeles y teclado | ✅ 2026-08-21 | La app **no ofrece copiar** (gate rechaza clipboard y `SelectionContainer`) + **una sola pieza de entrada** `core/input/SensitiveTextField`, con endurecimiento **por propósito** (credencial ≠ dato personal: apagar autofill en una contraseña bajaría la seguridad). Gate `check-input-surfaces.sh` con 7 cebos + 5 tests. Asimetrías declaradas: Android no puede vetar un IME ni expirar un clip; el veto iOS espera el host. ADR-0013, bitácora 0013. |
| F4 | Bloqueo por inactividad + gate biométrico anclado a clave (tier 2) | 🟡 2026-08-21 | **Mecanismo completo**: EC en Keystore firmando un reto (Android) / Keychain `.biometryCurrentSet` (iOS), política en `SessionLock` con 13 tests × 2 targets, gate `check-biometric-contract.sh` **ensayado con 8 cebos**, y **4 tests instrumentados verdes en Android real** confirmando vía `KeyInfo` que las propiedades quedaron aplicadas. ADR-0011, bitácora 0010. **Falta el end-to-end**: sin login la pantalla `Locked` no se alcanza; iOS espera su host. |

**Fase A cerrada el 2026-08-21** (F1, F2, F3 y el mecanismo de F4). Colas atadas al host iOS: el cover de ventana
(F1) y el veto de teclados (F3). Cola atada al login: el end-to-end de F4.

## Fase B — Lo que sobrevive (T3, T4)

| # | Slice | Estado | Nota |
| --- | --- | --- | --- |
| F5 | Logout = borrado total, verificado | 🟡 | Contrato construido en `core/session`; falta elevarlo a prueba de integración. |
| F6 | Sin backup / sin sincronización | 🟡 | `allowBackup=false` ya puesto; falta `dataExtractionRules` + verificación en device. |
| F7 | Sentinel de instalación iOS (secretos heredados) | 🔒 shell | Se cablea en el arranque del shell iOS. |
| F8 | Caché en reposo cifrada y purgable | ⬜ | No hay caché todavía; nace con su primera lectura. |

## Fase C — Identidad y sesión (T5, escalada)

| # | Slice | Estado | Nota |
| --- | --- | --- | --- |
| F9 | El token que no puede abrir la ficha | 🟡 2026-08-21 | **Decidido: `ADR-0036` del backend, aceptada** (audiencia por app, aplicada en su `AuthGuard` + handshake WS, deny-by-default, fuera de alcance = 404 sin anunciar). **Falta que lo construyan**; verificar entonces. Su advertencia nº1: el token clasifica al CLIENTE, la base sigue decidiendo el PERMISO — el alcance sólo resta, nunca suma. |
| F10 | Tokens cortos, refresh que rota, sin replay | 🟡 | Single-flight construido; falta el `RefreshClient` HTTP real. **Advertencia nº2 del backend (2026-08-21): el `≤15 min` es doctrina, NO configuración** — sin proyecto GCP no hay política de token en ninguna parte y hoy corre el token de dev. Este slice no puede *verificar* la expiración contra nada real hasta que exista esa política; se verifica el comportamiento del cliente y se declara el resto. |
| F11 | Login y MFA endurecidos | 🔒 backend/shell | Depende del flujo de auth real. |

## Fase D — Red y transporte (T5)

| # | Slice | Estado | Nota |
| --- | --- | --- | --- |
| F12 | Stack de red endurecido, verificado + cleartext negado en el sistema | 🟡 | Stack construido; falta `networkSecurityConfig`/ATS y su verificación. |
| F13 | Nada sensible en URLs; pinning re-evaluado | ⬜ | Gate anti-datos-en-URL; decisión de pinning documentada. |

## Fase E — Contrato y autorización (T5, frontera)

| # | Slice | Estado | Nota |
| --- | --- | --- | --- |
| F14 | La frontera de datos como gate ejecutable | ⬜ **prioridad subida** | Poda de DTOs mixtos; nace con la primera lectura de contrato. **CONFIRMADO por el backend con evidencia de su código (advertencia nº3, 2026-08-21): `Patient` lleva `bloodType` cifrado y `careDirective` en la MISMA fila que correo y teléfono.** O sea: una lista de operaciones permitidas admite el **cuerpo entero** de la respuesta — lo no-clínico necesita **proyección propia** en el contrato, no un permiso sobre la entidad. Eso convierte a F14 en un **pedido de contrato**, no sólo en un gate de cliente. |
| F15 | Restricción de tratamiento en rutas tenant-scoped (T11) | 🔒 backend | Se resuelve en el pedido de contrato de agenda/contactos. |
| F16 | IDOR (404-no-403), reagendar atómico (T7), idempotencia (T13) | 🔒 backend | Nace con S1.3/S1.4. |

## Fase F — Entrada, contenido y enlaces (T6)

| # | Slice | Estado | Nota |
| --- | --- | --- | --- |
| F17 | Deep links / universal links seguros | 🔒 shell | Sin custom scheme; link concede navegación, no acceso. |
| F18 | Contenido no confiable no rompe la app | 🟡 | Decodificación tolerante empezada en el stack; falta bytes de imagen. |
| F19 | Cero entrega de documentos | 🟡 | Gate `no_document_delivery` construido; elevar a prueba de ausencia de superficie. |

## Fase G — Cadena de suministro e integridad del binario (T6)

| # | Slice | Estado | Nota |
| --- | --- | --- | --- |
| F20 | Dependencias bajo control | 🟡 | Allowlist/denylist + lockfile construidos; falta confusión de deps y secretos en git. **Hallazgo de F4 (2026-08-21): el prefijo `androidx.` del allowlist admite cualquier grupo androidx en silencio** — `androidx.biometric` y sus 8 transitivas (incl. `appcompat:1.2.0`, de 2020) entraron sin que el gate preguntara. Estrechar el prefijo es trabajo de este slice. |
| F21 | Integridad del binario y del runtime (Play Integrity / App Attest, root/jailbreak, sin secretos, sin debug) | 🔒 shell/backend | Enforcement solo en Release. |

## Fase H — Lo invisible (T4)

| # | Slice | Estado | Nota |
| --- | --- | --- | --- |
| F22 | Logging redactado + cero telemetría fugada | 🟡 | Facade en el stack; falta el punto único y el gate anti-analytics elevado. |
| F23 | Canal de eventos de seguridad + kill-switch (fail-open) | 🔒 backend | Consume endpoints existentes del backend. |

## Regla de cierre de cada slice

Un slice no cierra sin: (1) el ataque descrito en lenguaje humano en su bitácora, (2) lo que se
verificó/fortificó, dicho sin adorno, (3) el cimiento durable (ADR/gate/test/threat-model), (4) lo
que quedó **diferido o sin verificar**, declarado — jamás escondido.
