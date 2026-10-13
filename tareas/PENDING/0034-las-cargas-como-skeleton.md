# 0034 · Las cargas como skeleton de su pantalla

> **Estado:** PENDING (abierta 2026-10-13) · ADR-0055 · **bloqueada por el kit**: el modo placeholder de
> LumeUIComposer no existe todavía. Se lo pidió a la sesión del kit el mismo día.

## De dónde sale

El autor pidió que todas las cargas de pantalla sean skeleton, imitando la forma de la vista que se va a cargar, y no
un indicador. Por formulario eligió un modo placeholder en el kit, en lugar de skeletons armados a mano, y dejó los
botones con su indicador.

## Qué se hace

1. `CareLoadView` (`core/screen/ScreenLoad.kt`) recibe, para el estado `Loading`, la vista con datos de relleno, y la
   dibuja bajo el modo placeholder del kit.
2. Las 13 pantallas que lo usan dan su relleno. Son: inicio, citas, mis médicos y ajustes del paciente; detalle y
   cambio de cita; reserva, resultados y los dos selectores de búsqueda; y agenda, pacientes y perfil del médico.
   - El relleno es sintético y genérico, sin nombres de personas reales (§9).
   - Vive junto al modelo de la pantalla, para que cambie cuando ella cambie.
3. Un gate que rechace `LumeActivityIndicator` en `features/`, con su cebo en `rehearse-gates.sh`.
4. Verificación en el emulador y en el simulador. Para que la carga se vea, el guion necesita una latencia visible.

## Qué NO hacer

- No armar skeletons a mano con `LumeSkeleton` y medidas en dp: el autor eligió el modo del kit.
- No pasar a skeleton los botones que esperan una respuesta.
