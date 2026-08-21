# 0012 · Lo que el backend dejó preparado, y las dos cosas que nos abre

**Fecha:** 2026-08-21 · **Origen:** el autor avisó que `lumemed-cloud-platform` preparó superficie
para esta app. Leído del tablero (`ECOSYSTEM-STATUS.md` §3.1 y §3.1.1), que es la fuente canónica.
Esta sesión **no tocó el repo hermano**: registra y planifica, no decide por ellos.

## Lo que llegó

**Dos ADRs aceptadas el mismo día que se pidieron:**

- **`ADR-0036` del backend** — la respuesta a nuestro pedido 0001: **audiencia separada por app** en
  Identity Platform, aplicada en su `AuthGuard` (no en su guard de roles, que no corre para
  `/v1/me`) y también en el handshake de WebSocket, deny-by-default con registro exhaustivo, y
  fuera de alcance responde **404 sin anunciar nada**. Es lo que pedimos, con el fallo cerrado y
  silencioso que pedimos.
- **`ADR-0035` del backend** — el tier de identidad de paciente que `ADR-0031` §4 exigía. **Abre el
  gate de nuestro ADR-0006.**

**Y tres advertencias que salieron de leer su código, no de suponer** — las tres nos tocan:

1. El token clasifica al **cliente**; la base sigue decidiendo el **permiso**. El alcance sólo puede
   restar, nunca sumar. (Su §7.4 prohíbe que un claim decida un permiso; la reconciliación se
   declara en vez de resolverse en silencio.)
2. **El `≤15 min` del access token es doctrina, no configuración.** Sin proyecto GCP no hay política
   de token en ninguna parte; hoy corre el token de dev.
3. **El DTO mixto es la trampa real**: `Patient` lleva `bloodType` cifrado y `careDirective` en la
   **misma fila** que correo y teléfono.

## La distinción que evita una falsa sensación de avance

**La decisión existe; la superficie no.** El propio tablero dice «falta construirlas, no
decidirlas». Y nuestro ADR-0006 punto 1 prohíbe cablear el lado paciente **incluso contra mocks**
hasta que exista. Así que Fase 2 pasó de 🔒 a 🔓, no a ⬜-listo-para-construir. Se registró así,
con esas palabras, para que la sesión de mañana no lea «gate abierto» y empiece a construir contra
el aire.

## Los dos hallazgos propios de esta lectura

**1. La dirección no está en nuestra frontera de datos.** El tier aceptado deja al paciente editar
«foto, correo, teléfono y **dirección**». Nuestra lista cerrada del §1.0 es «nombre, foto, teléfono
(E.164), correo, previsión» — **la dirección no aparece**. Si esta app es esa superficie, o no
ofrece dirección, o **ADR-0001 se enmienda con su propio ADR** (el §0 lo exige para extender la
lista). No es un detalle de UI y no se resuelve dentro de un slice de perfil: es la regla que define
el producto. Queda como decisión abierta del autor.

**2. La advertencia nº3 confirma F14 con evidencia, y le cambia la forma.** Nuestro plan de
fortificación ya tenía F14 («la frontera de datos como gate ejecutable») como poda de DTOs mixtos.
El backend acaba de confirmar el caso exacto desde su esquema: una lista de operaciones permitidas
**admite el cuerpo entero** de la respuesta, así que lo no-clínico necesita **proyección propia en
el contrato** — no basta un permiso sobre la entidad, y no basta que el cliente pode. Eso convierte
a F14 en **un pedido de contrato además de un gate**, y le sube la prioridad. Registrado en el plan.

La advertencia nº2 aterrizó en F10: ese slice **no podrá verificar** la expiración contra nada real
hasta que exista la política de tokens; verificará el comportamiento del cliente y declarará el
resto. Mejor saberlo antes de escribir el test que después.

## Higiene

La decisión abierta «cuándo pedir al backend el ADR del tier paciente» quedó **vencida** el mismo
día: el autor decidió y el backend aceptó. Movida a resueltas en vez de dejarla envejeciendo — una
lista de pendientes con cosas ya hechas deja de leerse.
