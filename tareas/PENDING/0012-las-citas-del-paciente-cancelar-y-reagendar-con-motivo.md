# 0012 · Las citas del paciente: cancelar y reagendar con motivo, y enterarse de lo que haga la consulta

> **Estado:** PENDING · espera dos cosas: el contrato del backend (sus tareas `0020` a `0022`) y que el
> autor le dé prioridad al lado del paciente. Escrita el 2026-09-26 desde la sesión del backend, por
> encargo del autor.

## De dónde sale

- El autor pidió el 2026-09-26 que el paciente, desde esta app, agende, cambie y cancele sus citas con
  un motivo, y que al médico se le avise; y lo mismo al revés. El plan que cruza los tres repos está en
  LumeBrain: «LumeMed/2026-09-26 — Encargo 0002 Agenda con motivo, reagendar y avisos».
- Lo que el autor decidió ese día, y que esta tarea da por sentado: el motivo sale de un catálogo
  cerrado y no clínico; reagendar es una sola operación del servidor; el push es por APNs y sin
  contenido; Android queda sin push hasta que se decida FCM.
- Encaja en la S2.2+ del WORKPLAN («Perfil del paciente y sus citas»), después de la S2.1.

## Lo que va a publicar el backend (son sus tareas, no las de este repo)

- `0020`: cancelar con motivo. **Del lado del paciente, sólo el catálogo, sin texto libre**: la nota
  libre existe únicamente del lado de la consulta. Así se respeta la regla de la constitución sobre
  las citas («Nunca el motivo clínico»), y se evita un campo libre que invita a escribir justamente eso.
- `0021`: reagendar como una sola operación, atómica e idempotente. Es la respuesta a nuestro pedido
  `docs/backend-requests/0003-agenda-reagendar-idempotencia.md`.
- `0022`: push por APNs, con una señal y una `loc-key`. El texto visible lo pone la app, como exige
  ADR-0012.

## Lo que le toca a esta app

1. Reservar, ver, cancelar y reagendar las citas propias contra el contrato publicado. Todo lo que la
   S2.2+ ya dice sigue valiendo: el 409 `patient_unavailable`, no reintentar, y releer
   `listMyAppointments` antes de mostrar un fallo.
2. Al cancelar o reagendar, elegir el motivo del catálogo. **Los nombres del código no usan la palabra
   que vigila el gate de datos**: `Scripts/check-data-boundary.sh` la rechaza en cualquier `.kt`, y el
   concepto es otro (la causa de una cancelación, no el motivo de la consulta). Va en inglés, por
   ejemplo `CancellationReason`.
3. Ver dentro de la app, con sesión, lo que haga la consulta: una cita cancelada o movida, con la
   etiqueta del motivo que eligió. La nota libre de la consulta no llega nunca a esta app.
4. Push en iOS: registrar el token después del login, y mostrar el texto genérico que elige la app.
   Antes hay que enmendar ADR-0012 y `Scripts/check-preauth-surfaces.sh`, que hoy prohíben las APIs de
   notificaciones: una notificación en la pantalla bloqueada es una superficie pre-login.
5. **Oponerse a los avisos, por canal** (decisión del autor del 2026-09-26, tarea `0024` del backend):
   el paciente puede pedir que no le escriban por correo, por SMS o, cuando exista, por push. Lo que ve
   dentro de la app sigue visible. La pantalla tiene que dejar claro que oponerse a un canal no apaga la
   información dentro de la app, ni borra su contacto.

## Qué NO hacer

- Componer reagendar con dos llamadas. La constitución lo prohíbe, y el backend va a publicar la
  operación.
- Un campo de texto libre para el paciente.
- Push en Android, o Firebase: sigue prohibido hasta que el autor decida sobre FCM.
- Poner el motivo, o cualquier dato de la cita, en una notificación.

## Lo que espera al autor

La prioridad. La FASE 1 construye primero el lado del médico, y esto es lado paciente. La pregunta
está en LumeBrain, «Decisiones pendientes del autor», sección «Agenda: motivo, reagendar y avisos».
