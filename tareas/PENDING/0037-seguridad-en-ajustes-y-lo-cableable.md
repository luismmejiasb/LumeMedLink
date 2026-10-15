# 0037 · «Seguridad» en Ajustes, y todo lo que el contrato ya permite cablear

> **Estado:** PENDING (abierta 2026-10-14) · Sigue a la tarea `0036` · ADR-0011, ADR-0037 (su regla de la familia),
> ADR-0060 · La parte de clave y TOTP con Identity Platform espera a la fase B de la `0036`.

## De dónde sale

El 2026-10-14 el autor preguntó qué faltaba para dar la app por cableada, y si era sólo el ingreso. La respuesta fue
que no: hay superficies que el contrato ya sirve y siguen contra el guion, y Ajustes no tiene opciones de seguridad.
Además apareció un defecto: la pantalla «Ingresa más rápido» dice «Puedes cambiarlo en Ajustes», y en Ajustes no
hay nada que cambiar.

## Decisiones del autor (2026-10-14, por formulario)

- **Se cablea todo lo que el contrato ya permite**, sin esperar a la fase B:
  - avisos (`listMyNotices`), la campana de «Inicio»;
  - valoraciones: las atenciones por valorar, valorar, ver las enviadas y retirarlas;
  - «Mis médicos» (`listMyProfessionalContacts`);
  - recordatorios por canal (`notification-opt-outs`), la fila «Recordatorios»;
  - el historial de cambios de contacto con «No reconozco este cambio» (`listMyContactChanges`,
    `disownMyContactChange`).
- **«Seguridad» en Ajustes tiene tres filas:**
  - cambiar la contraseña;
  - cambiar la app de TOTP;
  - Face ID o huella.
- **La biometría sigue obligatoria.** El autor mantuvo la regla de la familia del 2026-10-07 (enmienda de la
  ADR-0037): ninguna app Lume funciona sin reingreso biométrico. La fila de Face ID o huella muestra su estado y no
  se apaga. Se corrige la frase falsa de «Ingresa más rápido».

## Ubicación decidida por la sesión, consultable

- El historial de cambios de contacto va junto al perfil, al lado de «Solicitar corrección de un dato», porque el
  autor no lo eligió para «Seguridad».

## Qué NO hacer

- Ningún interruptor que apague la biometría (regla de la familia).
- Ninguna fila que prometa algo que no hace. Mientras el ingreso sea el del guion, cambiar la contraseña y el TOTP
  corren contra el guion, detrás de su interfaz, como el resto del acceso (ADR-0046). Con Identity Platform se
  cambia la implementación, no la pantalla.
- Nada clínico en un aviso (§1.0); el texto de un aviso viene del servidor y se muestra tal cual.

## Cómo se verifica

- Tests de cada gateway remoto contra el contrato, con respuestas simuladas.
- Tests de ViewModel de cada pantalla nueva.
- Una corrida en el emulador contra la plataforma local para lo cableado.
- Cada acción nueva queda tras su funcionalidad en `FeatureGate` (ADR-0060).

## Avance

- 2026-10-14: abierta.
- «Mis médicos» y los avisos, cableados y probados.
- **Capturas** (ADR-0065, decisión del autor): el target `iosAppUITests` y `Scripts/tour-screens.sh` recorren las
  pantallas cableadas en el simulador del repo y guardan una captura de cada una fuera del repo.
