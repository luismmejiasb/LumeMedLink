# 0033 — Los hallazgos medios

**2026-09-21.** La cola de la auditoría. Nada espectacular, que es el punto: **los medios son los que
se quedan sin hacer**, y la mitad de ellos son afirmaciones que dejaron de ser ciertas sin que nadie
lo notara.

## Siete documentos decían cosas que el código ya no hacía

Cada uno verificado acá antes de tocarlo, no tomado de la palabra de la auditoría.

- **§8.16 en presente**: «esta app **lo consume**», sobre `/v1/security-events`. Lo cableado es el
  no-op. La mitad cliente existe y está probada; nada escribe. **Un canal que se cree activo es peor
  que uno ausente** — literalmente el fallo que ADR-0023 se escribió para evitar.
- **§13 etiquetaba `[manual]` la frontera de datos** mientras ADR-0019 la había movido a
  `[lint parcial]` un mes antes.
- **La fila del tablero se entregó el 2026-08-20** y tres documentos siguieron pidiéndola: §0, §1.1 y
  el WORKPLAN. *Nada recomputa la prosa.* Una cifra tiene quien la vigile; una frase que dice «falta
  X» no tiene a nadie y sigue pidiendo X para siempre.
- **El threat model cerraba T4 con «Pendientes: F6, F8, F22»** cuatro líneas después de dar F6 por
  cerrada, en el mismo párrafo.
- **«En iOS no hay ningún archivo que marcar»**, en dos documentos, escrito antes de F7 — que escribe
  uno, excluido del backup por diseño.
- **El plan bloqueaba el tier 2 de iOS en «(sin host)»**, y el host existe desde el 2026-08-25.
- **El KDoc de `KeychainSecureStoreTest`**: «el día que exista el shell de iOS estos tests corren
  hosted». Ese día fue el 2026-08-25 y nadie re-corrió nada. **Y los seis siguen contados en el
  total**: 130 corren, 6 no.

## Y dos configuraciones eran la razón de que dos de esas afirmaciones fueran falsas

`:androidApp` compilaba con warnings tolerados mientras §2.9 decía «sin warnings» — ahora tiene
`allWarningsAsErrors`, y compila limpio, así que la afirmación pasó de escrita a **exigida**.

`dependencyResolutionManagement` nunca fijó `repositoriesMode`, así que el default dejaba a
**cualquier módulo** declarar sus propios repositorios y ganarle al allowlist; el lockfile registraría
fielmente lo que viniera. **Un allowlist sólo es un allowlist si es la única lista.**

## Tres gates y un guardián

- **El gate biométrico cruzado por su propio vocabulario.** `BIOMETRIC_STRONG` como substring lo
  satisface `AUTH_BIOMETRIC_STRONG`, que el propio gate exige cuatro líneas más arriba. Se podía
  cambiar el prompt a `DEVICE_CREDENTIAL` —un PIN en vez de biometría— y seguía verde. Ahora se
  exige la **llamada**: `setAllowedAuthenticators(…BIOMETRIC_STRONG`.
- **La mitad del gate de red que ve lo que inyectan las dependencias no corría en ninguna parte.** El
  job de gates corre en ubuntu sin build, así que el manifiesto fusionado nunca estaba, y el job que
  sí compila no corría gates. **Un mes imprimiendo OK sin mirar nada.** Ahora corre en el job que
  tiene artefactos, y `LUME_REQUIRE_MERGED_MANIFEST=1` convierte el salto en fallo: *un gate puede no
  poder comprobar algo; no puede reportar OK mientras tanto.*
- **El sentinel de iOS dejaba el marcador escrito cuando reportaba FAILED.** Si la exclusión del
  backup fallaba, el archivo ya estaba creado: `markHasRun` lanzaba, el guardián respondía FAILED —
  y el arranque siguiente leía un marcador que decía «este contenedor ya corrió» y **se saltaba la
  purga**. Peor: el marcador que encontraba era el que **no** tenía la exclusión, o sea exactamente
  el residuo que un restore debía purgar. ADR-0028 promete que un fallo deja el contenedor sin
  marcar; ahora el marcador se retira.
- **`tokenWasRejected()` en 401 de peticiones sin token.** Cualquier 401 encendía la bandera, incluso
  en una llamada sin firmar: el siguiente `token()` gastaba una rotación sobre un token que nadie
  rechazó.

## Un defecto latente, escrito antes de que muerda

El `RefreshClient` HTTP, cuando se cablee, **no puede construirse con un `tokenProvider`**:
`onRequest` pide `token()`, `SessionManager.token()` toma su Mutex, y el refresh hecho por ese mismo
cliente re-entra en `onRequest` y pide el mismo Mutex, que no es reentrante. **La sesión se congela
sin excepción y sin línea de log.** Hoy no lo dispara nada; F10 es exactamente donde lo haría. Queda
en el KDoc del stack, que es donde lo va a leer quien lo cablee.

## Lo que NO se arregló, con nombre

Mismo criterio que ADR-0029: nombrarlo es lo que hace que se arregle.

- **Un fallo de autenticación GCM es indistinguible de «no hay entrada»** en el store de Android:
  los dos devuelven `null`. Falla cerrado, que está bien — pero **nunca reporta
  `SECURE_STORE_UNREADABLE`**, que es la señal que §8.16 quiere. Arreglarlo cambia la dirección de
  fallo de `SecureStore.get()` y toca a todos sus llamadores: es una tajada con su ADR, no una
  edición.
- La clave tier-1 **se reusa por alias sin validar sus parámetros**.
- Un **cambio de configuración** en Android reinicia la sesión de UI.
- El **techo de intentos fallidos vive en memoria** (ya nombrado en ADR-0032).
- `enroll()` **sigue sin llamador**, el desbloqueo de iOS **bloquea el hilo principal**, y la postura
  de caché de NSURLSession **no tiene ni test ni gate** aunque su KDoc afirme ambos.

## La lección de la tanda

**Cinco de los siete documentos falsos lo eran por la misma razón**: alguien cerró algo y la prosa
que lo pedía siguió pidiéndolo. No es descuido — es que **un número tiene quien lo vigile y una frase
no**. El hábito que lo corta ya está escrito en el vault y hoy se pagó otra vez: al cerrar algo,
`grep` de quién más lo estaba pidiendo, y cerrarlo ahí **en el mismo cambio**.
