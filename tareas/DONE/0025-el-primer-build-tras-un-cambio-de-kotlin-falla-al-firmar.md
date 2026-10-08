# 0025 · El primer build de Xcode tras un cambio de Kotlin falla al firmar

> **Estado:** DONE (2026-10-08)

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

## Cerrada — 2026-10-08

El build phase ya no borra productos. Escribe el sello del framework en `KotlinFrameworkStamp.swift`, en
`DERIVED_FILE_DIR`, sólo cuando cambió; lo declara como salida **y** lo tiene en la fase Sources — medido que una salida
declarada sola no se compila (el sello cambió y nada se re-enlazó). Cuando Kotlin cambia, ese objeto cambia y el enlace
corre **en el mismo build**, planificado como cualquier otro.

**Cómo se verificó:** a mano, un cambio sólo de Kotlin → un build firmado, `Ld` presente, el marcador en el binario. Y
`Scripts/verify-ios-link-freshness.sh`, ahora **firmado** —con `CODE_SIGNING_ALLOWED=NO` no hay paso de firma, que es
justo donde fallaba, y por eso este verificador estuvo verde sobre el defecto—: con el mecanismo, el cambio llega; con el
sello congelado (control), no llega. Gate en `check-ios-host.sh` (salida declarada, en Sources, sello vivo, nada se
borra) con cuatro cebos.
