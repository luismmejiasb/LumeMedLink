# 0034 — El número que dos archivos pueden reclamar, y el gate que llegó de LumeMed

**Tipo:** `ci` · 2026-09-21

## Qué llega

`Scripts/numbered-docs-have-no-collisions.py`, hermano del que nació en LumeMed el mismo día, más
sus tres cebos en `rehearse-gates.sh` y su paso en el job de gates.

Tres espacios de nombres de este repo nombran sus archivos `NNNN-slug.md` y **se direccionan por ese
número en prosa**: `docs/adr`, `docs/backend-requests` y `docs/bitacora`. El número es la dirección
— esta constitución dice «ADR-0029» y espera que responda un archivo.

**Ningún código de este repo lee esos árboles.** Por lo tanto una colisión es invisible para el
compilador, para detekt, para ktlint y para todos los tests: es «una regla sin gate se cae sola»
cometida sobre la documentación.

## De dónde viene

De LumeMed, el 2026-09-21. Un `cherry-pick` de la rama de una sesión paralela aterrizó siete tareas
y una bitácora **sobre números que esa rama ya había usado**, y el pipeline entero siguió verde.

Lo que más cuesta ver: **las dos sesiones numeraron bien**. Cada una miró el árbol que tenía delante
y tomó el siguiente libre. La colisión nace **al unir**, no al escribir — así que pedir más cuidado
al numerar no arregla nada, y el chequeo tiene que correr después del merge. De ahí que sea un gate
de CI y no una regla de disciplina.

## Lo que NO comprueba, y por qué

**El índice de la bitácora.** `docs/bitacora/README.md` se declara abandonado en la entrada 0001 y
dice que el índice real es el listado del directorio; reconstruirlo o borrarlo es decisión del autor,
registrada ahí mismo el 2026-09-21 por la auditoría de ADR-0029. Comprobarlo pondría el gate rojo
sobre una **decisión**, que es justo lo que la lista NO-FLAG de la familia existe para impedir.

El día que el autor reconstruya esa tabla, el bloque de reconciliación del hermano de LumeMed entra
tal cual: verifica archivo↔fila, fila↔número enlazado, ninguna fila repetida, ningún enlace muerto y
orden ascendente.

## La convergencia, que vale más que el gate

Este repo escribió el 2026-09-21, en ADR-0029, que **«un gate sin cebo también se cae solo»**: dos
gates estuvieron ciegos desde que nacieron *aunque el ensayo se había hecho*, porque lo hizo quien
acababa de escribir el gate — la misma cabeza que no ve el hueco. Y sacó la conclusión correcta:
el ensayo pasa a ser **un artefacto que corre**, `rehearse-gates.sh`, en CI, después de los gates
que ensaya.

LumeMed llegó a la misma lección el mismo día por otro camino: su gate nuevo pasó diez cebos, y una
**caza adversarial contra el gate mismo** encontró tres huecos más — entre ellos que el piso de «no
escaneó cero» **sólo guarda hacia abajo**, así que un archivo numerado puesto donde nadie recorre
pasaba verde y derrotaba el pinning recién agregado.

Las dos mitades se completan, y conviene decirlas juntas porque ninguna sola alcanza:

- **De este repo:** el ensayo tiene que ser un artefacto que corre, o se degrada a un acto único que
  nadie repite. Por eso los tres cebos del gate nuevo entran a `rehearse-gates.sh` y no a un
  comentario.
- **De LumeMed:** el cebo confirma que el gate ve lo que su autor imaginó, jamás que imaginó
  bastante. Para lo segundo hace falta alguien que **ataque** el gate en vez de acompañarlo.
- **Y la regla concreta que sale de la suma:** todo gate con un piso necesita también un barrido
  hacia arriba — no sólo «¿sigue estando lo que declaré?», también «¿apareció algo donde no declaré
  nada?».

## Estado

Verde al instalarse: `docs/adr 32, docs/backend-requests 6, docs/bitacora 33`, cero colisiones.
`rehearse-gates.sh` pasa de 36 a **39 cebos, los 39 cazados**. Su `bait()` gana un despacho de
intérprete por extensión: los gates de acá son shell y éste es Python, y correr un `.py` bajo `sh`
fallaría por la razón equivocada — que se leería como cebo cazado, el falso verde exacto que el
ensayo existe para impedir.

CI sigue en `workflow_dispatch`, así que esto todavía no corre solo.
