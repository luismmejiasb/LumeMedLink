# 0042 — El esquema propio que Android dejaba pasar

**Tipo:** `ci` · 2026-10-07

## Qué llega

- **`Scripts/check-deep-links.sh`** (F17, §8.12): el gate de deep links que Android no tenía.
- Cinco cebos: cuatro de ese gate y el que le faltaba al rechazo de esquemas de iOS. El ensayo pasa de 47 a 52.
- La tarea `0011` avanza y sigue abierta; la fila F17 del plan pasa de ⬜ a 🟡.

## El hueco

El §8.12 prohíbe los esquemas propios en las dos plataformas, porque cualquier app puede reclamar uno. En iOS había gate
desde el host (`check-ios-host.sh` rechaza `CFBundleURLTypes`), aunque sin cebo en el ensayo: se había probado una vez, al
escribirlo, que es justo el ensayo que ADR-0029 dice que no cuenta. En Android **no había nada**: un
`<data android:scheme="lumemedlink">` en cualquier actividad pasaba todos los gates del repo.

## El gate

Lee el manifiesto fuente y todos los fusionados, parseados. Dos reglas por intent filter: todo esquema es `https`, y un
filtro navegable con `https` lleva `autoVerify`. El fusionado importa más de lo que parece: la forma clásica en que llega
un esquema propio no es que alguien lo escriba, es una librería de OAuth que trae su actividad de redirección — y eso sólo
se ve después del merge.

Los cebos cubren la tentación «para desarrollo», el App Link sin verificar, un filtro que mezcla `https` con un esquema
propio (Android junta los `<data>` de un filtro, así que uno malo envenena el filtro entero) y la actividad de redirección
de una librería. Un cebo con XML mal formado también pondría rojo al gate —por el parser—, así que se comprobó a mano que el
rojo dice la regla.

## Lo que no se hizo

La mitad positiva (dominio, archivos de asociación, router que pasa por el lock) espera un dominio que no existe y destinos
que todavía no hay. Está escrito en la tarea.
