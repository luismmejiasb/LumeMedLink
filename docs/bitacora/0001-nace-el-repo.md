# 0001 · Nace LumeMedLink: la constitución espejo

**Fecha:** 2026-08-17 · **Encargo del autor:** crear el espejo constitucional y de ciberseguridad de
LumeMed para la nueva app phone-first en Kotlin Multiplatform — pacientes y gestión no clínica del
médico — separando ambientes: LumeMed para los datos sensibles del médico, esta para la relación.

## El nombre

**LumeMedLink**, decisión del autor tras evaluar alternativas (LumeLink, LumeCircle, LumeHub,
LumeGo; se descartó LumeMedLite por la connotación de «versión recortada»). Registrado acá para no
re-litigarlo. El nombre público de tienda queda pendiente, junto al de LumeMed.

## Qué se escribió (y qué NO)

**Documentos, deliberadamente**: la constitución (`CLAUDE.md`), ocho ADRs fundacionales y el modelo
de amenaza. **Cero Kotlin**: el esqueleto del proyecto es S0.1 y llegará con sus gates, porque
escribir código antes que los gates es cómo una constitución se queda en prosa — la lección que la
familia ya pagó.

## Las tres decisiones que definen el espejo

1. **La frontera es el producto** (ADR-0001): cero contenido clínico, lista cerrada de lo que sí, y
   la nota honesta de que una agenda médica revela metadatos de salud — baja el riesgo, no el
   estándar.
2. **El lado paciente está gated** (ADR-0006): `ADR-0031 del backend` exige que la identidad de
   paciente llegue por su propio ADR — tier de auth, consentimiento, threat model. Esta app ES esa
   superficie llegando por la puerta, así que se empieza por el lado médico y el gate se cita, no se
   rodea.
3. **Espejo de doctrina, no de código** (ADR-0002/0004): los kits Swift no cruzan a Kotlin. Cruzan
   sus constituciones — tokens portados a Compose, doctrina de red re-implementada sobre Ktor — y
   cada diferencia de plataforma se declara (FLAG_SECURE existe, el veto de teclados no, el Keystore
   muere con el uninstall).

## Lo que quedó dicho para el autor

- El **push** de este repo (remote ya configurado) lo hace él, como siempre.
- La **fila en el tablero** del ecosistema espera su autorización.
- El **ADR del tier paciente** es el próximo movimiento de coordinación con el backend cuando decida
  abrir la Fase 2.
