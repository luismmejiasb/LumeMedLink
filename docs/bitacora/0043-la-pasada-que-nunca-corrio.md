# 0043 — La pasada de completitud, por fin, y lo que encontró

**Tipo:** `auditoría` + `fix` + `ci` · 2026-10-07

## Qué llega

- **Tarea `0002` cerrada**: la pasada de «qué no se auditó» que la auditoría del 2026-09-20/21 no llegó a correr.
- Doce huecos de gates cerrados con cebo, el lock que re-pregunta al volver del sueño, Face ID declarado en el plist, el
  borrado del tier 2 que ya puede fallar en voz alta. El ensayo pasa de 52 a 75 cebos.
- Ocho tareas nuevas (`0016`–`0023`) y declaraciones en el threat model para lo que no se arregla hoy.

## Cómo corrió

Cinco agentes, el tope del autor. Tres buscadores con una pregunta cada uno: **censo** (qué archivo versionado no nombra
ninguna bitácora, y qué tiene), **costuras** (el defecto entre dos dimensiones) y **pre-mortem** (la semilla de una
filtración en el código de hoy). Después dos refutadores con ángulos distintos: uno re-corrió cada cebo sobre una copia
propia de `HEAD` y buscó otro control que ya atrapara cada cosa; el otro atacó el alcance, lo ya declarado y el arreglo
propuesto.

Veintiún hallazgos, ninguno refutado. Lo más fuerte no fue un hallazgo sino una coincidencia: **dos buscadores, cada uno
por su lado, encontraron que la app volvía del sueño con la agenda en pantalla.** La ventana de inactividad se mide con dos
relojes desde ADR-0032, pero el temporizador que la cierra corría en un reloj que se detiene cuando el teléfono duerme, y
nada re-preguntaba al volver. Es el golpe 9 de este repo —«¿quién y cuándo pregunta?»— otra vez, un mes después de
escribirlo.

## La familia de siempre, tercera pasada

Doce de los hallazgos son la misma clase que ADR-0029 ya nombró dos veces: gates que miran la palabra y no el mecanismo,
o el mecanismo en el lugar equivocado. Un parámetro de la clave escondido en un bloque de comentario; ATS apagado detrás de
un comentario que abre en la misma línea; `setFlags(0, FLAG_SECURE)`, que nombra la bandera y la borra; un `Sentry` en el
host que ningún gate miraba; cuatro gates de CI sin un solo cebo mientras la constitución decía que el ensayo «borra cada
control». Todos reproducidos antes del arreglo y todos con cebo después — y para que lo último no vuelva a pasar, el
ensayo falla si un gate de `ci.yml` no tiene cebo.

## Lo que se decide, no se arregla

La biometría no distingue personas dentro del teléfono (un familiar ya enrolado abre la sesión); el árbol de
accesibilidad es un canal que nadie había declarado; iOS no tapa una grabación ni AirPlay; dos tipos de evento de
seguridad no tienen emisor; los errores transitorios terminan la sesión. Cada uno quedó declarado donde corresponde y con
su tarea; varios esperan al autor.

## Lo que no se midió

Nada de esta pasada corrió en un dispositivo. Lo de plataforma salió de leer el dex y el framework embarcados o de la
documentación, y está dicho así en cada tarea.
