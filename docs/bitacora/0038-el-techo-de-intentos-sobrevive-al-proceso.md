# 0038 — El techo de intentos sobrevive al proceso

**Tipo:** `fix` · 2026-10-07

## Qué llega

- **Tarea `0007` cerrada** y **ADR-0034**. El contador de desbloqueos fallidos deja de ser un `var` dentro de
  `SessionLock` y vive en el almacén tier 1, como una entrada más del enum que el logout borra entero.
- Una razón nueva para terminar la sesión, `ATTEMPTS_UNRECORDABLE`: un contador que el almacén no puede leer, escribir o
  interpretar no se trata como cero.
- Ocho tests nuevos en `SessionLockTest`, en Android host y en iOS.

## El defecto, dicho corto

`SessionLock` vive en la composición, y su contador con él. Matar el proceso —o rotar el teléfono, que recrea la
composición— lo volvía a cero. El test que lo prueba ahora hace exactamente eso: cuatro fallos, un `SessionLock` nuevo
sobre el mismo almacén (lo único que un proceso reiniciado comparte con el que murió) y un fallo más, que tiene que
terminar la sesión.

## Lo que pensé hacer y no hice

Cobrar el intento antes de mostrar el prompt, para que un proceso muerto a mitad del prompt también lo pagara. Lo
descarté al mirar qué cuenta este techo: cancelar es gratis por diseño (el espejo de ADR-0020) y un dedo equivocado no
cierra el prompt de Android, así que quien puede matar el proceso a mitad del prompt puede simplemente cancelarlo. Cobrar
antes agregaba un reembolso a una política de seguridad sin quitarle nada a nadie.

Y de esa misma lectura salió lo que vale la pena dejar escrito: **el techo cuenta sesiones de prompt que terminan en
fallo, no dedos equivocados**. Contra alguien que prueba dedos, lo primero que corta es el bloqueo del propio sistema
operativo; este techo acota cuántos de esos ciclos aguanta una sesión. Ya era así antes de hoy; ahora está en ADR-0034,
porque «cinco intentos» se lee como cinco dedos y no lo es.

## Visto rojo

Siete cebos, todos rojos y cada uno por el test que lleva su nombre: el contador de vuelta en memoria, el presupuesto
gastado sin revisar antes del prompt, un contador ilegible o corrupto leído como cero, un fallo que no se puede escribir
y se deja pasar, y un desbloqueo exitoso o un logout que ya no olvidan. Se corrieron una vez con un arnés fuera del repo;
lo que corre siempre es la suite.

## Lo que queda

- El 5 sigue sin ADR que lo fije.
- En Android, un contador manipulado se lee como «nunca escrito»: es el defecto de la tarea `0003`, que espera su ADR.
- La rotación ya no resetea el contador, pero sigue recreando el lock bloqueado: la otra mitad de la `0009`.
