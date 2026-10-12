# LumeMedLink · App principal

La app con TabBar de cinco pestañas (Inicio · Buscar · Citas · Mis médicos · Ajustes) y la vista simple del médico. Las pantallas `e*` son **estados vacíos, errores y casos borde**.

**Regla para todos los estados vacíos:** explican por qué no hay contenido y ofrecen una salida concreta. Nunca muestran un error técnico («error 500»).

---

## 1. Paciente · pestañas

Las cinco pestañas de la app. Aquí se ve solo la pantalla raíz de cada una; lo que se abre desde ellas está en su propia sección, abajo.

| Imagen | Pantalla | Se llega desde | Qué debe hacer / casos borde |
|---|---|---|---|
| `01-p1-inicio.png` | **p1** · Inicio | — | Ver imagen. |
| `02-p2-buscar.png` | **p2** · Buscar | — | Ver imagen. |
| `03-p5-mis-citas.png` | **p5** · Mis citas | — | Ver imagen. |
| `04-p6-mis-medicos.png` | **p6** · Mis médicos | — | Ver imagen. |
| `05-p7-configuracion.png` | **p7** · Configuración | — | Ver imagen. |

---

## 2. Buscar · de la búsqueda a la reserva

De la pestaña Buscar (p2) hasta la cita agendada: elegir especialidad y comuna, ver médicos, filtrar, elegir día y hora, confirmar y esperar la confirmación del médico.

| Imagen | Pantalla | Se llega desde | Qué debe hacer / casos borde |
|---|---|---|---|
| `06-e0-especialidad-todas.png` | **e0** · Especialidad · todas | p2 · Especialidad | Lista completa en orden alfabético, agrupada por letra. La elegida lleva ✓.<br>Sin conteo de médicos: la lista sale de un catálogo fijo, sin consultar disponibilidad.<br>Al escribir pasa a e1; al borrar lo escrito vuelve aquí. |
| `07-e1-especialidad-buscando.png` | **e1** · Especialidad · buscando | p2 · Especialidad | La búsqueda filtra en vivo desde la primera letra y resalta la coincidencia.<br>Ignora tildes y mayúsculas: «pediatria» encuentra Pediatría. |
| `08-e3-comuna.png` | **e3** · Comuna | p2 · Comuna | Solo los grupos por región, en orden alfabético.<br>Selección única: al tocar vuelve a Buscar. Si cambia la comuna, se borran los filtros de resultados. |
| `09-p3-resultados.png` | **p3** · Resultados | p2 · Ver médicos | Especialidad y comuna son la búsqueda, no un filtro: van arriba como resumen y se cambian con «Cambiar».<br>Al cambiar especialidad o comuna se borran los demás filtros y la lista se vuelve a cargar.<br>Sin resultados: «No hay médicos de Medicina general en Providencia» con atajo a comunas cercanas. |
| `10-p4-filtros.png` | **p4** · Filtros | p3 · Filtros | Los filtros refinan una búsqueda ya hecha. Especialidad y comuna no están aquí: se eligen en p2 y cambiarlas reinicia estos filtros.<br>Ordenar es selección única. Disponibilidad (única) y Previsión (múltiple) abren su propia lista, como Especialidad.<br>«Ver N médicos» recalcula el número en vivo. Con 0 resultados el botón dice «Sin resultados» y queda deshabilitado.<br>«Más cerca» pide permiso de ubicación la primera vez; si se niega, usa la comuna elegida. |
| `11-a1-elegir-dia-y-hora.png` | **a1** · Elegir día y hora | p3 · Agendar | Llega con el médico y su primera hora libre ya seleccionada, saltando las que chocan con otra cita del paciente.<br>Mes y año son menús desplegables: solo ofrecen meses con agenda abierta (hasta la reserva máxima del médico). Días sin horas se ven deshabilitados; las horas tomadas, tachadas.<br>Una hora en la que el paciente ya tiene cita se marca «Tu cita» y no se puede elegir. Si la hora se toma mientras elige: alerta de e10. |
| `12-a2-confirmar-reserva.png` | **a2** · Confirmar reserva | a1 · Continuar | Resumen completo antes de enviar: fecha, duración, dirección y previsión.<br>El motivo es opcional y solo lo ve el médico y su secretaria.<br>Sin conexión al confirmar: el botón muestra «Reintentar» y la hora no se pierde. |
| `13-a3-reserva-enviada.png` | **a3** · Reserva enviada | a2 · Confirmar reserva | Alerta de éxito con el estado real: esperando confirmación, no «cita confirmada».<br>«Ver mis citas» lleva a a4; «Listo» vuelve a Resultados (p3). |
| `14-a4-mis-citas-reserva-nueva.png` | **a4** · Mis citas · reserva nueva | a3 · Ver mis citas | Es p5 con la cita recién creada en su lugar cronológico, destacada con el borde azul durante 3 s.<br>Al confirmar el médico: notificación y la etiqueta pasa a «Confirmada». Si la rechaza, pasa a Canceladas con su motivo. |
| `15-e2-especialidad-sin-coincidencias.png` | **e2** · Especialidad · sin coincidencias | e1 · sin coincidencias | Sin botón de salida: al borrar lo escrito (✕) vuelve la lista completa de e0.<br>Nunca se ofrece una especialidad sin médicos disponibles. |
| `16-e5-resultados-vacios.png` | **e5** · Resultados vacíos | p3 · sin médicos | Nunca se muestra una lista vacía sin salida: siempre hay una alternativa concreta.<br>Si un filtro de p4 deja 0 resultados, el estado ofrece «Quitar filtros» en vez de comunas. |
| `17-e6-sin-conexion.png` | **e6** · Sin conexión | p3 · sin conexión | Errores de red nunca dicen «error 500»: dicen qué pasó y qué hacer.<br>La búsqueda (especialidad, comuna y filtros) se conserva al reintentar.<br>Si la búsqueda falla por el servidor: «Estamos con problemas. Intenta en unos minutos». |
| `18-e10-cambiar-hora-hora-tomada.png` | **e10** · Cambiar hora · hora tomada | f3 · hora tomada | La hora elegida se tacha en el momento y el botón vuelve a deshabilitarse.<br>Si un día se queda sin horas, deja de ofrecerse y se sugiere el siguiente con disponibilidad. |

---

## 3. Inicio · tu próxima cita

Lo que se abre desde la tarjeta de próxima cita en Inicio (p1): cómo llegar, cambiar o cancelar.

| Imagen | Pantalla | Se llega desde | Qué debe hacer / casos borde |
|---|---|---|---|
| `19-f1-como-llegar.png` | **f1** · Cómo llegar | p1 · Cómo llegar | Iconos de Simple Icons (CC0). En la app se usa el icono que entrega el sistema para cada app instalada.<br>Solo aparecen las apps instaladas. iOS siempre ofrece Mapas; Android siempre Google Maps.<br>Si hay una sola app disponible, se abre directo sin mostrar la hoja.<br>La dirección viaja con coordenadas de la consulta, no como texto: así el pin cae en la puerta y no en la esquina. |
| `20-f2-cambiar-o-cancelar.png` | **f2** · Cambiar o cancelar | p1 · Cambiar o cancelar | Pasado el plazo (menos de 30 min) las dos opciones se deshabilitan —no se ocultan— y aparece «Llamar a la consulta» con el teléfono, si el médico lo comparte.<br>El plazo lo define el médico en su horario de LumeMed.<br>Una cita esperando confirmación se puede cancelar en cualquier momento. |
| `21-f3-cambiar-hora.png` | **f3** · Cambiar hora | f2 · Cambiar fecha u hora | Solo se muestran horas reales del horario del médico: feriados y días sin atención aparecen deshabilitados.<br>Si el médico confirma cada reserva, la cita pasa a «Esperando confirmación» y conserva la hora original hasta entonces.<br>Error: si otra persona toma la hora mientras eliges, se marca ocupada y aparece «Esa hora se acaba de tomar. Elige otra». |
| `22-f4-cancelar-cita.png` | **f4** · Cancelar cita | f2 · Cancelar cita | La acción destructiva va en rojo y abajo a la derecha, como todo cierre de flujo.<br>Al confirmar: toast «Cita cancelada» y la cita pasa a «Canceladas» en Mis citas.<br>Sin conexión: el botón queda deshabilitado con «Necesitas conexión para cancelar». No se encola: cancelar debe confirmarse en el momento. |
| `23-e7-inicio-sin-citas.png` | **e7** · Inicio sin citas | p1 · sin citas | Ocupa el lugar de la tarjeta de próxima cita: la jerarquía de Inicio no cambia.<br>Si tiene citas «esperando confirmación» pero ninguna confirmada, se muestran esas en su lugar. |

---

## 4. Valoración de la atención

Desde la tarjeta de valoración en Inicio (p1). Tocar una estrella abre la hoja con ese nivel; cada nivel ofrece opciones distintas.

| Imagen | Pantalla | Se llega desde | Qué debe hacer / casos borde |
|---|---|---|---|
| `24-f5a-valorar-malo.png` | **f5a** · Valorar · Malo | p1 · 1–2 estrellas | Con 1 o 2 estrellas la pregunta es qué falló: los aspectos describen problemas concretos.<br>Aparece «Hacer un reclamo»: la valoración es una opinión; el reclamo, un trámite formal con respuesta.<br>Textos y colores de cada nivel: ver la escala debajo. |
| `25-f5b-valorar-regular.png` | **f5b** · Valorar · Regular | p1 · 3 estrellas | Con 3 estrellas no fue malo ni bueno: se pregunta qué mejorar, en tono constructivo.<br>Los mismos temas que en Bueno, pero redactados como algo que puede mejorar. |
| `26-f5c-valorar-bueno.png` | **f5c** · Valorar · Bueno | p1 · 4–5 estrellas | Con 4 o 5 estrellas se pregunta qué destacar.<br>Lo destacado se muestra como etiquetas en el perfil del médico. |
| `27-f5d-valorar-otro.png` | **f5d** · Valorar · Otro | f5b · tocar «Otro» | «Otro» existe en los tres niveles. Al tocarlo aparece un campo de una línea para escribir el aspecto propio.<br>Máximo 40 caracteres, con contador. Se puede combinar con las otras opciones.<br>Al desmarcar «Otro», el campo desaparece y su texto se descarta. |

---

## 5. Mis citas · detalle de una cita

Se abre al tocar una cita en Mis citas (p5). La pantalla es la misma; cambian el estado, la información visible y las acciones de la barra inferior.

| Imagen | Pantalla | Se llega desde | Qué debe hacer / casos borde |
|---|---|---|---|
| `28-c1-detalle-confirmada.png` | **c1** · Detalle · confirmada | p5 · tocar una cita | La dirección completa sin cortes, con «Cómo llegar» que abre la hoja de f1.<br>Dentro de las 24 h: los botones siguen visibles, pero Cambiar y Cancelar ofrecen llamar a la consulta.<br>El contacto de la consulta solo aparece si el médico lo comparte. |
| `29-c2-detalle-por-confirmar.png` | **c2** · Detalle · por confirmar | p5 · cita por confirmar | Explica por qué aún no está confirmada y qué pasa después.<br>Muestra el motivo que escribió el paciente al reservar (a2). Solo lo ven él, el médico y la secretaria.<br>Si el médico la rechaza, la cita pasa a Canceladas con su motivo. |
| `30-c3-detalle-atendida.png` | **c3** · Detalle · atendida | p5 · Pasadas | Sin acciones de cambio: la acción es Agendar de nuevo, que abre a1 con el mismo médico.<br>Si no valoró, en lugar de su valoración aparecen las 5 estrellas para hacerlo (abren f5).<br>Nunca muestra diagnósticos, recetas ni notas clínicas. |
| `31-e8-mis-citas-vacia.png` | **e8** · Mis citas vacía | p5 · sin citas | Cada pestaña tiene su propio vacío: en Pasadas, «Todavía no te has atendido con Lume»; en Canceladas, solo el texto, sin botón. |

---

## 6. Mis médicos

Desde la pestaña Mis médicos (p6). Por ahora solo el estado vacío; el detalle del médico reutiliza su perfil.

| Imagen | Pantalla | Se llega desde | Qué debe hacer / casos borde |
|---|---|---|---|
| `32-e9-mis-medicos-vacia.png` | **e9** · Mis médicos vacía | p6 · sin médicos | No se muestra el buscador mientras la lista esté vacía: no hay nada que buscar.<br>Un médico que no comparte su contacto aparece igual, con «Contacto privado» en vez de teléfono. |

---

## 7. Configuración · cambiar foto de perfil

El mismo flujo sirve para el paciente (p7) y para el médico (m3): en los dos, el avatar lleva un botón redondo con una cámara. En el médico, la foto nueva es la que ven los pacientes en Buscar y en sus citas.

| Imagen | Pantalla | Se llega desde | Qué debe hacer / casos borde |
|---|---|---|---|
| `33-k1-opciones-de-foto.png` | **k1** · Opciones de foto | p7 · botón de la foto | El botón redondo de la cámara abre esta hoja; también se abre al tocar la foto.<br>«Eliminar foto» solo aparece si ya hay una; al eliminar, vuelven las iniciales.<br>En Android es una hoja inferior de Material con las mismas tres opciones. |
| `34-k2-ajustar-foto.png` | **k2** · Ajustar foto | k1 · Tomar o Elegir | Recorte circular: lo que queda dentro del círculo es lo que se ve en el avatar.<br>Formatos JPG, PNG o HEIC. Se comprime en el teléfono antes de subir. |
| `35-k3-foto-actualizada.png` | **k3** · Foto actualizada | k2 · Usar foto | La foto nueva reemplaza a las iniciales en toda la app y en tu perfil.<br>Si la subida falla: la foto anterior se mantiene y aparece «No pudimos subir tu foto · Reintentar». |
| `36-k4-sin-permiso-de-fotos-o-camara.png` | **k4** · Sin permiso de fotos o cámara | k1 · sin permiso | Se pide el permiso solo al tocar «Tomar foto» o «Elegir de la galería».<br>Para la cámara el mensaje cambia a «Sin acceso a la cámara». |

---

## 8. Médico

Agenda · Pacientes · Perfil · vista simple, sin datos clínicos

| Imagen | Pantalla | Se llega desde | Qué debe hacer / casos borde |
|---|---|---|---|
| `37-m1-agenda.png` | **m1** · Agenda | — | Ver imagen. |
| `38-m2-pacientes.png` | **m2** · Pacientes | — | Ver imagen. |
| `39-m3-perfil-del-medico.png` | **m3** · Perfil del médico | — | Ver imagen. |

