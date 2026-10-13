# 0034 · Las cargas como skeleton de su pantalla

> **Estado:** PENDING (abierta 2026-10-13) · ADR-0055 · **Cableada** con el `LumePlaceholder` del kit (`b6cfabe`). Falta:
> cubrir dos componentes en el kit y verlo en iOS y en las pantallas empujadas.

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

## Avance 2026-10-13

- **`CareLoadView`** dibuja la carga como la pantalla misma, bajo `LumePlaceholder`.
  - Es un solo árbol: el relleno mientras espera y los datos al llegar.
  - Un fallo sigue mostrando su estado con «Reintentar».
  - El relleno sale del `StandIn` de cada modelo: la **misma lectura** contra el guion sin latencia
    (`standInPatientCare`, `standInDoctorCare`). No hay datos escritos a mano, y la forma sigue a la pantalla cuando
    ella cambia.
  - Los modelos con parámetro (detalle, cambio, reserva, resultados) leen el relleno con una copia del mismo modelo
    apuntada al guion.
  - Hasta que el relleno está listo, que es casi inmediato, se ven las filas genéricas de `LumeSkeleton`: nunca un
    indicador.
- **Gate:** P5 en `check-forbidden-patterns.sh` rechaza `LumeActivityIndicator` en la app, con su cebo (123 cebos, todos
  en rojo).
- **Visto en el emulador:** Inicio del paciente, con la latencia del guion subida a 8 s en una build local y no
  commiteada. Coincide con la forma real.
  - **Faltan en el kit:** el rótulo en mayúsculas de la tarjeta y las estrellas de la valoración, que todavía se dibujan
    con su contenido.
  - Las otras pestañas cargan junto con Inicio al entrar, y no se alcanzaron a capturar esperando.
