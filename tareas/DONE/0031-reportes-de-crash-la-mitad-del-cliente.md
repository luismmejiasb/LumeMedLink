# 0031 · Reportes de crash propios — la mitad del cliente

> **Estado:** DONE (abierta y cerrada 2026-10-12) · ADR-0051 · pedido al backend `0009`, escrito y sin enviar

## De dónde sale

El autor eligió, el 2026-10-12, saber de los crashes con reportes propios a nuestro backend, sin Crashlytics.
Decidió por formulario:

- el reporte lleva el tipo y dónde en nuestro código;
- lleva una clave propia de crashes, sólo con consentimiento, y sin clave antes de consentir;
- se manda apenas abre la app, sin sesión;
- la mitad del cliente se construye ahora, y el pedido al backend espera.

## Qué se hace

1. **El reporte** en `core/`: un tipo con forma cerrada que no tiene campo para el mensaje (ADR-0051 §1), y una
   función que lo arma desde una excepción, quedándose con hasta ocho marcos de `com.luismejias.lumemedlink`.
2. **La captura**, por plataforma:
   - Android: el manejador por defecto, que encadena al anterior;
   - iOS: el hook de excepciones no manejadas de Kotlin/Native.

   Escribe el archivo y devuelve el control. Nunca lanza.
3. **La cola**: hasta tres archivos en el directorio propio, fuera del backup. Se borran al ser aceptados o a los siete
   días. Un intento por apertura, sin reintento.
4. **La clave de crashes**: se crea al consentir, se borra al retirar el consentimiento y en el logout. Va en el
   contrato de logout con su test.
5. **El consentimiento**: una tercera fila **opcional** en 2b y un interruptor en Configuración. Necesita sus pantallas
   y sus textos (es/en), y una desviación nueva en ADR-0044.
6. **El transporte**: uno que no manda nada, con nombre honesto, hasta que exista el endpoint. Después, el stack
   endurecido sin `Authorization` ni `X-Lume-Installation`.

## Qué NO hacer

- Ningún SDK de terceros: el denylist del §8.1 no se toca.
- Jamás el mensaje de la excepción, ni el de su causa, en el reporte ni en un log.
- No reusar el canal de eventos de seguridad.

## Cómo se verifica

- Un test con una excepción cuyo mensaje lleva un RUT y una URL, que exige que ninguno aparezca en el reporte
  serializado.
- Un test de que no hay clave sin consentimiento y de que el logout la borra; y de que la clave se pone al enviar, no
  al capturar (un reporte en cola antes del logout sale sin ella).
- `check-backup-posture.sh` aprende los archivos de la cola.
- Un test de que la cola no pasa de tres y de que un archivo de más de siete días se descarta.
- En el dispositivo: forzar un crash, reabrir y ver el reporte en la cola, en Android y en iOS.

## Cómo quedó (2026-10-12)

- `core/crash/`: el reporte cerrado (`CrashReport`, sin campo para el mensaje), la cola (`CrashQueue`, tres y siete
  días), el consentimiento y la clave (`CrashConsent`, en el almacén seguro, así que el logout los borra), el envío
  (`flushCrashReports` con `NoEndpointCrashReportSender`) y la captura por plataforma.
- 2b tiene la tercera fila, opcional; Configuración, el interruptor «Reportes de error».
- Tests: el mensaje con RUT, URL y nombre no viaja; sólo marcos propios y a lo sumo ocho; tres y siete días; la clave
  se pone al enviar (un reporte de antes del retiro sale sin ella); el logout la borra; el envío se intenta una vez y
  no lanza; el símbolo interno de iOS.
- `check-backup-posture.sh` aprendió la cola, con dos cebos.
- **Visto en el simulador y en el emulador** con un crash forzado (un build temporal, jamás commiteado): el archivo
  quedó en la cola sin el mensaje, con el tipo y un marco propio; en iOS la carpeta lleva la marca de exclusión del
  backup.
- Falta, del lado del backend, el endpoint (pedido `0009`, sin enviar). Y lo declarado en ADR-0051: los crashes
  nativos y los ANR no se capturan.

