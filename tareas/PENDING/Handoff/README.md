# LumeMedLink · Paquete de construcción

Todo lo necesario para construir **Auth** y la **App principal** de LumeMedLink (iOS + Android, Kotlin Multiplatform).

## Cómo leerlo
1. `00-Producto.md` — qué es la app y **qué nunca hace** (no accede a información médica de LumeMed).
2. `03-Specs/1-Auth.md` y `03-Specs/2-App-principal.md` — una tabla por sección: cada PNG, desde dónde se llega, qué hace y sus casos borde.
3. `01-Pantallas/` — la referencia visual. Cada archivo se llama `NN-id-nombre.png`:
   - `NN` = orden de lectura dentro del flujo.
   - `id` = el identificador usado en las specs y en las flechas de navegación (p. ej. `a1`, `1b-e2`).
   - Un `id` con `e` (`e5`, `2a-e1`) es un **error, estado vacío o caso borde**.

## Estructura
```
LumeMedLink-Handoff/
├── README.md                 ← empieza aquí
├── 00-Producto.md            qué es LumeMedLink y su frontera con LumeMed
├── 01-Pantallas/             PNG @2x (780×1688 = pantalla de 390×844 pt)
│   ├── 1-Auth/
│   │   ├── 01-flujo-paciente/    20 pantallas
│   │   └── 02-flujo-medico/       6 pantallas
│   └── 2-App-principal/
│       ├── 01-paciente-pestanas/              5
│       ├── 02-buscar-de-la-busqueda-a-la-reserva/ 13
│       ├── 03-inicio-tu-proxima-cita/         5
│       ├── 04-valoracion-de-la-atencion/      4
│       ├── 05-mis-citas-detalle-de-una-cita/  4
│       ├── 06-mis-medicos/                    1
│       ├── 07-configuracion-cambiar-foto-de-perfil/ 4
│       └── 08-medico/                         3
├── 02-Assets/
│   ├── marca/                SVG de marca LumeMedLink
│   └── iconos/               SVG 24×24 (tab-mis-medicos también en PNG)
└── 03-Specs/
    ├── 1-Auth.md             cada pantalla: origen, comportamiento, casos borde + validaciones
    └── 2-App-principal.md
```

## Sistema visual
- Todo es **LumeUIKit** (el mismo sistema que LumeMed). Solo cambia el acento a azul.
- Acento `#3E63DD` · fuerte `#2F4FBE` · claro `#91A8F2` · tinte `rgba(62,99,221,.12)`.
- Fondo `#EBEFF3` · tarjetas `#FFFFFF` · texto `#191C1F` · secundario `#6A7279` · separadores `rgba(20,30,50,.09)`.
- Nombre: «Lume» negro, «Med» teal `#0E9891`, símbolo de enlace y «Link» azul.
- Pantalla de diseño: **390 × 844 pt** (los PNG están a @2x). Toque mínimo 44 pt.
- NavBar y TabBar: blanco translúcido `rgba(255,255,255,.86)` con desenfoque y línea inferior/superior.

## Sobre las imágenes
- Se exportaron **sin el marco del teléfono** y **sin sombras ni desenfoques**: el exportador de imágenes los dibuja mal. En la app, NavBar, TabBar, botón volver y hojas llevan blur/sombra según LumeUIKit.
- Datos de ejemplo ficticios (Camila Soto, Dra. Mariana Rendón…).

## Iconos
- `02-Assets/iconos/`: SVG 24×24. Los monocromos usan `currentColor` (el comentario de cada archivo dice el color original y cuántas veces se usa).
- `tab-mis-medicos` es un icono bitmap (médica con estetoscopio); va en PNG y dentro de un SVG.
- **Mapas, Google Maps y Waze** (hoja «Cómo llegar»): se usa el icono que entrega el sistema para cada app instalada; no se incluyen.
