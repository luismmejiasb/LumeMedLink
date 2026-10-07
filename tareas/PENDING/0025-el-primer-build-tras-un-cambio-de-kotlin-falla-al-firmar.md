# 0025 · El primer build de Xcode tras un cambio de Kotlin falla al firmar

> **Estado:** PENDING · abierta el 2026-10-07, medida una vez con su control. Técnica, no del autor.

## De dónde sale

El build phase «Build Kotlin framework» fuerza el relink cuando el framework cambió **borrando el producto**
(`LumeMedLink` y `*.debug.dylib`, ADR-0030). Medido hoy: cuando el Kotlin cambió y **los objetos Swift no**, Xcode ya había
planificado el build sin enlazar —el `.debug.dylib` existía al planificar—, el script lo borró a mitad de build y
`CodeSign` falló con «No such file or directory». El segundo build, sin cambios, sí enlaza (`Ld` presente) y sale
verde con el Kotlin nuevo. Evidencia: `tmp/lml/0017-measure.log` de las corridas de las 18:27 y 18:31.

## Por qué importa

Falla **ruidoso**, no silencioso: nadie corre Kotlin viejo por esto. Pero cada verificador de iOS
(`verify-ios-privacy-cover.sh`, `verify-install-sentinel.sh`) hace builds sucesivos con cambios sólo de Kotlin, y un primer
build rojo se lee como un defecto del código que se está midiendo — el error que la bitácora 0026 ya conoce.

## Qué se hace

Que el relink no dependa de borrar un producto que Xcode ya planificó: por ejemplo, una salida declarada del script que
el paso de enlace tenga como entrada (un `.xcfilelist` o un `-Wl,` que lea el sello), medido con el mismo control —
cambio sólo de Kotlin → **un** build verde con el código nuevo.

## Qué NO hacer

- No volver al framework en la fase Frameworks: ADR-0030 midió que no funciona.
- No quitar el borrado sin reemplazo: es lo que impide el binario byte-idéntico con Kotlin viejo.
