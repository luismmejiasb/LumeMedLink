# 0037 — La postura de NSURLSession, medida en la sesión que el engine crea de verdad

**Tipo:** `fix` + `test` + `ci` · 2026-10-07

## Qué llega

- **Tarea `0004` cerrada.** `applyLumeSessionPosture()` (antes `applyLumeCachePosture()`) apaga, además de la caché,
  el almacén de credenciales compartido, el de cookies y el envío de cookies.
- **`DarwinSessionPostureTest`**: el primer test que mira la `NSURLSession` que el engine real arma.
- **La mitad iOS de `check-network-posture.sh`**, con un helper nuevo, `Scripts/lib/kfun.py`, y siete cebos. El ensayo
  pasa de 40 a 47, todos rojos.
- Enmienda de ADR-0016.

## Por qué el test no podía ser el obvio

El obvio era aplicar la postura a una configuración y leerla. Eso prueba una función que este repo ve, y no lo que la
puede romper: la sesión se arma **dentro de Ktor**, que toma la configuración por defecto, aplica lo suyo y recién al
final corre nuestro bloque. Un upgrade que moviera uno de sus ajustes después del nuestro pasaría ese test y desharía
la postura en silencio.

El único objeto de la sesión viva que Ktor devuelve hacia afuera es el que le pasa a un manejador de desafíos. Así que
el test levanta un servidor HTTP mínimo en 127.0.0.1 que contesta 401 con `WWW-Authenticate: Basic`, el manejador recibe
la `NSURLSession` real, guarda su `configuration` y cancela. Nada sale del dispositivo, y ATS no gobierna una IP.

Dos detalles leídos en el IR de Ktor, no medidos: el engine elige la cola principal como cola del delegado cuando se
construye en el hilo principal (por eso el test lo construye fuera: si no, podría esperarse a sí mismo), y su
`sessionConfig` está deprecado en favor de `configureSession`, así que leerlo desde afuera no es una puerta.

## Visto rojo, uno por uno

El reporte dijo 0,002 s para un test que levanta un servidor y hace una petición, y un verde así no se cree. Con la
postura intacta: verde. Sin la caché, sin el almacén de credenciales, con el envío de cookies encendido, y con el
almacén de cookies compartido puesto después del de Ktor: rojo, cada uno con su mensaje. Sin nuestra línea de cookies:
**verde** — que es lo esperado y además una medición nueva: la tarea daba como «no medido» el argumento con que Ktor
anula ese almacén, y ahora está medido. Nuestra línea es un seguro, y el test es lo que avisa el día que el seguro sea lo
único en pie.

## El gate, y el cebo que mintió

El gate afirma las llamadas **dentro de las funciones que producción ejecuta**, no en cualquier parte del archivo:
`platformHttpEngine()` debe construir `lumeDarwinEngine()` sin agregar nada, ese engine debe registrar la postura, y la
postura debe llamar cada setter una vez y con su valor. El helper `kfun.py` extrae el cuerpo de una función con
comentarios y literales en blanco; la misma palabra en un helper que nadie llama, en un comentario o en un test no
cuenta.

De los siete cebos, uno salió verde en la primera corrida: el que daba vuelta `setHTTPShouldSetCookies(false)`. El gate
tenía razón — el cebo había reemplazado la primera aparición del texto, que es la del KDoc que cita la línea. Ahora el
ancla de un cebo tiene que ser única en su archivo, o el cebo no se aplica.

## Lo que no se verificó

Lo mismo que ADR-0016 ya decía: el stack completo no llegó nunca a un host real, ATS no se observó en un dispositivo, y
no se miró qué escribe el sistema a disco. Esto verifica la configuración que recibe NSURLSession, no lo que hace con
ella.
