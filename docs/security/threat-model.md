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
En LumeMed era «observador presente en la consulta». Aquí es estructural: **el teléfono de un
paciente lo usa su familia**, y una agenda de citas con un psiquiatra visible en una notificación es
exactamente la filtración que la Ley 21.719 castiga. **Controles**: push sin contenido (§8.5),
FLAG_SECURE / cover (§8.3), bloqueo por inactividad con biometría anclada a clave (§8.3), nada
personal en superficies pre-auth (widgets, recientes, pantalla bloqueada).

### T3 · Persistencia posterior
Datos que sobreviven al logout, al cambio de usuario o a la desinstalación. **Controles**: logout =
wipe duro con test (§8.13); sentinel de instalación en iOS (el Keychain sobrevive al uninstall;
ADR-0005 declara la asimetría — Android no lo necesita); caché con política de purga.

### T4 · Exfiltración pasiva por plataforma
Backup, sincronización, indexación, teclados, portapapeles, crash reporting. **Controles**:
`allowBackup=false` + `dataExtractionRules` / `isExcludedFromBackup`; default-deny de SDKs de
crash/analytics (§8.1 — con Identity Platform, Crashlytics está a una línea y el freno es
constitucional); portapapeles sin datos personales (§8.9); en Android el teclado de terceros **no se
puede vetar** y la mitigación parcial se declara (§8.10) en vez de fingir la paridad con iOS.

### T5 · Atacante en red / backend comprometido
**Controles**: HTTPS-only fail-closed, TLS ≥ 1.2, cleartext negado también en el manifiesto Android,
scope de token estrecho (ADR-0003: el token de esta app jamás concede lecturas clínicas — el daño de
un token robado aquí queda acotado por diseño). Pinning diferido con su trade-off declarado
(ADR-0004), misma decisión y razón que LumeMed.

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
