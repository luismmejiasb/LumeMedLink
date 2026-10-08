# 0027 · Un solo teléfono por cuenta — la mitad del cliente

> **Estado:** FREEZE · abierta el 2026-10-07 con la decisión del autor (ADR-0041). Espera `backend-requests/0007`
> y un backend desplegado.

## Qué se construye cuando se descongele

1. Una clave por instalación, no exportable, hecha en `establishSession` (ADR-0037) y enviada al servidor.
2. Distinguir en el stack la respuesta `session-superseded` (el `type` exacto lo fija el backend) de un token
   vencido, y llevarla al shell por una señal propia.
3. Ante la sustitución: el **logout completo** (`performLogout`) y una pantalla que diga qué pasó y qué hacer si no
   fue la persona. Ante el vencido, lo de siempre (ADR-0036).
4. Al volver al primer plano, una consulta autenticada barata para enterarse sin esperar a que la persona toque algo.
5. En el flujo de ingreso (S1.1), el aviso de ADR-0041: «este teléfono debe ser sólo tuyo».

## Cómo se verifica

Un test del stack con el `type` de sustitución (borra todo) y con un 401 común (borra sólo la sesión), y su cebo:
confundirlos pone rojo uno de los dos.

## Qué NO hacer

- Decidir en el cliente qué teléfono gana: lo decide el servidor.
- Mostrar en el aviso datos del otro teléfono que el backend no haya decidido mostrar.
