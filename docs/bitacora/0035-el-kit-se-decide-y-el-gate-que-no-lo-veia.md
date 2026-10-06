# 0035 — El kit se decide, y el gate que no veía sus campos

**Tipo:** `decisión` + `ci` · 2026-10-06

## Qué llega

- **ADR-0033**: el design system es LumeUIComposer, consumido por path hasta su primer tag. S0.3 deja de esperar.
- **Enmienda de ADR-0013**: copiar dentro de un campo editable sí; los datos mostrados siguen sin copiarse. Y los
  campos del kit pasan por `SensitiveTextField`.
- **Enmienda de ADR-0002**: la rama de UI quedó decidida.
- **`check-input-surfaces.sh`** con una regla nueva y su cebo: el ensayo pasa de 39 a 40, todos rojos.
- Tareas `0013` (cablear el kit) y `0014` (los popups del kit frente al endurecimiento de ventana, sin medir).
- Constitución: §0 (fila UI), §5 y §8.9.

## De dónde viene

De una sesión de nube que comparó el kit con su gemelo y después con esta app. La auditoría vive en el kit
(`../LumeUIComposer/docs/audits/lumemedlink-fit-2026-10-06.md`) porque la mayoría de lo que encontró es del kit; acá
aterriza lo que es de esta app. El autor aceptó todas sus recomendaciones el mismo día.

## El hallazgo que se pagó acá

**El gate de entradas no veía un campo del kit.** Su patrón de campo crudo es
`\b(BasicTextField|OutlinedTextField|TextField)\s*\(`, y en `LumeTextField(` no hay frontera de palabra entre `Lume` y
`TextField`: el regex no coincide. Una pantalla que llamara un campo del kit pasaba en verde sin ninguno de los
atributos que `SensitiveTextField` decide — y el `Default` del kit además pide mayúscula de oración y el autocorrector
del sistema, que es el camino que ADR-0013 cierra para un dato personal.

Es la misma forma que la segunda pasada de ADR-0029 nombró: **un gate correcto con el alcance mal puesto**. La regla
estaba bien; lo que corría justo afuera era un nombre que nadie había escrito todavía. Y se cerró **antes** de que exista
un solo campo del kit en el repo, que es el único momento en que cerrarlo es gratis.

Orden del trabajo, a propósito: primero el cebo, visto **verde** contra el gate viejo (el hueco reproducido); después la
regla; después el cebo rojo y el control negativo (un campo del kit dentro de `core/input/` pasa).

## Lo que NO se verificó

Nada compila en la sesión que hizo esto: su red no alcanza `dl.google.com`. Lo único corrido son los gates de shell y su
ensayo. El cableado del kit, la copia de recursos y la sospecha de los popups quedan para la máquina del autor, en
`0013` y `0014`.
