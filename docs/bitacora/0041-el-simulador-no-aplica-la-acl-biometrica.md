# 0041 — El simulador no aplica la ACL biométrica del Keychain

**Tipo:** `medición` · 2026-10-07

## Qué llega

- La medición que pedía la tarea `0006` («primero medirlo»), hecha y **inconcluyente por construcción**. La tarea sigue
  abierta, con lo que falta y de quién.
- Enmienda de ADR-0011 y corrección de la fila F4 del plan de fortificación: el lado iOS del tier 2 sólo se verifica en un
  iPhone físico.

## El experimento

En un worktree descartable, una sonda en el host de iOS enrolaba el ítem del tier 2 y llamaba `unlock()` desde la
composición —o sea, desde el hilo principal—, con un latido `NSLog` en Main cada 250 ms. La pregunta de la tarea era si el
prompt de Face ID aparece con el hilo principal bloqueado, o si la app se congela detrás.

Ninguna de las dos: `unlock()` volvió en milisegundos con `Unlocked`, sin prompt en pantalla, sin que yo enviara ninguna
coincidencia de Face ID, y el latido siguió como si nada.

## Los controles

Un desbloqueo que no pide nada puede ser cualquier cosa, así que dos controles:

1. Sólo desbloquear, sin re-enrolar, tras reenviar la notificación de enrolamiento: `Unlocked`.
2. **Con Face ID des-enrolado:** `Unlocked`. Un dispositivo no devuelve un ítem `.biometryCurrentSet` sin biometría; ni
   siquiera existe.

Así que el simulador guarda el ítem con su ACL y no la aplica. No hay prompt, no hay bloqueo que medir, y no hay
invalidación por enrolamiento que observar. Nada del tier 2 de iOS se prueba en un simulador — y es un hecho que vale para
toda la familia, no sólo para este repo: está en el vault.

(Una trampa del instrumento, por si alguien lo repite: el `log stream` del simulador escribía al mismo archivo donde yo
anotaba marcas con `>>`, y como él no escribe en modo append, sus líneas pisaban las mías. El orden se leyó igual.)

## Lo que sí hay

La documentación de Apple de `SecItemCopyMatching`, leída hoy: *«SecItemCopyMatching blocks the calling thread, so it can
cause your app's UI to hang if called from the main thread. Instead, call SecItemCopyMatching from a background dispatch
queue or async function.»* La auditoría lo había razonado; Apple lo escribe como contrato.

## Lo que no hice, y por qué

No apliqué el arreglo. La tarea dice «no arreglarlo antes de medirlo», por un precedente real de este repo (ADR-0026), y
medir requiere un iPhone físico. Con la documentación en la mano, la decisión es del autor: medir en su iPhone, o aplicar
el arreglo —la lectura en un dispatcher de IO inyectado— con Apple como evidencia. Está en `PROGRESS.md`.
