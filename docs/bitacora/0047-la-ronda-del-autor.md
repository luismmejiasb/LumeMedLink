# 0047 — La ronda del autor: biometría para toda la familia, un solo teléfono, y lo que el kit tiene que tocar

**Tipo:** `decisión` + `feature` + `medición` · 2026-10-07

## Lo que decidió el autor, y dónde quedó

| Pregunta | Respuesta | Dónde |
| --- | --- | --- |
| ¿Sin biometría no hay sesión? | Sí, en toda la familia; un teléfono **sin hardware** se valida por SMS y correo | Enmienda de ADR-0037; tarea `0026` (FREEZE) |
| `0019`, error pasajero | B: sigue bloqueada, sin contar, sin cerrar | ADR-0040, construido |
| `0016`, teléfono compartido | No se comparte: un teléfono por cuenta, como un banco; otra sesión cierra la anterior y avisa | ADR-0041; `backend-requests/0007`; tarea `0027` (FREEZE) |
| `0021`, accesibilidad | B: no se leen datos sensibles, sí rótulos y textos orientativos | ADR-0042; espera al kit |
| `0022`, portapapeles iOS | B: sólo local, vence a los 2 minutos | ADR-0042; espera al kit |
| Lo que espera backend desplegado | Freeze | `tareas/FREEZE/` |

Interpretación propia que el autor puede corregir: «sin capacidad» es **sin hardware**, no «sin huella enrolada» — si
no, no enrolar sería el interruptor que apaga la biometría; y el respaldo exige **los dos** canales.

## `NotNow`

Un resultado nuevo del gate. Mapeado desde exactamente lo que la tarea `0019` nombró: `ERROR_TIMEOUT` y
`ERROR_HW_UNAVAILABLE` del prompt, `BIOMETRIC_ERROR_HW_UNAVAILABLE` del chequeo previo (que antes terminaba la sesión
igual que «sin hardware»), y en iOS `errSecInteractionNotAllowed`/`errSecNotAvailable`. Sin hardware, sin enrolar o con
actualización pendiente siguen siendo `Unavailable`; lo no clasificado sigue contando.

## La medición de accesibilidad

Un verificador nuevo, `Scripts/verify-accessibility-sensitivity.sh`: pone un campo sintético en el ingreso y un test de
dispositivo lee el árbol vivo con `UiAutomation` (API 37). La primera versión no vio nada — `uiAutomation.windows` viene
vacío sin la bandera de ventanas interactivas; con `rootInActiveWindow` sí. Resultado, con control positivo:

- un nodo propio marcado directamente con `isSensitiveData` → `isAccessibilityDataSensitive = true` (Compose traduce la
  propiedad y el instrumento la ve);
- la marca pasada como `modifier` del campo del kit → el nodo editable sigue en `false`, igual que sin marca;
- el rótulo → `false`, como el autor quiere.

La marca en la raíz no marcaba nada, así que **no se dejó**: un control que se lee como control y no lo es es el
defecto que esta familia más repite. Se pidió al kit una forma de marcar sólo el nodo del valor, y lo mismo para el
portapapeles local de iOS. El verificador queda en rojo por exactamente esa razón.

## FREEZE

`tareas/FREEZE/` como en el backend, con la regla en la constitución (§10). El gate de números ahora recorre las tres
carpetas — antes, un archivo numerado en `FREEZE/` habría sido un espacio de números que nadie recorre, y el gate lo
habría rechazado — con un cebo nuevo: una tarea congelada que reusa el número de una cerrada.
