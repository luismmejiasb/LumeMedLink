# 0030 · La app principal del paciente, contra un guion leído del contrato

> **Estado:** PENDING (abierta 2026-10-11) · ADR-0046, ADR-0045 · pedido al backend `0008`

## De dónde sale

Después del acceso (tarea `0029`), el paquete de diseño dibuja la app principal (`tareas/PENDING/Handoff/
01-Pantallas/2-App-principal`). El autor decidió el 2026-10-11 construirla contra simulaciones locales que siguen
los contratos del backend, y pedir al backend lo que falta. A diferencia del acceso, casi todo existe en el
contrato 0.96.0; lo que no, va en `docs/backend-requests/0008`.

## Qué se hace, por tajadas

1. **Las cinco pestañas y su barra — HECHO 2026-10-11.** Inicio (p1, e7), Buscar (p2), Mis citas (p5, e8), Mis
   médicos (p6, e9), Configuración (p7), sobre `LumeTabBar` con el contador de «por confirmar» en Citas.
   `core/care/PatientCare.kt` + `ScriptedPatientCare`; `features/patient/`; `app/SessionScope`.
2. **Buscar → reservar — HECHO 2026-10-11** (e0–e3, p3, p4, a1–a4, e2, e5, e6; e10 es de «cambiar hora», tajada 3): especialidad y comuna desde los catálogos,
   resultados del directorio, filtros, día y hora de la disponibilidad, confirmar, «Reserva enviada» (el médico
   confirma: `REQUESTED`). Reservar manda `clientId`.
3. **Tu próxima cita** (f1–f4): cómo llegar, cambiar (reagendar es la operación atómica del contrato, nunca cancelar
   y reservar — T7) y cancelar con su motivo propio (`CANNOT_ATTEND`, `SCHEDULE_CONFLICT`, `OTHER`).
4. **Detalle de una cita** (c1–c3).
5. **Valorar la atención** (f5a–f5d) — sin contrato; espera el control de estrellas del kit.
6. **Foto de perfil** (k1–k4) — sin contrato y con cámara/galería: ADR de dependencia antes.
7. **El médico** (m1–m3): agenda simple, pacientes como contactos, su perfil.

Cada tajada lleva sus capturas junto a las del diseño, enviadas al autor.

### El guion de la búsqueda (`core/care/DirectoryScript.kt`)

| Para ver | Haz |
| --- | --- |
| p3, tres médicos | Medicina general en Providencia |
| e5, comunas cercanas | Cardiología en Vitacura |
| e6, sin conexión | cualquier especialidad en Ñuñoa |
| la hora se acaba de tomar | Dra. Rendón, lunes 21, 10:00 |
| una reserva por confirmar por médico | reservar dos veces al mismo médico |
| la hora que ya tienes | Cardiología en Las Condes, lunes 21: falta las 09:00 (Dr. Pinto) |

## Cómo se verifica

- `./gradlew build`, los tests de `features/patient/` y `shared/CalendarDateTest` en los dos targets.
- Todos los gates, y `Scripts/rehearse-gates.sh` en verde.
- Capturas en el simulador de iOS con el recorrido de `tmp/tour/` (Android deja la captura negra: `FLAG_SECURE`).

## Lo que espera al autor

- **El encabezado con acciones** (campana y perfil en Inicio, calendario en Mis citas): `LumeNavBar` no tiene
  ranura a la derecha a propósito. ¿Se agrega al kit o se mueven?
- **La tarjeta de la próxima cita rellena del acento**: hoy es la tarjeta de acento del kit (un lavado).
- **Un control de estrellas** en el kit, para valorar.
- **«Correo» en Mis médicos**: abrir la app de correo es el intent de envío de Android, que ADR-0007 cierra sin
  excepciones. ¿Se abre una excepción para una dirección sin adjunto?
- **Un avatar como accesorio de fila** del kit, para las iniciales del médico (hoy: tile de persona con color estable).
- **El diseño c1 muestra «Motivo que escribiste»** (texto clínico): el autor ya quitó el motivo y esta app no lo tiene
  (ADR-0001); el paquete de diseño debería corregirse.
- **e8 dibuja una tercera pestaña «Canceladas»** en Mis citas que p5 no tiene: ¿cuál manda?

## Qué NO hacer

- No guardar en el teléfono las búsquedas recientes ni nada leído: online-only (§0), y el pedido `0008` pregunta
  quién las guarda.
- No componer reagendar con cancelar + reservar (T7).
- No mostrar el motivo de una cita: no existe en esta app (ADR-0001).
- No escribir UI propia: lo que falte en LumeUIComposer se recomienda y espera la autorización del autor.
