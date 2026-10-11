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

## Lo que decidió el autor (2026-10-11)

- **Encabezado con acciones** (campana y perfil en Inicio, calendario en Mis citas): el kit agrega hasta dos botones
  redondos opcionales a la derecha de la barra con título grande. La barra con volver sigue sin ellos.
- **Título raíz** de las pestañas en 30 bold, con un rol tipográfico nuevo del kit.
- **Control de estrellas** en el kit, para valorar (f5, c3).
- **«Correo» en Mis médicos**: excepción acotada a ADR-0007, sólo `mailto:` con la dirección (sin adjunto, asunto ni
  cuerpo), escrita en una ADR y con el gate P2 permitiendo esa única forma.
- **Avatar como accesorio de fila**: lo entregó el kit y está cableado.
- **c1 «Motivo que escribiste»**: c1 y c2 se construyen sin ese bloque. **Para el paquete de diseño**: el motivo
  clínico no existe en esta app (ADR-0001); c1/c2 deberían corregirse.
- **Canceladas**: Mis citas lleva tres pestañas (Próximas, Pasadas, Canceladas), como e8 y la spec de a4.
- **Disponibilidad en p4**: una fila con su valor que abre su propia lista, como el diseño.
- **Pedido `0008`**: pasado a la sesión del backend.

## La respuesta del backend al pedido `0008` (2026-10-11)

Está en `../lumemed-cloud-platform/tareas/PENDING/0121-lo-que-le-falta-al-contrato-para-la-app-del-paciente.md`.
El contrato sigue en 0.96.0. Esto choca con lo que dibuja el guion y espera al autor:

- **§8, cómo entra el paciente**: el backend cerró el autoservicio el 2026-09-16 (ADR-0035 del backend): buscar es
  abierto, pero reservar exige un vínculo activo con la consulta. ADR-0044 de este repo da el acceso por abierto, y
  cambiarlo exige una ADR que derogue SD-1 del backend. Es la decisión pendiente sobre el modelo de acceso del paciente.
- **§6, recordatorios**: la plataforma no manda recordatorios y el paciente no elige tiempos, así que «24 h y 2 h
  antes» de Configuración no existe. Además, la ventana de cambio (`cancelCutoffHours`) no viene en la cita, aunque la
  descripción del contrato diga que sí: es un defecto del backend.
- **§7, búsquedas recientes**: el backend propone no guardarlas ni en el teléfono ni allá, porque especialidad más
  comuna dice algo de la salud de quien busca. p2 las dibuja.
- **§4 y §5, valoraciones y buzón**: esperan al autor; sin buzón, la campana no tiene qué mostrar.
- **Propuesto como aditivo**, pendiente del autor: `professionalId` en la cita, `specialtyCode` en el contacto, la
  dirección del lugar en la cita y `cancelCutoffHours`. Sin coordenadas: el backend no geocodifica.

El backend pide no presentar como reales las formas que todavía no existen. **El autor decidió** (2026-10-11): no se
le hacen más pedidos al backend por ahora, porque está ocupado con LumeMed. Todo sigue siendo maqueta fiel al diseño;
los recordatorios y el acceso quedan como los dibuja el diseño, con el choque anotado. Las búsquedas recientes se
quitaron, y los cuatro aditivos quedan aprobados para comunicarlos cuando el backend vuelva a tomar pedidos (ver el
pedido `0008`, sección final).

## Igualar el kit al diseño (pedido del autor, 2026-10-11)

El autor vio la app lejos del diseño, sobre todo en Agendar y en la barra de pestañas, y pidió que el kit respete
distribución, espacio y aspecto de cada componente. Autorizó cuatro piezas nuevas: los días y horas de a1 (franja de
días y grilla de 3 columnas con hora tomada y «Tu cita»), el selector compacto de mes y año, la valoración visible y
la tarjeta de acento rellena. La barra de pestañas pasa a ser la flotante del diseño, con el disco elevado y la
transición del gif. El diff medido (fondo de página, título centrado, pesos, reparto 42/58 de la barra de acción,
callout lavado, alerta centrada, chips de acento, filas etiqueta-sobre-valor) se le pasó a la sesión del kit. La app,
por su lado, usa `LumeAvatar` para los médicos y saca las listas de una fila que duplican la sangría.

Ese mismo día el kit entregó los ocho lotes, más el avatar de fila (también autorizado por el autor), y la app los
cableó. Lo que queda abierto: el título raíz en 30 bold (el kit usa 28 semibold; un rol tipográfico nuevo espera al
autor), y los detalles que se le devolvieron al kit tras la captura (tinta blanca dentro de la tarjeta sólida, ancho
de los días, el punto medio del chip de filtros, el campo de búsqueda sin título y el tile lavado de p2).

## Qué NO hacer

- No guardar en el teléfono las búsquedas recientes ni nada leído: online-only (§0), y el pedido `0008` pregunta
  quién las guarda.
- No componer reagendar con cancelar + reservar (T7).
- No mostrar el motivo de una cita: no existe en esta app (ADR-0001).
- No escribir UI propia: lo que falte en LumeUIComposer se recomienda y espera la autorización del autor.
