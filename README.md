# LumeMedLink

La app **phone-first** del ecosistema Lume para lo que rodea a la consulta sin ser la ficha:
perfil, citas programadas, contactos — para **pacientes** y para el lado **no clínico** del médico.
A futuro, el extremo paciente de la teleconsulta.

**Kotlin Multiplatform** (Android + iOS, Compose Multiplatform). Hermana de:

| Repo | Qué es | Qué toma este repo de él |
| --- | --- | --- |
| [`LumeMed`](../LumeMed) | La herramienta clínica del médico (iPad-first, PHI) | La **constitución espejo**: doctrina de seguridad y arquitectura, adaptada por plataforma |
| [`lumemed-cloud-platform`](../lumemed-cloud-platform) | El backend y el contrato OpenAPI | El contrato (el rol paciente **no existe todavía** — ver ADR-0006) |
| [`LumeUIKit`](../LumeUIKit) | El design system Swift | Los **tokens y el lenguaje visual**, portados a Compose — jamás el código |

## La frontera, en una línea

**Cero contenido clínico.** Ni ficha, ni diagnóstico, ni nota, ni resultado, ni receta. Si una
feature lo necesita, la feature es de LumeMed (médico) o no existe todavía (paciente). La frontera
es constitucional: `CLAUDE.md` §1 y ADR-0001.

## Leer primero

1. `CLAUDE.md` — la constitución. Manda sobre todo lo demás.
2. `docs/adr/` — las decisiones estructurales.
3. `docs/security/threat-model.md` — el modelo de amenaza, priorizado.
4. `WORKPLAN.md` / `PROGRESS.md` — el plan vivo y el estado real.
