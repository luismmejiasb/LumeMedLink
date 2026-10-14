# Encargo al backend — la foto de perfil del paciente

> **Cómo usar este documento:** es el prompt completo para el agente de `lumemed-cloud-platform`. Viene de dos
> decisiones del autor del 2026-10-14: la foto la ven **el paciente y sus médicos**, y llega **por la API**, nunca por
> una URL firmada a otro host. LumeMedLink ya tiene el flujo de la foto (elegir, sacar, recortar al círculo), en
> memoria y sin subir (su ADR-0050). Su ADR-0064 fija lo que la app hace con la foto.

---

## PROMPT

Eres el agente de desarrollo de `lumemed-cloud-platform`. Tu `ADR-0035` deja al paciente editar su foto, y su hallazgo
F6 dice que el concepto no existe: no hay campo, ni almacenamiento, ni retención. Además advierte la trampa de reusar
`medical-files`. Te pedimos construirlo.

### Lo que necesitamos

1. **La foto es de la cuenta, no de una clínica.** La app muestra un solo perfil aunque haya uno por clínica (decisión
   del autor), así que hay una sola foto por paciente.
2. **Subir, leer y borrar, por la API.** Una operación bajo `/v1/me` o `/v1/patient/` que recibe los bytes de la
   imagen (`image/jpeg`), otra que los devuelve y otra que borra la foto. La app manda una imagen ya recortada al
   círculo y achicada, de unos 512 px, así que el tope de tamaño puede ser chico. **No usar una URL firmada a Cloud
   Storage**: el stack de red de la app sólo habla con el host del backend.
3. **El servidor vuelve a codificar la imagen** antes de guardarla. Eso borra los metadatos, la ubicación GPS
   incluida, y neutraliza cualquier archivo malformado. Valida el tipo por sus primeros bytes, no por el encabezado,
   y aplica un tope de tamaño y de dimensiones.
4. **Fuera de lo clínico.** No es un `medical_file`: sin retención clínica y sin el antivirus de esos archivos (F6).
   Se borra cuando se borra la cuenta o cuando el titular ejerce su derecho de supresión.
5. **La ven los médicos del paciente.** Los profesionales de las clínicas donde el paciente tiene perfil la pueden
   leer: la decisión del autor es que el médico reconozca a quien atiende. Decide tú la ruta del lado del médico y
   responde:
   - qué pasa con la restricción de tratamiento (Ley 21.719, tu T11): un titular con restricción vigente no debe
     mostrar su foto;
   - qué pasa cuando se corta el vínculo con una clínica: sus médicos dejan de verla.
6. **Un cambio de foto es un evento sin contenido**: no viaja la imagen en ningún log, evento ni aviso.

### Lo que no pedimos

- Una foto por clínica.
- Que la foto se use para verificar identidad: es sólo para reconocerse.
- Miniaturas ni varios tamaños: la app pide la única que hay.

Responde con el contrato (operaciones, tipos de contenido, topes y errores) y con la ADR que fija la retención y quién
la lee.
