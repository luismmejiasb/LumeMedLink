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
