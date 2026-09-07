# Plan de fortificación — LumeMedLink

> El índice maestro del programa de seguridad. Aprobado por el autor el 2026-08-21. Cada slice es
> una **sesión completa**: verifica lo que exista, fortifica lo que falte, y deja un **cimiento que
> no se pudre** — un ADR, un gate visto rojo con cebo, tests, y su entrada en el threat model.
>
> **No promete invulnerabilidad** — ninguna app la tiene, y afirmarla violaría §14. Promete subir el
> costo de cada ataque y encoger la superficie, con cada afirmación respaldada por un test o un gate.
> Se prioriza contra `threat-model.md` (T1–T6 + la violación de frontera, la clase sobre Crítico).
>
> Estado: ⬜ sin empezar · 🟡 en curso · ✅ cerrado · 🔒 bloqueado (shell/backend).

## Fase A — El teléfono en manos equivocadas (T1, T2, T3)

| # | Slice | Estado | Nota |
| --- | --- | --- | --- |
| F1 | Captura de pantalla y multitarea (FLAG_SECURE / cover iOS / tapjacking) | 🟡 2026-08-21 | Núcleo hecho y **verificado en device (Android)**: `screencap` da negro puro sobre la app, normal sobre el launcher (bitácora 0009). **Cover iOS de ventana: EXISTE desde 2026-08-25** (ADR-0025) — `UIWindow` propia sobre `.alert`, armada en `willResignActive`, el último instante antes del snapshot; gate `check-ios-host.sh` con cebo. **Y SIGUE SIN VERIFICAR**: el simulador no puede probarlo — con AMBOS covers desactivados el resultado es idéntico por tres métodos distintos (Compose renderiza por Metal), así que el control positivo no se puede hacer fallar. Necesita device real (bitácora 0023). ADR-0010, bitácora 0008. |
| F2 | Superficies pre-auth (notificaciones sin contenido, widgets, pantalla bloqueada) | ✅ 2026-08-21 | Ninguna superficie existe (decisión, ADR-0012) + `shared/PushSignal` **sin campo de texto** (la fuga es imposible de escribir) + gate `check-preauth-surfaces.sh` con 8 cebos + 6 tests. **Hallazgo: el push de Android choca con el denylist propio (FCM = Firebase) — decisión abierta del autor.** Bitácora 0011. |
| F3 | Portapapeles y teclado | ✅ 2026-08-25 | La app **no ofrece copiar** (gate rechaza clipboard y `SelectionContainer`) + **una sola pieza de entrada** `core/input/SensitiveTextField`, con endurecimiento **por propósito**. Reabierto y **cerrado** el 2026-08-25 con los tres canales de exportación de estructura del SO (ADR-0024, bitácora 0022): **autofill** estaba abierto —todo campo de Compose publica `ContentDataType.Text` sin opt-out— y se cierra desde el **ancestro**, porque asignarlo en la vista de Compose se descarta en silencio; **content capture** ya estaba cerrado por FLAG_SECURE y se le agrega la bandera propia, sin presentarla como el arreglo; **assist** está vacío por construcción. Gate ampliado a allowlist tras que su propio cebo lo cruzara (mismo error de clase que F20), re-cebado en 7 grafías. Probado en device con cebo: sin el arreglo, 2 tests rojos. Residual declarado: petición manual, modo de compatibilidad y PCC re-admiten la vista. **Cola iOS CERRADA 2026-08-25**: el veto de teclados de terceros (`shouldAllowExtensionPointIdentifier`) vive en el host y lo cubre `check-ios-host.sh` con cebo — la única asimetría a favor de iOS, aplicada. |
| F4 | Bloqueo por inactividad + gate biométrico anclado a clave (tier 2) | 🟡 2026-08-21 | **Mecanismo completo**: EC en Keystore firmando un reto (Android) / Keychain `.biometryCurrentSet` (iOS), política en `SessionLock` con 13 tests × 2 targets, gate `check-biometric-contract.sh` **ensayado con 8 cebos**, y **4 tests instrumentados verdes en Android real** confirmando vía `KeyInfo` que las propiedades quedaron aplicadas. ADR-0011, bitácora 0010. **Y la propiedad del tier PROBADA en device con control** (`Scripts/verify-tier2-invalidation.sh`): enrolar una huella nueva destruye la clave — el control exige que la fase B falle antes de creerle (bitácora 0014). **Falta sólo** que la app *navegue* hasta `Locked` (necesita login) y el equivalente iOS (sin host). |

**Fase A cerrada el 2026-08-21** (F1, F2, F3 y el mecanismo de F4); **F3 reabierto y re-cerrado el 2026-08-25**
con los canales de exportación de estructura del SO (ADR-0024). Colas atadas al host iOS: el cover de ventana
(F1) y el veto de teclados (F3). Cola atada al login: el end-to-end de F4.

## Fase B — Lo que sobrevive (T3, T4)

| # | Slice | Estado | Nota |
| --- | --- | --- | --- |
| F5 | Logout = borrado total, verificado | ✅ 2026-08-21 | **Verificado contra el Keystore REAL** (`LogoutWipeOnDeviceTest`, 4/4 en device): ida y vuelta, **el texto en claro no está en disco**, el logout no deja nada legible, y el wipe borra archivos **y la clave** (lo que vuelve irrecuperable cualquier copia suelta). Cierra el hueco que declaró la bitácora 0007. Secretos ahora enumerables (`SecureStoreKey`), con el test del wipe iterando el enum → un secreto futuro queda cubierto al declararse. **Declarado: es un logout LOCAL, sin revocación server-side** — la UI no puede insinuar efecto remoto. ADR-0014, bitácora 0015. |
| F6 | Sin backup / sin sincronización | ✅ 2026-08-21 | **Se encontró un agujero real y vivo**: a targetSdk≥31 Android **ignora `allowBackup`** para migración device-to-device (`IGNORE_ALLOW_BACKUP_IN_D2D`) — medido: nuestro paquete emitió `progress: …3072/1024` + `Success` bajo D2D. Cerrado con `dataExtractionRules` (9 dominios × 2 secciones), gate `check-backup-posture.sh` (4 cebos + el typo que delató su propio falso verde) y `verify-no-backup.sh` con **control en vivo** que exige que la fuga reaparezca. `<cross-platform-transfer>` prohibida (es opt-in). iOS: **no hay archivo que marcar** — doc corregida. ADR-0015, bitácora 0016. |
| F7 | Sentinel de instalación iOS (secretos heredados) | ⬜ | **DESBLOQUEADO 2026-08-25**: el host iOS existe (ADR-0025). Slice propio, sin empezar. |
| F8 | Caché en reposo cifrada y purgable | ✅ 2026-08-21 | **Decidido antes de que exista la caché, que es cuando es barato**: cualquier byte en reposo pasa por el `SecureStore` de `core/session` — que ya cifra bajo clave no exportable, ya vive en un directorio excluido de backup y transferencia, y ya lo borra el logout **verificado en hardware**. Una segunda ruta tendría que re-ganar las tres y fallaría en silencio: ya lo vimos con la caché de NSURLSession. Gate P4 (3 cebos rojos). `isExcludedFromBackup` llega con el primer archivo, no antes. ADR-0022. |

## Fase C — Identidad y sesión (T5, escalada)

| # | Slice | Estado | Nota |
| --- | --- | --- | --- |
| F9 | El token que no puede abrir la ficha | 🟡 2026-09-07 | **Re-baseline 2026-09-07 contra contrato 0.32.0.** **El backend lo CONSTRUYÓ el 2026-08-27** (no sólo aceptado): `IDP_PATIENT_TENANT_ID` obligatoria sin default ni escape de dev, marca de ruta con `@ClientTier`, **ausencia de marca = rechazo**, desajuste = **404 que jamás anuncia el alcance**. Verificado en el contrato: existen 6 operaciones bajo `/v1/patient/*` más 3 de portal-access del lado clínica. **Lo que falta ya no es del backend**: verificarlo exige un token, y no hay proyecto GCP (§F11). Consecuencia dura para la app, anotada: un 404 inesperado va a parecer un endpoint que falta, y no lo es. |
| F10 | Tokens cortos, refresh que rota, sin replay | 🟡 2026-09-07 | **Re-baseline 2026-09-07 contra contrato 0.32.0.** Lo verificable del lado cliente sigue verificado (single-flight, rotación, el gastado no sobrevive a un reinicio). **El bloqueo cambió de causa**: `ADR-0036` ya está construido, así que el `RefreshClient` HTTP no lo espera a él — espera (a) que exista un proyecto GCP, que **sigue sin existir**, y (b) la decisión del autor sobre si la app refresca directo con Identity Platform o el backend intermedia. El `≤15 min` sigue siendo doctrina sin política desplegada, por la misma razón. |
| F11 | Login y MFA endurecidos | 🔒 2026-09-07 | **Re-baseline 2026-09-07 contra contrato 0.32.0.** **El backend ya no es el bloqueo** — construyó `ADR-0036` el 2026-08-27. El bloqueo real, verificado en el tablero: **no hay proyecto GCP, ni staging, ni producción**. No hay contra qué autenticarse. Sigue faltando la verificación externa de que una segunda audiencia emita el claim `sign_in_second_factor: totp`, y esa pregunta es del backend. |

## Fase D — Red y transporte (T5)

| # | Slice | Estado | Nota |
| --- | --- | --- | --- |
| F12 | Stack de red endurecido + cleartext negado en el sistema | ✅ 2026-08-21 | **Dos defectos vivos cerrados**: (1) la caché de NSURLSession escribía **el bearer token y el cuerpo** a disco en claro, sobreviviendo al logout; (2) el log «redactado» conservaba el path, donde van los ids de paciente — ahora redacción **por construcción** (constructor privado + allowlist de segmentos). Android declara al sistema: cleartext negado **y anclas de confianza sólo del sistema** (cierra el MITM por CA de MDM/malware). Gate `check-network-posture.sh` sobre el manifiesto **FUSIONADO** (7 cebos) — el único que ve lo que inyectan las dependencias. `ForbiddenImport` ampliado con los agujeros nombrados. **Tres afirmaciones falsas corregidas** y cuarta asimetría añadida al threat model. **El stack sigue sin abrir un socket real.** ADR-0016, bitácora 0017. |
| F13 | Nada sensible en URLs; pinning re-evaluado | 🟡 2026-08-21 | **F12 le dio urgencia al pinning**: **Mitad de URLs ✅**: doctrina + gate `check-url-hygiene.sh` (4 cebos rojos, 2 casos legítimos verdes), ADR-0017. **Mitad de pinning: DECISIÓN ABIERTA DEL AUTOR** — no se re-decidió aquí. Lo que cambió: la 4ª asimetría lo mueve de T5 a T1/T2 en iOS. Lo que NO cambió: **no es implementable** (sin certificado y sin host iOS). Opción intermedia hallada: iOS *puede* distinguir ancla de sistema de CA del usuario (`kSecTrustResultUnspecified` vs `Proceed`) — cerraría la asimetría sin el costo de rotación, pero la evidencia es de simulador y **no verificada por esta sesión**. Bitácora 0019. | Gate anti-datos-en-URL; decisión de pinning documentada. |

## Fase E — Contrato y autorización (T5, frontera)

| # | Slice | Estado | Nota |
| --- | --- | --- | --- |
| F14 | La frontera de datos como gate ejecutable | ✅ 2026-08-21 | Gate de **nombres** que dice que lo es (`check-data-boundary.sh`, ES+EN, 4 cebos rojos / 2 legítimos verdes) — mueve el §13 de [manual] a [lint parcial]. Y la otra mitad es **pedido de contrato**, no poda del cliente: `backend-requests/0002` pide la proyección no clínica **como recurso propio** — podar en el cliente igual habría hecho cruzar los bytes clínicos por la red. ADR-0019, bitácora 0021. |
| F15 | Restricción de tratamiento en rutas tenant-scoped (T11) | 🔒 2026-09-07 | **Re-baseline 2026-09-07 contra contrato 0.32.0.** **La causa del bloqueo cambió y es más precisa.** No espera un ADR: espera una **ruta que no existe**. Verificado en el contrato 0.32.0 — sólo hay citas **por paciente** (`/v1/orgs/{tenantId}/patients/{patientId}/appointments`); la agenda del día tenant-scoped que T11 pone en riesgo, y que es la pantalla principal de S1.3, **no está construida**. El propio tablero del backend la registra como deuda hacia esta app. |
| F16 | IDOR (404-no-403), reagendar atómico (T7), idempotencia (T13) | 🔒 2026-09-07 | **Re-baseline 2026-09-07 contra contrato 0.32.0.** **Confirmado leyendo el contrato, ya no citado de segunda mano.** (1) **Cero cabeceras de idempotencia** en las 94 rutas — T13 sigue viva tal cual. (2) **No existe ninguna ruta de reagendar** — T7 sigue viva: reagendar sigue siendo cancelar + re-reservar, con la ventana donde el cupo queda libre. (3) `bookMyAppointment` responde **409**, así que la colisión de cupo sí es distinguible. La postura online-only y el no-reintento en POST quedan confirmados como correctos, no como precaución. |

## Fase F — Entrada, contenido y enlaces (T6)

| # | Slice | Estado | Nota |
| --- | --- | --- | --- |
| F17 | Deep links / universal links seguros | ⬜ | **DESBLOQUEADO 2026-08-25**: el host iOS existe (ADR-0025). El plist ya niega `CFBundleURLTypes` con gate; los universal links son el slice. |
| F18 | Contenido no confiable no rompe la app | ✅ 2026-08-21 | Decodificación tolerante (un campo desconocido cuesta una fila, no la página) + **el 2xx con HTML se rechaza en la validación** (portal cautivo, F12) + bytes remotos sólo por el stack, y **cualquier escritura en disco fuera de `core/` falla** (P4, ADR-0022). Los cargadores de imágenes con red propia siguen en el denylist (ADR-0018). |
| F19 | Cero entrega de documentos | ✅ 2026-08-21 | Ampliado de «sin share sheet» a **ninguna vía de entregar un archivo**: impresión (Android e iOS), creación de documentos, document pickers, MediaStore, chooser. 5 cebos rojos. |

## Fase G — Cadena de suministro e integridad del binario (T6)

| # | Slice | Estado | Nota |
| --- | --- | --- | --- |
| F20 | Dependencias bajo control | ✅ 2026-08-21 | **Siete huecos, medidos**: el prefijo admitía `androidx.health.connect` (API de datos CLÍNICOS) — ahora coincidencia **exacta** y denylist por nombre; el **classpath de plugins** no estaba en ningún lockfile (24 grupos nunca revisados, incl. `org.tensorflow`) — ahora lockeado, 441→500 módulos; **el `gradle-wrapper.jar` era el de 9.4.1 con la distribución pinneada a 9.7.1** (la raíz de confianza) — regenerado y con gate; un comentario podía inyectar un grupo; borrar un lockfile dejaba verde; `org.apache.http` y `org.slf4j` importables contra §7/§8.1; actions por tag mutable. **Verificación por bytes NO adoptada**, con su costo y su disparador escritos. ADR-0018, bitácora 0020. |
| F21 | Integridad del binario y del runtime | 🟡 2026-08-21 | **Mitad construible hecha**: no había bloque `buildTypes` — ambos ahora declaran su postura, `release` con `isDebuggable=false`, y gate (ADR-0021). `isMinifyEnabled` off **como decisión** (R8 en KMP+Compose sin keep rules probadas es peor riesgo). **Bloqueado el resto**: Play Integrity y App Attest se verifican en servidor y no hay backend desplegado; App Attest además necesita el host iOS. **Hallazgo de F6: no hay bloque `buildTypes` en ningún Gradle**, así que debug es debuggable por default de AGP y **ningún gate exige `isDebuggable=false` en release** — y el build debug es el que el autor sideloadea con token real para verificar en device (`run-as`/`adb pull` lo alcanzan). |

## Fase H — Lo invisible (T4)

| # | Slice | Estado | Nota |
| --- | --- | --- | --- |
| F22 | Logging redactado + cero telemetría fugada | ✅ 2026-08-21 | **Un solo punto de logging** en `core/logging`, con conjunto **cerrado** de eventos — no acepta `String`, así que un nombre de paciente no tiene dónde ir. **El default escribe NADA, como decisión**: lo que va a logcat sale del dispositivo dentro de un `adb bugreport`, en release, y sobrevive al reboot. Dos gates (detekt + `check-logging.sh`, 5 cebos rojos), porque un ban de imports no ve una llamada calificada ni `printStackTrace`. ADR-0020. |
| F23 | Canal de eventos de seguridad + kill-switch | 🟡 2026-09-07 | **Re-baseline 2026-09-07 contra contrato 0.32.0.** **Encontró un defecto propio, embarcado.** `HttpSecurityEventReporter` serializaba nuestro `SecurityEventKind` directo al cable; el contrato 0.32.0 acepta **su propio** enum de 8 valores y **ninguno de nuestros 6 nombres está ahí**. Como el reporter no lanza por doctrina, cada evento se habría rechazado con **400 en silencio** — palabra por palabra el fallo que ADR-0023 se escribió para evitar. Corregido con `PlatformSecurityEventKind` + un `when` exhaustivo sin `else`: **3 traducen** (`reauthFailure`, `reauthLockout`, `securityStorageFailure`) y **3 no tienen nombre allá** y no se envían — una mentira aproximada es peor que el silencio en un canal donde el kind ES el mensaje. 4 tests nuevos, 3 cebos rojos. `backend-requests/0006` pide los tres. **Falta el cableado** (sin base URL ni auth). |

## Regla de cierre de cada slice

Un slice no cierra sin: (1) el ataque descrito en lenguaje humano en su bitácora, (2) lo que se
verificó/fortificó, dicho sin adorno, (3) el cimiento durable (ADR/gate/test/threat-model), (4) lo
que quedó **diferido o sin verificar**, declarado — jamás escondido.
