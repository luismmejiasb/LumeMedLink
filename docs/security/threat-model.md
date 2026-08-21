# Modelo de amenaza — LumeMedLink

> Espejo del modelo con el que se auditó LumeMed (su `docs/audit/IOS-AUDIT.md`), re-priorizado para
> ESTE producto: teléfonos personales, dos audiencias, y una frontera de datos que baja el impacto
> sin bajar el estándar (ADR-0001). Todo hallazgo de seguridad se prioriza contra esta lista; lo que
> no responde a ninguna va al final.

## Qué protege esta app (y qué no puede filtrar porque no lo tiene)

**Tiene**: datos personales plenos (nombre, RUT, teléfono, correo, foto), la agenda de citas (que
**revela metadatos de salud**: existir en la agenda de un oncólogo es información sensible), la lista
de pacientes del médico como contactos, tokens de sesión.

**No tiene, por construcción** (ADR-0001/0007): ficha, diagnósticos, resultados, documentos. La
consecuencia de auditoría: si contenido clínico aparece en cualquier superficie de esta app, no es un
hallazgo de severidad — es una **violación de frontera**, la clase por encima de Crítico.

## Las amenazas, en orden

### T1 · Dispositivo perdido, robado o incautado
Atacante con posesión física, sin credenciales. Un teléfono se pierde más que un iPad de consulta.
**Controles**: secretos en Keystore/Keychain con piso de dispositivo-con-bloqueo (ADR-0005); datos
cacheados cifrados y purgables; sesión corta; sin backup que reviva la agenda en otro equipo.

### T2 · Dispositivo COMPARTIDO — aquí sube de rango
El nivel 2 del modelo de LumeMed es el observador con acceso visual y físico momentáneo al
dispositivo desbloqueado (el paciente o un familiar en la consulta). Aquí es estructural: **el teléfono de un
paciente lo usa su familia**, y una agenda de citas con un psiquiatra visible en una notificación es
exactamente la filtración que la Ley 21.719 castiga. **Controles**: push sin contenido (§8.5),
FLAG_SECURE / cover (§8.3), bloqueo por inactividad con biometría anclada a clave (§8.3), nada
personal en superficies pre-auth (widgets, recientes, pantalla bloqueada).
**Estado de fortificación** (`fortification-plan.md`): **F1 ✅ núcleo** — FLAG_SECURE app-wide
(Android) + cover Compose (iOS, primera capa) + tapjacking, con gate `check-screen-security.sh`
(ADR-0010, bitácora 0008). **F4 🟡 mecanismo completo** — bloqueo por inactividad + desbloqueo
biométrico **anclado a material de clave, jamás un booleano** (ADR-0011): EC en Keystore firmando
un reto (Android), Keychain `.biometryCurrentSet` (iOS), la clave destruida si cambia el
enrolamiento; gate `check-biometric-contract.sh` con 8 cebos, 4 tests instrumentados verdes en
Android real (bitácora 0010) y la **invalidación por enrolamiento probada en device con control** (bitácora 0014). **F2 ✅** — ninguna superficie pre-auth
existe (sin widgets, sin shortcuts, sin permiso de notificaciones, sin banderas de lock screen) y el
futuro push sólo puede tomar la forma de `shared/PushSignal`, **que no tiene campo de texto**; gate
`check-preauth-surfaces.sh` con 8 cebos (ADR-0012, bitácora 0011). Pendientes de T2: el end-to-end
de F4 (espera el login) y el cover iOS de host.

### T3 · Persistencia posterior
Datos que sobreviven al logout, al cambio de usuario o a la desinstalación. **Controles**: logout =
wipe duro con test (§8.13) — **F5 ✅ 2026-08-21: verificado contra el Keystore real en device** (el claro no llega al disco; el wipe borra archivos y clave), con la salvedad declarada de que es un logout LOCAL sin revocación server-side (ADR-0014, bitácora 0015); sentinel de instalación en iOS (el Keychain sobrevive al uninstall;
ADR-0005 declara la asimetría — Android no lo necesita); caché con política de purga.

### T4 · Exfiltración pasiva por plataforma
Backup, sincronización, indexación, teclados, portapapeles, crash reporting. **Controles**:
`allowBackup=false` **+ `dataExtractionRules`** (complementarios: el primero no cubre la migración
entre dispositivos a targetSdk ≥ 31 — medido, ADR-0015); en iOS no hay archivo que marcar todavía y
`isExcludedFromBackup` llega con la primera caché (F8); default-deny de SDKs de
crash/analytics (§8.1 — con Identity Platform, Crashlytics está a una línea y el freno es
constitucional); portapapeles sin datos personales (§8.9); en Android el teclado de terceros **no se
puede vetar** y la mitigación parcial se declara (§8.10) en vez de fingir la paridad con iOS.
**Estado de fortificación: F6 ✅ 2026-08-21** — se encontró y cerró un agujero real: a targetSdk ≥ 31 Android
**ignora `allowBackup` para la migración device-to-device**, y nuestro paquete emitía datos por ahí (medido).
Cerrado con `dataExtractionRules` (9 dominios × 2 secciones), gate + verificación en device con control en vivo
(ADR-0015, bitácora 0016). **F3 ✅ (reabierto por un hallazgo de F6: Compose exporta la estructura de autofill
de cada pantalla incondicionalmente, y FLAG_SECURE no la toca)** — la app no ofrece copiar (gate sobre clipboard y `SelectionContainer`) y toda entrada sensible pasa por `core/input/SensitiveTextField`, con endurecimiento por propósito; las dos asimetrías (clip sin expiración en Android, IME invetable en Android) quedan declaradas, no maquilladas (ADR-0013, bitácora 0013). Pendientes de T4: F6, F8, F22.

### T5 · Atacante en red / backend comprometido
**Controles**: HTTPS-only fail-closed, TLS ≥ 1.2, cleartext negado también en el manifiesto Android,
scope de token estrecho (ADR-0003: el token de esta app jamás concede lecturas clínicas — el daño de
un token robado aquí queda acotado por diseño). Pinning diferido con su trade-off declarado
(ADR-0004) — **decisión propia de este repo**; LumeMed no tiene una decisión de diferirlo que heredar.

### T6 · App maliciosa co-residente
**Controles**: App Links/universal links verificados con identificadores opacos (§8.12); Play
Integrity / App Attest server-side (§8.11); sin custom schemes; sin IPC expuesto sin permiso.

## Las tres asimetrías de plataforma que un auditor debe saber

1. **Android bloquea screenshots; iOS no.** `FLAG_SECURE` es regla dura en Android. En iOS aplica la
   doctrina de LumeMed ADR-0023/0028: cover de privacidad, jamás un blackout fingido.
2. **El Keychain de iOS sobrevive al uninstall; el Keystore de Android no.** El sentinel de
   instalación es un control de un solo lado (ADR-0005).
3. **iOS veta teclados de terceros app-wide; Android no puede.** La mitigación Android es por campo y
   parcial, y se declara como tal.
