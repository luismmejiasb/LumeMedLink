# 0011 · F2: las superficies que se ven sin desbloquear nada

**Fecha:** 2026-08-21 · **Encargo:** segundo slice de Fase A.

## El ataque, en lenguaje humano

Hay lugares donde tu teléfono muestra cosas **sin que nadie desbloquee nada**: la pantalla de
bloqueo, la barra de notificaciones, la lista de accesos directos del launcher, un widget en la
pantalla de inicio.

Una notificación que diga *«Recordatorio: tu control de diabetes»* aparece ahí, entera, para quien
tenga el teléfono en la mano. Eso es **revelar información de salud** bajo la Ley 21.719 — y la
amenaza que este repo pone primero (T2) es exactamente esa: el teléfono de un paciente lo usa su
familia. Ojo con lo contraintuitivo: la frontera de datos (ADR-0001) **no salva de esto**. La app
nunca guarda un diagnóstico, pero una notificación compuesta a partir del motivo de una cita lo
imprimiría igual.

Lo mismo con un widget («Próxima cita: Dr. Fulano, Oncología») o un acceso directo cuyo nombre sea
el de un paciente.

## Qué hice

**1. Ninguna de esas superficies existe, y ahora eso es una decisión, no un accidente.** Sin
widgets, sin accesos directos dinámicos, sin permiso de notificaciones, sin banderas para dibujarse
sobre la pantalla de bloqueo. Una superficie que la app no tiene no puede filtrar; cada una vuelve
por su propio slice revisado.

**2. El cimiento fuerte: hice la filtración imposible de escribir.** El tipo `PushSignal` es la
única forma que un push puede tomar en esta app, y **no tiene campo de texto**. Sólo un conjunto
cerrado de *razones para despertar a la app* (agenda cambió / sesión revocada / algo te espera) más
un identificador opaco que **valida al construirse y rechaza cualquier cosa con forma de prosa** —
«Control de diabetes» no puede convertirse en uno.

Esa es la diferencia entre una regla y un cimiento: «no pongas texto clínico en una notificación» es
una frase que alguien olvida; un tipo donde no hay dónde poner texto lo hace cumplir el compilador.
Y el texto que el usuario ve lo elige **la app** a partir de la razón, nunca el servidor — así
ningún backend, ni uno comprometido, decide qué aparece en una pantalla bloqueada.

**3. El gate `check-preauth-surfaces.sh`**, ensayado con **8 cebos**, todos vistos rojos:
NotificationCompat, texto de notificación con dato clínico, notificación pública en lock screen,
shortcut dinámico, widget, `setShowWhenLocked`, contenido de notificación iOS, y el permiso
`POST_NOTIFICATIONS` metido en el manifiesto. Más 6 tests del tipo `PushSignal`.

## El hallazgo que no buscaba, y que necesita tu decisión

**El push de Android choca de frente con el denylist de este mismo repo.** No lo supuse — lo probé:
metí `com.google.firebase:firebase-messaging` en un lockfile y el gate lo rechazó por **dos**
entradas (Firebase y Play Services), y `com.google.firebase.*` además está prohibido como import en
detekt.

El §8.1 escribió ese denylist pensando en Analytics/Crashlytics. **FCM es otro producto que entra
por la misma puerta** — y es el único transporte de push que Android ofrece. Consecuencia: hoy el
push en Android es **estructuralmente imposible** sin que el autor decida estrechar ese denylist, y
esa decisión no la toma un slice de notificaciones de pasada. Queda registrada como decisión
abierta.

**Y otra asimetría de plataforma, declarada:** el push de iOS es APNs, nativo, sin SDK de terceros y
sin colisión alguna. Así que un slice futuro de push podría salir en iOS mientras Android espera esa
decisión — consecuencia de producto, no sorpresa técnica.

## Lo que este slice NO puede demostrar

No hay push, ni widgets, ni notificaciones que probar corriendo. Lo verificado es: que las
superficies **no existen** (gate sobre código y manifiesto, con cebos), y que el tipo que las
gobernará **no admite contenido** (tests). El comportamiento en vivo de una notificación se
verificará el día que exista su slice — y llegará con el gate ya puesto, que es el punto.
