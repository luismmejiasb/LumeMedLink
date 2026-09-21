# 0031 — La ventana que se apagaba en Ajustes

**2026-09-21.** Quinto y último punto de la auditoría. Dos defectos en las mismas siete líneas.

## 1 · La ventana se medía con el reloj de pared

```kotlin
return clock.nowEpochMillis() - last >= windowMillis
```

**El reloj de pared es un ajuste.** Atrasa la hora del teléfono y la resta se vuelve negativa: la
ventana no vence nunca y la sesión no se bloquea jamás.

En simple: el bloqueo por inactividad se apagaba desde **Ajustes → Fecha y hora**, sin contraseña, en
quince segundos. Sobre la amenaza que este repo pone **primera** —el teléfono del médico en manos de
alguien más de la casa— eso no es un detalle.

Y el arreglo obvio, «usa un reloj monotónico», es medio arreglo y falla al revés: en las dos
plataformas hay relojes de tiempo transcurrido que **se detienen mientras el dispositivo duerme**, y
un teléfono en el bolsillo tres horas es el caso ordinario, no el exótico. Medido sólo así, la
sesión volvería desbloqueada tras una noche en el velador.

## 2 · Nadie preguntaba si la ventana ya había vencido

El shell guardaba `locked` como copia del estado del lock y la releía **en un solo lugar**: el
manejador de punteros. Podían pasar cinco minutos con la agenda en pantalla y la app se enteraba
**cuando alguien la tocaba** — que es justo el momento en que la persona ya está mirando la pantalla.

La amenaza es el teléfono **dejado sobre una mesa**, y un teléfono sobre una mesa no tiene toques.

La lógica del lock estaba bien. Simplemente nunca se le preguntaba.

## El arreglo: dos relojes, y cierra el que llegue primero

```kotlin
val elapsed = maxOf(byWallClock, byElapsedClock)
```

Cada uno cubre el fallo del otro, y —esto es lo que me gusta— **no depende de acertar cuál
plataforma pausa cuál reloj**:

| fallo | reloj de pared | reloj transcurrido | `maxOf` |
|---|---|---|---|
| atrasan la hora | no bloquea nunca | no le afecta | **bloquea** |
| dispositivo dormido, reloj pausado | no le afecta | no bloquea nunca | **bloquea** |
| adelantan la hora | bloquea antes | no le afecta | **bloquea antes** — la dirección inofensiva |

Seam nuevo `ElapsedClock` en `core/`, que le pide a cada plataforma **lo mismo y por escrito**:
tiempo desde el arranque **incluyendo lo que el dispositivo estuvo dormido**. Android
`SystemClock.elapsedRealtime()` (no `uptimeMillis`, no `nanoTime`: esos se detienen). iOS
`clock_gettime(CLOCK_MONOTONIC)`, que en Darwin **sí** avanza durante el sueño, al revés que
`CLOCK_UPTIME_RAW` y `ProcessInfo.systemUptime`.

**Honesto con la evidencia:** lo de iOS sale de la documentación de Apple, **no está medido** — un
simulador no duerme y esta sesión no tuvo device que durmiera. Si la documentación estuviera
equivocada, la dirección del fallo es la **segura**: queda la mitad del reloj de pared, que es donde
estaba antes.

Y la ventana ahora **se cierra sola**: `millisUntilLock()` expone lo que falta, clampado a cero para
que nadie le pase un negativo a `delay()` y gire en vacío. El shell duerme eso y vuelve a preguntar
al despertar — si hubo actividad mientras dormía, recibe un número positivo nuevo y duerme otra vez.
Sin intervalo que afinar y sin reinicio que orquestar.

## Los cebos

Cinco tests nuevos, y **tres cebos rojos, cada uno por el test correcto**:

- medir sólo con el reloj de pared → cae `windingTheWallClockBackDoesNotKeepTheSessionOpen`;
- medir sólo con el transcurrido → cae `anElapsedClockThatStopsDoesNotKeepTheSessionOpenEither`;
- quitar el clamp → cae `millisUntilLockCountsDownAndNeverGoesNegative`.

Y algo que encontré de paso: **los tests que ya existían inyectaban un reloj y heredaban el otro del
sistema real**. Un test que depende de un reloj de verdad es exactamente lo que este repo no quiere;
quedaron cableados con los dos.

## Lo honesto sobre el riesgo

**Hoy no era alcanzable.** La app no tiene login, así que nunca navega a `Home` y la ventana no corre
en producción. Lo que se arregló es un defecto que habría **embarcado con el primer slice de login**,
dentro del control que ese slice existe para proteger.

No fue una brecha. Fue una trampa puesta para el yo de dentro de dos semanas.

## Lo que queda nombrado y sin cerrar

El techo de intentos fallidos vive en memoria: **matar el proceso lo reinicia**. El subsistema
biométrico del SO tiene su propio bloqueo, que acota el daño, pero el techo que esta app cree
imponer no es el que se impone. Necesita su propio slice.

ADR-0032.
