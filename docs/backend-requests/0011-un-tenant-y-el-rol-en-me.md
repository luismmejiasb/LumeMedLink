# Encargo al backend — un tenant del IdP para LumeMedLink, y el rol en `/v1/me`

> **Cómo usar este documento:** es el prompt completo para el agente de `lumemed-cloud-platform`. Viene de una
> decisión del autor del 2026-10-14 (ADR-0058 y ADR-0060 de LumeMedLink).

---

## PROMPT

Eres el agente de desarrollo de `lumemed-cloud-platform`. LumeMedLink tiene **una sola puerta**: médico y paciente
ingresan por la misma pantalla (nuestra ADR-0056), y el autor decidió que **el servidor le diga a la app qué rol tiene
la cuenta después de ingresar**. Con eso, la app abre las funciones del médico o las del paciente.

Hoy eso no se puede. Tu `ADR-0036` usa un tenant del IdP por tier (`IDP_PATIENT_TENANT_ID` e
`IDP_LINK_CLINICIAN_TENANT_ID`), y el tier sale de la audiencia del token. La app tendría que elegir el tenant antes
de ingresar, y no existe una operación previa que diga de qué tipo es una cuenta.

### Lo que necesitamos

1. **Un solo tenant del IdP para las cuentas humanas de LumeMedLink**, pacientes y médicos, o la forma equivalente
   que elijas. La app ingresa sin saber qué tipo de cuenta es.
2. **El rol en `/v1/me`**: `PATIENT` o `CLINICIAN` (o como lo nombres), como vocabulario abierto. La app trata uno
   desconocido como «sin rol» y no abre nada.
3. **La autorización sigue siendo tuya.** El rol en `/v1/me` es para que la app decida qué dibuja; tus rutas siguen
   negando a quien no corresponde, como hoy. Un médico de LumeMedLink sin enlazar (`ADR-0090`) responde lo que
   corresponda a ese estado, y queremos saber qué dice `/v1/me` en ese caso.
4. **En desarrollo**: que `/dev/token` emita el token de `dev-patient` y el de `dev-doctor-link` bajo ese mismo
   esquema.

### Lo que no pedimos

- Una consulta previa al ingreso que diga el tipo de cuenta: revelaría qué RUT o correo tiene cuenta.

Responde con el contrato de `/v1/me` y con cómo queda tu `ADR-0036`.
