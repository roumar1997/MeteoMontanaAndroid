# Diseño — Muros largos (geometría línea) + edición colaborativa

> **Implementado y en producción** (Fases 1-9: backend, shared, Android, iOS,
> moderación, diario por lineId — todas completas). Este documento queda como
> **referencia del modelo de datos y las reglas de negocio** para cuando se
> toque geometría de vías/muros; el plan de implementación fase a fase se
> retiró (ya ejecutado).

## 🎯 Problema

La unidad del mapa es una **piedra** (`school_block` tipo BLOCK): un **punto** +
foto(s) + vías (líneas). Encaja para **bloque** (roca compacta = 1 marcador).
NO encaja para **vía**: una pared es un **muro largo y lineal** con muchas vías
una al lado de otra. Modelar cada vía como su propia "piedra" mete 16+ marcadores
apelotonados → satura el mapa ("lo peta") y además es falso (no son 16 rocas, es
1 muro). **Los muros largos también aparecen en bloque** (travesías, rampas), así
que NO es un problema exclusivo de vía.

## ✅ Decisiones cerradas

### 1. Geometría ⟂ Modalidad (dos ejes independientes)
- **Modalidad** (`discipline`): `BOULDER` / `ROUTE` → cuenta en el perfil (YA hecho).
- **Geometría** (`geometry`): `POINT` / `LINE` → cómo se dibuja en el mapa (NUEVO).
- Se combinan libremente: bloque-punto (roca), bloque-línea (travesía),
  vía-punto (pilar suelto), vía-línea (pared larga).

### 2. Modelo de datos (aditivo)
A `school_block`:
- `geometry`: `"POINT"` | `"LINE"` (default `POINT` → todo lo actual sigue igual).
- `path`: JSON con la lista de `[lat,lon]` que traza la base del muro (vacío si
  POINT). El `lat/lon` actual se queda como ancla (etiqueta).
- Caras (fotos) y líneas (vías) NO cambian: un muro = piedra con `geometry=LINE`,
  varias caras (tramos del muro) y sus vías. Reutiliza TODO lo de multi-cara.

### 3. Mapa e interacción
- Render: el muro se pinta como **polilínea** (`CumbrePolyline` ya existe en
  Android e iOS, de las correcciones del admin). Sin marcadores apelotonados.
- Etiqueta en el punto medio: "Muro X · 14 vías".
- Tocar la línea → abre el visor del muro (caras + vías en orden).
- Crear/proponer: **tocas varios puntos** siguiendo la base del muro para trazar
  la polilínea (como dibujar una topo, pero sobre el mapa). POINT = un solo tap.
- Decluttering: los muros de vía arrancan **colapsados bajo su sector** (reutiliza
  el colapsar-por-zona ya existente).

### 4. Orden de las vías (el orden YA está en el modelo)
Tres niveles, no confundir:
1. Orden de puntos dentro de una línea (el trazo) → `linePath` (ya).
2. Orden de vías dentro de una foto → `sortOrder` (ya).
3. Orden de las fotos a lo largo del muro → `faceOrder` (ya).

**Orden global del muro** = ordenar por `(faceOrder, sortOrder)`.
- **Numeración secuencial del muro**: la vía N se calcula de ese orden global
  (vía 1..N de izquierda a derecha atravesando todas las fotos), no por foto.
- **Reordenar arrastrando** fotos y vías en el editor → reescribe
  `faceOrder`/`sortOrder` → renumera solo.
- **Flag de dirección** (izq→der / der→izq) por si la polilínea se traza al revés.
- ⚠️ Refinamiento fase 2 (NO en v1): anclar cada foto a un tramo de la polilínea
  (esta foto cubre los metros 0–8) → tocar un punto del muro abre la foto correcta.

### 5. Edición colaborativa: "propones el estado completo → diff en la revisión"
Muchos usuarios proponen sobre el mismo muro sin saber cuántas vías hay. Pueden
alargar el muro, añadir/quitar vías, reordenarlas, añadir fotos y elegir orden.
**Añadir una foto nueva NO borra las anteriores: las recoloca.**

- La propuesta NO manda "lo que añado": manda **el estado completo propuesto** del
  muro (polilínea entera + lista ordenada de fotos + lista ordenada de vías). Es
  una *merge request*: propones el "archivo" entero, el sistema lo compara con el
  estado actual y saca el **diff**.
- **Cada vía y foto que ya existía lleva su `id`**; las nuevas van sin id. ESTO es
  lo que permite distinguir "vía nueva" de "vía existente movida". Sin id no hay diff.
- **Edición por lotes**: todo el arrastrar/añadir/reordenar es **estado local en el
  editor**; SOLO al final se pulsa "ENVIAR PROPUESTA" una vez. NO una propuesta por
  movimiento (igual que el editor de topo actual).
- Aprobar = poner el muro en el estado propuesto **conservando las identidades**
  (`id`): las fotos viejas siguen, reordenadas; las nuevas se insertan; nada se
  borra. Se apoya en el patrón ya existente de "añadir a bloque existente".

### 6. El admin debe VER el diff (no aceptar a ciegas)
Reutiliza el patrón actual "existentes difuminadas + nuevas sólidas". Al revisar:
- **Longitud/forma**: en el mapa, **polilínea vieja en gris + nueva sólida**
  superpuestas → ve si se alargó/acortó/cambió el trazado.
- **Orden de vías**: lista con badges 🟢 NUEVA · ↕ MOVIDA "#3 → #5" · 🔴 QUITADA ·
  resto sin badge; arriba el orden propuesto y la dirección.
- **Fotos**: cuáles son nuevas vs existentes y su orden viejo→nuevo.
- Enganche técnico: las vías/fotos llevan **id estable** → el diff es calculable.

### 7. Concurrencia
El admin revisa el diff **contra el estado ACTUAL** (no contra el que vio el
proponente; el muro pudo cambiar mientras tanto). Si la propuesta mueve una vía
que mientras se borró → **avisar del conflicto** con gracia, NO auto-fusionar.
Para el volumen actual (revisión de una en una) basta.

### 8. Enganche del diario por `id` estable + snapshot (todo en vivo)
La entrada del diario debe reflejar la vía VIVA, no una copia congelada (grado
6a→6b debe salir; el tic debe llevar al sitio correcto tras reordenar/cambiar foto).

- **Clave**: `id` ≠ número. El número de piedra **se recicla** (por eso no se
  guarda). El `id` (UUID) de una vía **NO se recicla nunca** → es seguro guardarlo.
- La entrada guarda **`lineId`** (enganche real) **+ snapshot** (nombre/grado/
  modalidad, como respaldo).
- Al pintar el perfil: si `lineId` resuelve en el catálogo → **datos en vivo**
  (grado actual + deep-link a su foto/posición actuales). Si no resuelve →
  snapshot de respaldo.
- **Todo en vivo (decidido)**: al resolver en vivo, si grado/modalidad difieren del
  snapshot, se **re-sincroniza** el snapshot guardado → así los **contadores y
  máximos** del perfil (que se calculan del snapshot en el backend) también se
  corrigen solos.
- **Vía borrada (decidido)**: la entrada **se queda** en el perfil (en gris,
  "vía eliminada"). Borrar una foto/piedra por error NO debe quitarle a la gente
  las vías ya hechas. NUNCA se elimina el tic histórico por un borrado de catálogo.
- **Offline**: se marca por nombre como ahora; al sincronizar, el flusher engancha
  el `lineId`.
- ⚠️ Hoy ya se resuelve el grado en vivo **por nombre** (`GetJournalViaInfoUseCase`).
  El cambio es la **llave: nombre → `id`** (exacto: aguanta renombres, nombres
  duplicados, reordenes y el caso del muro). Es endurecer un patrón ya probado.

---

# 🛠️ PLAN DE IMPLEMENTACIÓN

> **CÓMO USAR ESTE PLAN EN UNA SESIÓN NUEVA**
> 1. Mira **📍 ESTADO ACTUAL** justo aquí debajo: te dice qué fases están hechas
>    `[x]` y cuál es la `← SIGUIENTE`.
> 2. Ve a esa fase, lee su "Objetivo / Ficheros / Pasos / Aceptación".
> 3. Implementa, compila/verifica, marca el checklist, actualiza el ESTADO ACTUAL
>    y commitea (incluye este `.md`). Sin eso, la siguiente sesión no sabe por dónde va.
> 4. Reglas de build recordatorio:
>    - Backend: `JAVA_HOME` al JBR (`C:\Program Files\Android\Android Studio\jbr`),
>      `./mvnw.cmd -q -o -DskipTests compile`; tests con `-Dtest=...` (online la 1ª vez).
>    - Android: el módulo `app` necesita `google-services.json`; en local crear uno
>      dummy temporal para compilar (`./gradlew :app:compileDebugKotlin`), el error
>      `default_web_client_id` es esperado (recurso del google-services real del CI).
>      `shared` compila sin dummy (`./gradlew :shared:compileDebugKotlin`).
>    - iOS: NO se compila sin Mac → lo verifica el **CI de iOS** al pushear. OJO con
