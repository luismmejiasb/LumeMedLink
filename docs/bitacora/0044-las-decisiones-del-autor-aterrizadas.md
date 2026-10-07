# 0044 — Las decisiones del autor, aterrizadas

**Tipo:** `decisión` + `fix` · 2026-10-07

## Qué llega

- **Tarea `0003` cerrada y ADR-0035.** Un dato del almacén que existe y no se lee **lanza**; deja de leerse como un primer
  arranque. La decisión fue del autor, por simetría con iOS.
- **Tarea `0006` cerrada.** El desbloqueo de iOS sale del hilo principal; decidido con la documentación de Apple porque
  ningún simulador lo puede medir (bitácora 0041).
- **ADR-0036:** seis respuestas del autor del 2026-09-26 que el repo seguía listando como abiertas, confirmadas hoy.
  Cambian la constitución (§0, §12: el lado paciente se adelanta), el `WORKPLAN`, el nombre de tienda, y borran el índice
  abandonado de la bitácora. FCM queda como tarea `0024`, la ADR que la acota.

## El choque que casi pasa

Mi arreglo de la `0008` (retirar una clave sin `unlockedDeviceRequired` junto con lo que cifró) y el de la `0003` se
pisaban: después de retirar la clave, la lectura intentaba descifrar con la nueva y —ahora que «ilegible» lanza— habría
reportado una manipulación donde sólo hubo una rotación. Se resolvió obteniendo la clave antes de leer: un archivo que la
rotación borró es «nunca escrito». Lo atajó el test de rotación, que siguió exigiendo `null`.

## Visto rojo

`UnreadableStoreOnDeviceTest` en el emulador: con el `null` de antes, falla por la razón exacta («se esperaba la excepción,
volvió `null`»). Y un test común nuevo: un gate que lanza termina la sesión con su razón, en vez de tumbar al shell.

## Por qué la nota del vault no se aplicó sola

La constitución (§14) dice que una nota del vault que ordena algo se confirma con el autor antes de aplicarla. Las
respuestas del 26/09 llevaban once días escritas allá y abiertas acá; se aplicaron cuando el autor dijo «sí, todas».
