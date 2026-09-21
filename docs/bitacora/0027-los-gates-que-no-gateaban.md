# 0027 — Los gates que no gateaban

**2026-09-21.** Una auditoría exhaustiva (13 dimensiones en paralelo, cada hallazgo pasado por tres
refutadores) encontró 53 defectos vivos. Dos eran críticos, y los dos tenían la misma forma: **un
gate verde con su control borrado.**

## Lo que se pudo hacer con CI en verde

**Cebo 1 — cuatro atributos del manifiesto dentro de un comentario.** Saqué del `<application>`
`allowBackup`, `dataExtractionRules`, `usesCleartextTraffic` y `networkSecurityConfig`, y los dejé
dentro de un comentario XML de cuatro líneas. El archivo sigue bien formado. `check-network-posture`
verde. `check-backup-posture` verde. Eso es **F6 y F12 enteros** — el backup en la nube, el backup
entre teléfonos, el veto de tráfico en claro y el anclaje de confianza al almacén del sistema —
borrables sin que nada chiste.

La causa: los dos filtraban comentarios **línea a línea** (`grep -v '<!--'`). Un comentario
multilínea lleva el marcador sólo en la primera.

**Cebo 2 — los controles de ventana, mudados a un test.** Borré de `MainActivity` las llamadas a
`FLAG_SECURE`, `filterTouchesWhenObscured`, `denyAutofillExport()` y `denyContentCapture()`, y puse
esas cadenas en una `listOf` dentro de un archivo de test que ni siquiera corre.
`check-screen-security` verde. `check-input-surfaces` verde. Eso es **F1 y F3**.

La causa: `grep -r <token> androidApp/src`, y `androidApp/src/test` está dentro.

## Lo que hace que esto duela

**Ninguno de los dos era una regresión.** Los dos estaban ciegos **desde que nacieron**. Y la regla
que debía haberlos atrapado —«cada gate se ensaya con archivo-cebo antes de confiar en su verde»,
la lección fundante de esta familia— **sí se siguió**.

La siguió quien acababa de escribir el gate. Ahí está la debilidad: **el autor de un gate escribe el
cebo que su gate atrapa**, porque es la misma cabeza. Los dos huecos los encontró alguien que no
sabía qué había querido probar el gate.

Y el ensayo era un **acto**, no un **artefacto**. Ocurrió una vez, quedó anotado en una bitácora, y
nada volvió a repetirlo nunca.

## Qué se cambió

Se arregló la **clase**, no las dos instancias: el mismo defecto estaba en **seis** gates.

- `Scripts/lib/uncomment.py` — despoja comentarios y, opcionalmente, literales de cadena, con un
  tokenizador. Los blanquea en su sitio, así que los números de línea sobreviven y el mensaje de
  fallo sigue apuntando a la línea real. Maneja bloques anidados (Kotlin y Swift los anidan) y
  escapes dentro de cadenas.
- `Scripts/lib/xmlattr.py` — lee un atributo **parseando** el XML. Un parser no ve comentarios, así
  que ese cebo se vuelve imposible por construcción. De paso cierra un hueco que el grep nunca
  cubrió: `android:allowBackup` en un `<activity>` no es la postura de la app, pero es el mismo texto.
- Los seis gates ahora exigen **la forma de la llamada** (no el token), **en `androidApp/src/main`**
  (no en `test/`), **fuera de comentarios y de literales**.

## Un tercer hueco que apareció escribiendo el ensayo

`check-ios-host.sh` exigía la **presencia** de `shouldAllowExtensionPointIdentifier`. Dejé el método
en su sitio y le cambié el cuerpo a `return true` — todos los teclados de terceros admitidos — y el
gate siguió verde. Exigía que el hook **existiera**, no que **rechazara**. Un hook que responde que
sí a todo es el default con pasos de más. Ahora se exige el rechazo.

## Y el ensayo se vuelve artefacto

`Scripts/rehearse-gates.sh`: borra o disfraza cada control, en las grafías que de verdad engañaron a
estos gates, y **exige que el gate se ponga rojo**. Quince cebos, los quince atrapados. Corre en CI,
al final, después de los gates que ensaya.

Es el gate de los gates: el único paso que puede fallar **porque otro paso no puede fallar**.

## El instrumento mintió primero, otra vez

El primer borrador del ensayo construía un `git worktree` de **HEAD** mientras los arreglos estaban
sin commitear. Reportó **once cebos sin detectar** contra gates que ya estaban reparados.

Es exactamente el defecto de artefacto rancio que estos gates existen para prevenir, un nivel más
arriba. Ahora mide el árbol de trabajo. Lo anoto porque la conclusión equivocada llegó con la
confianza intacta, y sólo la cazó que el resultado fuera *demasiado* malo para ser cierto.

## Lo que NO cierra

La auditoría encontró la misma familia en gates que este cambio no toca: `check-data-boundary` sólo
ve declaraciones `val`/`var` y no conoce la palabra «motivo»; `check-wrapper` verifica
`distributionSha256Sum` por **presencia**, no por valor; el chequeo de pineo de acciones acepta
`@latest`; un paquete llamado `core` dentro de `androidApp` desactiva `ForbiddenImport`. Cada uno
necesita su cebo y su arreglo. Quedan nombrados en ADR-0029 para que cerrar esto no se lea como
cerrar la clase.

ADR-0029.
