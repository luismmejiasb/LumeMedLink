# 0032 · Firebase acotado — Crashlytics, Analytics, Remote Config, Messaging y App Distribution

> **Estado:** PENDING (abierta 2026-10-13) · ADR-0052 · deroga ADR-0051 · reemplaza el punto 1 de ADR-0038
>
> **Avance 2026-10-13:** hechos los puntos 1 a 6 y el 8 —doctrina, gates con sus cebos, `core/telemetry`, los dos
> lados y el script de App Distribution—, verificados sin configuración en el simulador y el emulador. **Falta el 7, el
> push**, y lo que necesita del autor.

## De dónde sale

El autor derogó la prohibición de Firebase el 2026-10-13 y decidió por formulario:

- Crashlytics reemplaza a los reportes propios, y se reutilizan la fila de 2b y el interruptor de Configuración;
- Crashlytics y Analytics recolectan sólo con consentimiento;
- Analytics manda un catálogo cerrado;
- la integración usa los SDK nativos de cada plataforma, detrás de interfaces de `core/`.

## Qué se hace

1. **Doctrina:** ADR-0052, la constitución (§0, §8.1, §8.8), ADR-0051 derogada, ADR-0038 en parte reemplazada, y el
   pedido `0009` retirado.
2. **Gates, con sus cebos en `rehearse-gates.sh`:**
   - `check-dependency-allowlist.sh` deja de negar Firebase como grupo y niega por nombre los productos fuera de
     ADR-0052;
   - un gate nuevo lee el `Package.resolved` de iOS;
   - un gate nuevo exige que Analytics se llame sólo desde su adaptador, sólo con nombres del catálogo, y que no
     exista `setUserId`, propiedades de usuario, `setCustomKey` ni `log` de Crashlytics.
3. **Lo común, en `core/telemetry/`:**
   - el consentimiento (lo que era `CrashConsent`, ahora sin la clave);
   - las interfaces `CrashReporting`, `Analytics` (que sólo acepta un `AnalyticsEvent`) y `RemoteFlags` (que sólo
     acepta un `RemoteFlag` con su valor por defecto);
   - los no-op con nombre honesto, para cuando no hay configuración.
4. **Se borra lo de ADR-0051:** la captura, la cola, el transporte, la clave, sus tests y su gate de backup.
5. **Android:**
   - los artefactos del BoM 35.0.0;
   - Firebase iniciado en código desde `google-services.json` fuera de git, sin el plugin;
   - los flags del manifiesto: todo apagado de fábrica, sin `AD_ID` y sin `FirebaseInitProvider`;
   - el manejador que le entrega a Crashlytics una copia sin mensajes.
6. **iOS:**
   - los paquetes SwiftPM de `firebase-ios-sdk` 13.0.1;
   - Swift implementa las interfaces y las inyecta en `MainViewController`;
   - las claves del `Info.plist`, todo apagado de fábrica;
   - el hook de excepciones de Kotlin registra en Crashlytics con el parser de marcos ya existente y sin razón.
7. **Push (mitad cliente):** el token después del login y borrado en el logout, payload sólo de datos y el texto de
   la app. No se manda a ningún lado mientras los pedidos al backend sigan pausados.
8. **App Distribution:** un script con la CLI de Firebase, sin plugin de Gradle y sin SDK en el binario.

## Qué NO hacer

- Ningún producto de Firebase fuera de los cinco.
- Jamás un parámetro libre en un evento, un user ID, ni el mensaje de una excepción.
- Remote Config no apaga controles de seguridad y no es el kill switch.
- No subir a git los archivos de configuración de Firebase.

## Lo que necesita del autor

- Crear el proyecto de Firebase y registrar las dos apps (`com.luismejias.lumemedlink`).
- Bajar `google-services.json` y `GoogleService-Info.plist` y dejarlos donde indica el README de configuración.
- Subir la llave de APNs para el push de iOS.
- La línea del aviso de privacidad que nombra a Google como encargado y la transferencia internacional.

## Cómo se verifica

- Gates y ensayo de cebos en verde, incluidos los nuevos.
- Tests de Android e iOS en verde.
- Sin archivos de configuración: la app arranca en los dos simuladores y Firebase no se inicia.
- Con configuración: un crash forzado llega a la consola de Crashlytics sin mensaje, y un evento del catálogo llega a
  DebugView. Esto queda para cuando exista el proyecto.
- Antes de publicar: una captura de red de lo que la app manda a Google, por producto, para el modelo de amenazas.
