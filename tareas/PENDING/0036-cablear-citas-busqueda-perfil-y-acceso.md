# 0036 · Cablear con la plataforma: citas, búsqueda y reserva, perfil y acceso

> **Estado:** PENDING (abierta 2026-10-14) · ADR-0058, ADR-0059 (propuesta), ADR-0060, ADR-0061 · Pedidos al backend
> `0010`, `0011` y `0012`. Fase A en curso; la fase B espera al backend.

## De dónde sale

El 2026-10-14 el autor reabrió el cableado contra la plataforma, que hasta entonces corría contra el guion
(ADR-0046). Acotó el alcance a cuatro áreas: **citas, búsqueda de médicos con reserva, edición del perfil y acceso**.
Pidió **SOLID estricto**. La sesión de la plataforma dio el inventario y la de LumeMed los casos de uso y lo que el
paciente no puede ver. Las respuestas están en esta conversación y en las ADRs; las rutas del backend, en los pedidos.

## Decisiones del autor (2026-10-14, por formulario)

- **Ingreso con RUT y contraseña**, luego TOTP, con la cuenta nacida del código de la clínica (ADR-0058, pedido `0010`).
- **Una sola puerta; el rol lo dice `/v1/me`** (ADR-0058, pedido `0011`).
- **Toda funcionalidad principal tras `FeatureGate`**: el rol abre, el flag de Remote Config cierra (ADR-0060).
- **El cambio de teléfono y de correo se verifica con un código** (pedido `0012`). Hasta que exista, esa parte sigue
  contra el guion.
- **Un solo perfil a la vista**; un cambio se aplica en cada clínica y el resultado se dice por clínica.

## Reglas que vienen del contrato (LumeMed, 2026-10-14)

- Sólo `/v1/patient/*`, `/v1/directory/*`, `/v1/reference/*` y `/v1/me`. Nunca `/v1/orgs/*`.
- Una cita se cancela o se mueve sólo en `SCHEDULED` o `CONFIRMED`, y dentro de `cancelCutoffHours`.
  - `0` permite hasta el inicio; `null` cancela como `0` y no permite mover.
  - Una `REQUESTED` se retira, no se cancela.
  - `CANCELLED`, `COMPLETED`, `NO_SHOW` y una cita sellada (`closedAt`) son terminales.
- Un estado desconocido se muestra como desconocido, nunca como error.
- `clientId` es un UUID por intento: se reusa en el reintento y se renueva si cambia lo que se manda.
- Mover a `REQUESTED` deja la hora vieja en pie: no se dice «movida» mientras espera.
- La hora es la de la clínica, nunca la del teléfono.
- **Abierto:** mostrar o no `specialtyCode` en la cita. Revela salud; se decide al cablear las citas.

## Qué se hace

### Fase A — sin esperar al backend

1. ADRs 0058–0061 y pedidos `0010`–`0012`. **Hecho** en este cambio.
2. **`FeatureGate`** (`core/features`):
   - el catálogo, la decisión pura, `RoleSource`, `FlagSource` y el gate;
   - `Telemetry` se parte en analítica, crashes y flags;
   - tests de la tabla completa y de las fuentes cambiando en el tiempo.
3. **Segregación de interfaces.** `PatientCareGateway` (23 métodos) se parte por capacidad: citas, perfil y cambio de
   contacto. Valoraciones, avisos y el lado del médico siguen contra el guion, cada uno tras su propia interfaz.
   `DirectoryGateway` se ajusta a lo que el contrato tiene:
   - búsqueda con al menos un filtro;
   - períodos cerrados;
   - feriados.
4. **El cliente generado** (ADR-0061):
   - `contract/` con el archivo pinneado y la lista de operaciones;
   - `Scripts/generate-contract.py`;
   - `Scripts/check-contract.sh` con sus cebos.
5. **Implementaciones HTTP** sobre el stack: citas, directorio y reserva, y perfil (con el cambio en todas las
   clínicas).
   - El token de desarrollo sólo en debug; la base URL por tipo de build. Release no tiene base URL y queda contra el
     guion hasta la fase B.
   - Tests con `MockEngine` por operación: éxito, cada error del contrato y un estado desconocido.
6. **Una corrida real** contra el backend local (`docs/runbooks/local-dev.md` del backend), con `dev-patient`.

### Fase B — cuando el backend construya `0010`, `0011` y `0012`

7. El acceso real: Identity Platform, con su propia ADR para el cliente del IdP, más RUT, TOTP, enrolamiento y
   teléfono enrolado. Exige que el autor acepte la ADR-0059. Se descongelan `0027` y `0028`.
8. La verificación del contacto nuevo, cableada.
9. El rol de `/v1/me` alimentando `RoleSource`.

## Qué NO hacer

- No generar el contrato entero: los modelos clínicos no entran al binario (ADR-0061).
- No decidir el rol con un flag, ni mostrar una función del médico antes de que el servidor lo diga.
- No escribir código de sesión de paciente antes de que el autor acepte la ADR-0059.
- No componer reagendar con cancelar más reservar (T7): es `rescheduleMyAppointment`.
- No sacar a la pantalla `reason`, `cancellationNote` ni nada de la forma de la consulta.

## Cómo se verifica

- Los tests de cada paso, y los gates del repo con los cebos nuevos.
- La corrida contra el backend local: listar, cancelar, reagendar, buscar, reservar y editar el perfil con
  `dev-patient`.
- En el device: lo mismo, con `DEV_SERVER_ON_NETWORK` del backend.

## Avance 2026-10-14

- **Pasos 1 a 4, hechos:**
  - `21c8264`: ADRs y pedidos.
  - `0c53865`: `FeatureGate` y las pestañas de los dos lados.
  - `b0be877`: una interfaz por capacidad.
  - `33df731`: el cliente generado, con 20 operaciones, `ContractClient` y `check-contract.sh` con cuatro cebos.
  - El reporter de eventos de seguridad ya usa el cliente generado: su DTO y su vocabulario escritos a mano
    desaparecieron.
- **Falta el gate dentro de cada pantalla** (cambios de cita, reserva, valorar, cambio de contacto, avisos; solicitudes
  y compartir contacto del médico). Se hace al cablear cada área, para no tocar esas pantallas dos veces.
- **Bloquea el paso 6, decisión del autor:** el stack sólo acepta `https`, y el backend local corre en
  `http://localhost:3000`.
- **El backend respondió los pedidos:**
  - `0011` lo construye ya;
  - `0010` lo resuelve sin auth casero: el IdP guarda la clave, el TOTP y el freno, y el backend sólo convierte el RUT
    con un hash con llave;
  - `0012` va después del `0011`.
- **El autor decidió en la sesión del backend el autorregistro con selfie** (como Alemana Go). Reabre T5 y choca con
  el punto 2 de la ADR-0058 de este repo. El backend lo dejó como tarea con preguntas: proveedor de identidad, revisión
  legal y menores. Mientras no exista, la cuenta sigue naciendo del código de la clínica. Cuando se construya, se
  enmiendan la ADR-0058 y la §1.2 de la constitución.

## Avance 2026-10-14 (tarde) — primera corrida real

- **Contrato 0.130.0** (commit `510efe8f` de la plataforma). Respondió los pedidos `0011`, `0013` y `0014` y la forma
  de elegir lado (`0075`).
- **Cableado en debug contra la plataforma local** (ADR-0062):
  - el reloj de la clínica;
  - las citas: listar, cancelar, retirar y reagendar;
  - la búsqueda con reserva: catálogos, resultados con su próxima hora, cupos, días cerrados, reservar y profesional
    por id.
  - Perfil, valoraciones, avisos y el lado del médico siguen contra el guion, detrás de su interfaz.
- **Verificado en el emulador del repo** (`LumeMedLink_API27`), contra el backend que levantó la sesión de la
  plataforma:
  - «Mis citas» lista las dos citas sembradas de `dev-patient`;
  - Medicina interna › Providencia encuentra a la profesional con «Mañana · 09:00»;
  - reservar crea la cita, moverla a las 10:00 es una sola `rescheduleMyAppointment`, y cancelarla la deja en
    «Canceladas».
- **Defectos encontrados por la corrida, corregidos:**
  - `414b85a`: el motor OkHttp sólo aceptaba TLS, una segunda negativa debajo del stack.
  - `eb75712`: la próxima hora vacía se leía como «sin horas».
- **Falta:**
  - el perfil, con el cambio aplicado en todas las clínicas;
  - el entorno local en iOS (Swift `#if DEBUG` y ATS sólo en Debug);
  - el gate dentro de las pantallas;
  - la fase B (acceso real), que espera el pedido `0010` y un proyecto de Identity Platform.
- **Decisión de LumeMed, informada el 2026-10-14:** la secretaria queda fuera del MVP.

## Avance 2026-10-14 (noche) — el perfil, y la foto

- **Perfil cableado, sólo lectura** (`RemoteProfile`, `listMyPatientProfiles`):
  - Se muestra uno: el de la clínica primera por nombre y luego por id, nunca por el orden del servidor.
  - El teléfono, el correo y el RUT pueden faltar; se dicen «Sin registrar», nunca se inventan.
  - «Cuenta verificada» no aparece: el contrato no afirma ninguna verificación.
- **El cambio de teléfono y de correo, cableado** (contrato 0.131.0, commit `90332cfa`; respuesta `0077` de la
  plataforma al pedido `0012`):
  - `RemoteContactChange` pide, reenvía y confirma la verificación. Un código escribe el contacto en todas las
    clínicas a la vez.
  - El `ticket` vive sólo en memoria, con la hoja abierta: `PendingContactChange` viaja en el reenvío y en la
    confirmación, y no se imprime.
  - El stack lee `attemptsLeft` del error de código incorrecto.
  - Un `stale_write` se confirma otra vez con el mismo código, una sola vez.
  - Pedir el cambio exige un ingreso de 5 minutos o menos (`ADR-0102` de la plataforma). En el backend local, un
    token de desarrollo nuevo cumple esa condición. Con Identity Platform, la pantalla tendrá que pedir el reingreso
    con clave y TOTP antes de abrirse: **queda para la fase B**. Hasta entonces, un ingreso viejo se dice «vuelve a
    ingresar».
  - `updateMyPatientProfile` no se llama: la dirección no se edita en esta app.
- **Verificado en el emulador del repo**, contra la plataforma local 0.131.0 (commit `4f9f05b0`):
  - Configuración muestra el perfil real de `dev-patient`.
  - Cambiar el correo: el código llega a Mailpit, la confirmación escribe el dato y el perfil releído lo muestra.
  - Cambiar el teléfono: el SMS queda en `/dev/sms` y el número cambia igual.
  - Un código equivocado no gasta la solicitud: el correcto confirma después.
  - Al final se devolvieron el correo y el teléfono de la siembra.
- **No verificado:** el mensaje de código incorrecto. `uiautomator` no muestra el error del campo de código, aunque
  `LumeOTPField` lo publica en su semántica, y `FLAG_SECURE` impide la captura de pantalla. Falta revisarlo con
  TalkBack. Su lógica está cubierta por los tests del ViewModel y del gateway.
- **Dos cebos del ensayo de gates no se aplicaban** desde `414b85a` y desde el `buildConfig` de la ADR-0062: sus
  anclas dejaron de ser únicas o cambiaron de forma. `rehearse-gates.sh` lo dijo («2 of 138 baits WENT
  UNDETECTED»). Se corrigieron las anclas, y los dos cebos vuelven a poner rojo su gate.
- **La foto de perfil** (decisión del autor por formulario: la ven el paciente y sus médicos, y llega por la API):
  - ADR-0064, que enmienda la §2 de la ADR-0050;
  - pedido `0015` a la plataforma, enviado a su sesión.
- **El lockfile de `androidApp`** no tenía `kotlinx-datetime` para `debugLintChecksClasspath`, y `./gradlew build`
  fallaba desde `1bf3257`. Se regeneró con `build --write-locks` (ADR-0018).
- **El lint de Android estaba rojo desde `a16c0a6`**, y nadie lo veía porque `build` se caía antes en el lockfile:
  `MissingClass` sobre el `<provider>` de Firebase que el manifiesto elimina. Se ignora sólo en ese nodo.
- **El entorno local en iOS, cableado:**
  - El host Swift pasa `localBackend("http://localhost:3000")` sólo bajo `#if DEBUG`; Release pasa el guion.
  - ATS no necesita excepción. Medido en el simulador con una sonda temporal: `localhost` y `127.0.0.1` pasan sin
    clave ATS, y `http://example.com` se rechaza con -1022. Va como enmienda de la ADR-0062; ningún paso de build
    escribe `NSAllowsLocalNetworking`.
  - Gate 2d de `check-network-posture.sh` con tres cebos.
  - El binario de Release no contiene la dirección local y el de Debug sí.
  - **No verificado:** el flujo completo en el simulador. No hay cómo tocar la pantalla del simulador desde aquí, y
    tampoco hay un target de UI tests.
- **El gate dentro de las pantallas, hecho** (nota del 2026-10-14 en la ADR-0060): cada acción del paciente y del
  médico tiene su puerta cerrada cuando su funcionalidad está apagada. Las funciones puras están probadas; que el host
  las pase a cada pantalla queda para revisión, sin test de UI.
- **Falta:**
  - la fase B, que ahora incluye el reingreso con clave y TOTP antes de cambiar un contacto;
  - la foto, cuando la plataforma construya el pedido `0015`.

## Avance 2026-10-16 — el sobrecupo que el paciente pide

- **La fase B y la foto siguen bloqueadas** en la plataforma: sus tareas `0146` (ingreso con RUT) y `0150` (foto)
  están en PENDING.
- **Contrato 0.135.0** (commit `243f1a12` de la plataforma, su tarea `0157` y su `ADR-0105`). Las operaciones que ya
  usábamos no cambiaron.
- **Decidido por formulario (ADR-0069):**
  - pedir no es ofrecer: la app nunca muestra un sobrecupo como hora, sólo deja pedirlo donde el profesional lo abrió;
  - los cuatro motivos de la plataforma, acotados: el motivo se ve sólo en la hoja donde se elige;
  - la entrada queda oculta hasta que el directorio diga quién recibe solicitudes (pedido `0017`).
- **Construido, sin pantalla todavía:** `OverbookRequests` (pedir, listar, retirar) sobre el contrato y en el guion,
  tras `Feature.OVERBOOK_REQUESTS`. `DirectoryDoctor.takesOverbookRequests` es `false` en todo lo que responde la
  plataforma.
- **Gate de frontera:** admite `RENEW_PRESCRIPTION` como identificador entero (ADR-0069), con dos cebos nuevos: un
  nombre que lo contiene y un nombre clínico en la misma línea siguen en rojo.
- **La ubicación, decidida por formulario** (enmienda de ADR-0069): la lista es una sección de «Citas», y la entrada
  irá en los resultados, bajo «Agendar».
- **La sección «Solicitudes de sobrecupo», construida:** en «Próximas», sólo cuando hay alguna. Muestra las que esperan
  y las respondidas en los últimos 30 días, cada una con profesional, clínica, estado y fecha, nunca el motivo. Una que
  espera se puede retirar; una que la consulta respondió antes sólo se vuelve a leer.
- **La plataforma tomó el pedido `0017` como su tarea `0161`**, todavía sin construir.
- **Falta:**
  - la hoja para pedir, cuando exista la `0161`;
  - ver la sección en el simulador: `dev-patient` no tiene solicitudes en la siembra, y crear una toca la base local
    de la plataforma.

## Avance 2026-10-16 (tarde) — las dos fotos

- **Contrato 0.137.0** (commit `d4347b82` de la plataforma): la foto del paciente (su `0150`, respuesta `0082`) y la
  del médico (su `0160`, respuesta `0083`).
- **La foto del paciente, cableada** en lugar del sustituto en memoria. Contra el guion sigue en memoria.
- **La foto del médico, en los encabezados de tarjeta** (Inicio, detalle, resultados y reserva), con tope de cuatro
  lecturas a la vez y sólo en memoria.
- **El generador** ahora tipa los cuerpos de imagen y rechaza cualquier otro cuerpo que no sea JSON; antes caían a
  `Unit` en silencio. El cebo nuevo da rojo con el generador nuevo y verde con el viejo.
- **Falta:**
  - la foto del médico en las filas de lista (Mis médicos, Citas): necesita que el avatar de fila del kit acepte una
    imagen, y eso es trabajo del kit con autorización del autor;
  - verla en el simulador.

