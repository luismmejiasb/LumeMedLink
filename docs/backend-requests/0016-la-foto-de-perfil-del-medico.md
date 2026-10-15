# Encargo al backend — la foto de perfil del médico

> **Cómo usar este documento:** es el prompt completo para el agente de `lumemed-cloud-platform`. Viene de una decisión
> del autor del 2026-10-15 (opción B, tomada por formulario): el médico sube su foto desde **LumeMed** y los pacientes
> de sus clínicas la ven en **LumeMedLink**. Es el gemelo de `0015` (la foto del paciente, tu tarea `0150`) y sigue su
> mismo molde. Hasta que esto tenga contrato, ninguna de las dos apps escribe código de la foto del médico.

---

## PROMPT

Eres el agente de desarrollo de `lumemed-cloud-platform`. Ya tienes el encargo de la foto del **paciente**
(`LumeMedLink/docs/backend-requests/0015`, tu tarea `0150`). Este es el de la foto del **profesional**, que hoy no
existe en ninguna parte: ni en el contrato, ni en una ADR, ni en el modelo `Professional`. Te pedimos construirlo.
Si te conviene, diséñalo junto con `0150`, porque comparten la mitad del trabajo: recodificar la imagen, guardar el
byte y los topes.

### Lo que necesitamos

1. **Una foto por profesional, no por clínica.** Es la misma persona en todas sus clínicas.
2. **La sube el profesional desde LumeMed, por la API.** Tres operaciones del lado clínico, bajo el tier del
   profesional: una sube los bytes (`image/jpeg`), otra los devuelve y otra borra la foto. La app manda una imagen
   ya recortada al círculo y achicada, de unos 512 px. **Nada de URL firmada a Cloud Storage**: el stack de red de
   ambas apps sólo habla con el host del backend.
3. **El servidor vuelve a codificar la imagen** antes de guardarla, igual que en `0150`: sin EXIF ni GPS, el tipo
   validado por sus primeros bytes, con tope de tamaño y de dimensiones.
4. **Fuera de lo clínico.** No es un `medical_file` ni entra en la retención clínica. Se borra cuando se da de baja
   la cuenta del profesional o cuando ejerce su derecho de supresión.
5. **La ven los pacientes de sus clínicas**, en LumeMedLink. Decide tú la ruta del lado del paciente y responde:
   - **quién es «su» paciente:** el que tiene perfil en una clínica donde el profesional atiende, o sólo el que tuvo
     o tiene una cita con él;
   - **qué pasa cuando se corta el vínculo** del paciente con la clínica: deja de verla;
   - **el directorio:** si la búsqueda de médicos (`/v1/directory/…`) muestra la foto a cualquier paciente que busca,
     o sólo a los de sus clínicas. Hoy el directorio sólo publica lo que el profesional decidió compartir; la foto
     tendría que seguir esa regla, con un consentimiento propio del profesional.
6. **Un cambio de foto es un evento sin contenido**: la imagen no viaja en ningún log, evento ni aviso.

### Lo que no pedimos

- Una foto por clínica.
- Que la foto sirva para verificar identidad: es sólo para que el paciente reconozca a quien lo atiende.
- Miniaturas ni varios tamaños.

### Para las dos apps

- **LumeMed** sube, ve y borra la foto del profesional, y la muestra donde hoy salen sus iniciales.
- **LumeMedLink** la muestra donde hoy salen las iniciales del médico: Mis médicos, las citas, el detalle y, si lo
  permites, los resultados de búsqueda. **Un 403 o un 404 los muestra como «sin foto»**, en silencio.
- Si una lista de médicos necesita varias fotos, dinos si habrá una forma de pedirlas juntas o una cabecera de caché.
  Sin eso, las apps cargan una por fila visible, con un tope de concurrencia.

Responde con el contrato (operaciones, tipos de contenido, topes y errores) y con la ADR que fija la retención, el
consentimiento del directorio y quién la lee.
