# 0040 — La clave tier 1 dice por su nombre cómo se hizo

**Tipo:** `fix` · 2026-10-07

## Qué llega

- **Tarea `0008` cerrada.** Una clave tier 1 hecha sin `unlockedDeviceRequired` ya no se reusa donde ese parámetro existe.
- Enmienda de ADR-0009; `Tier1KeyRotationOnDeviceTest`; los tests del logout en dispositivo, corregidos para no quedar
  vacíos.

## El arreglo que la tarea pedía no existía

La tarea decía: leer el `KeyInfo` de la clave y rotarla si no cumple. Fui a buscar el accesor y **no está**: `KeyInfo`
reporta si la clave pide autenticación, por cuánto tiempo, si la invalida un enrolamiento, su nivel de seguridad… y nada
sobre `unlockedDeviceRequired`. El parámetro cuya ausencia es el defecto es justo el único que no se puede leer de vuelta.

Así que la procedencia va en el nombre. La clave con el parámetro vive bajo un alias nuevo que sólo se crea con él, y
cualquier clave bajo el alias viejo es, por definición, una sin él. En API 28+ se retira con lo que cifró.

## El test que se habría vuelto vacío

Los tests del logout en dispositivo terminaban afirmando que el alias `lume_session_tier1` ya no existe. Después de este
cambio, en API 28+ ese alias **no existe nunca**: la aserción pasaba sin haber mirado la clave que el logout debe borrar.
Es el mismo par de tests que ya mintió una vez (bitácora 0028), y por la misma familia de razón: una aserción de
**ausencia** escrita sobre un nombre pasa sola el día que el nombre cambia. Ahora afirman todos los alias posibles, y antes
exigen que la clave en uso exista. El cebo del wipe viejo —que borraba sólo el alias viejo— sólo lo atrapan así.

## Lo que no se puede probar acá

La actualización del sistema operativo. El test siembra su resultado: una clave bajo el alias viejo, hecha sin el
parámetro, con un valor cifrado en el formato del almacén — y un control comprueba primero que la siembra se lee. El
almacén no distingue una clave sembrada de una heredada, que es exactamente lo que este arreglo asume: el alias es la
única evidencia que hay.
