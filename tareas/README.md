# tareas

Trabajo **de esta app**, escrito para poder ejecutarse sin reconstruir el diagnóstico que lo originó.

Nace el 2026-09-24 copiando la carpeta que ya usan las dos hermanas —`../LumeMed/tareas/` y
`../lumemed-cloud-platform/tareas/`— y la simetría es a propósito: cuando algo de aquí espera algo de
allá, las dos puntas del mismo hecho se leen igual. **El motivo de nacer hoy es un traspaso de equipo**:
el autor cambia de máquina, y todo lo que esta sesión sabía y el repo todavía no, tenía que quedar
escrito en algún lugar que viaje con `git clone`.

## Cómo funciona

```
tareas/
├── PENDING/   ← lo que está por hacerse
└── DONE/      ← lo terminado, con su nota de cierre
```

**Al terminar una tarea se MUEVE de `PENDING/` a `DONE/`** —`git mv`, para que la historia la siga— y
se le agrega al final una sección **«Cierre»**: qué se construyó, en qué commit, qué quedó fuera y por
qué. Una tarea sin cierre no está terminada aunque el código exista.

**No se borra una tarea.** Si se descarta, igual se mueve a `DONE/` y el cierre explica la decisión.

**El número es la dirección** y es único entre `PENDING/` y `DONE/` juntas, porque una tarea se mueve
de una a otra: `Scripts/numbered-docs-have-no-collisions.py` cubre las dos como un solo espacio
(§13 de la constitución).

## Qué separa una tarea de una fila del WORKPLAN

`WORKPLAN.md` dice **qué tajadas existen y en qué orden**; `PROGRESS.md`, qué pasó. Una tarea dice
**cómo se hace una**, con el detalle que no cabe en una fila: el bloqueo con su cita, los archivos
que se tocan, la decisión ya tomada, y **qué NO hacer**. Si una tarea mueve una tajada, `WORKPLAN` y
`PROGRESS` se actualizan en el mismo cambio (§10).

## Qué debe traer una tarea

- **De dónde sale**, con su cita (bitácora, ADR, `archivo:línea`). La mayoría de las de hoy salen de
  la auditoría del 2026-09-20/21 (bitácoras 0027–0033): son los hallazgos que quedaron **nombrados y
  sin arreglar**, que es la forma de que se arreglen.
- **Qué se construye**, en archivos concretos.
- **Qué NO se construye y por qué.**
- **Cómo se verifica** — y en este repo eso significa **con un cebo que la ponga roja antes de creerle
  el verde** (§9, ADR-0029).

## Decisiones del autor, que NO son tareas

Una decisión que sólo el autor puede tomar vive en `PROGRESS.md` («Decisiones abiertas») y en el vault
(`LumeBrain/LumeMed/Decisiones pendientes del autor.md`), **no aquí**. Una tarea bloqueada por una
decisión la nombra y no se empieza a medias.
