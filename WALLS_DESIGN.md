# Diseño — Muros largos (geometría línea) + edición colaborativa

> Documento de diseño. Recoge las decisiones tomadas con Rodrigo antes de
> implementar. **Aún NO implementado** (la feature Bloque/Vía sí lo está; esto
> es el siguiente paso). Leer junto a `CLAUDE.md` (modelo de piedras/bloques) y
> a la sesión de la bitácora "modalidad Bloque vs Vía".

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
>      el orden de params en los init que genera SKIE (= orden de declaración Kotlin).
>    - **NO** se mergea a `main` sin que Rodrigo lo apruebe ("mergea a main").

## 📍 ESTADO ACTUAL
- [x] **Fase 1 — Backend: geometría + path + dirección** ✅ (V28, compila + 13 tests verdes)
- [x] **Fase 2 — Backend: propuesta de estado completo + diff + merge no destructivo** ✅
      (reconcileWall + WallDiffCalculator + test; 16 tests verdes)
- [ ] **Fase 3 — Backend: enganche del diario por `lineId` + propagación de cambios**  ← SIGUIENTE
- [ ] Fase 4 — Shared (KMP): propagar todo a las dos apps
- [ ] Fase 5 — Android: render muro (polilínea) + colapsar por sector
- [ ] Fase 6 — Android: editor de muro (trazar/reordenar/dirección, enviar una vez)
- [ ] Fase 7 — Android: vista de diff del admin
- [ ] Fase 8 — Android: diario por `id` + resolución en vivo + "vía eliminada"
- [ ] Fase 9 — iOS: réplica EXACTA de fases 5–8 (paridad)

**Próximo paso**: Fase 1 (abajo).

---

## Convenciones técnicas comunes (leer una vez)
- **`geometry`**: `"POINT"` (default, todo lo actual) | `"LINE"` (muro).
- **`path`**: JSON `[[lat,lon],...]` con los vértices de la polilínea (base del muro).
  Vacío/null si POINT. Recomendado límite ~50 puntos.
- **`direction`**: `"LTR"` (default) | `"RTL"` → sentido de numeración de las vías.
- **Propuesta de estado completo**: la contribución de un muro NO manda solo lo
  nuevo, manda el **muro entero deseado**. El editor **siempre precarga el estado
  actual** del muro y el usuario lo modifica → así nada se pierde por accidente
  (añadir foto = el resto sigue en el payload). Omitir algo = quitarlo (se marca
  ROJO en el diff para que el admin lo vea).
- **`lineId` en cada vía del payload**: las vías existentes llevan su id; las nuevas
  van con `lineId=null`. Es lo que permite el diff (movida vs nueva) y el merge no
  destructivo (preservar ids). Reutilizamos `bloquesJson` añadiéndole `lineId`,
  `faceOrder`, `sortOrder` por entrada.

---

## Fase 1 — Backend: geometría + path + dirección
**Objetivo**: una piedra puede ser un MURO (polilínea). Crear/leer/editar muros por
API. Sin diff todavía (eso es fase 2). Aditivo: todo lo existente = POINT.

**Ficheros** (`MeteoMontanaAPI`):
- `db/migration/V28__block_geometry.sql` (nuevo): `ALTER TABLE school_blocks ADD
  COLUMN geometry VARCHAR(8) NOT NULL DEFAULT 'POINT'`, `ADD COLUMN path TEXT`,
  `ADD COLUMN wall_direction VARCHAR(4) NOT NULL DEFAULT 'LTR'`.
- `domain/model/SchoolBlock.java`: enum `Geometry {POINT, LINE}` + campos
  `geometry`, `path` (String JSON), `direction` (String). Constructores: añadir
  variante con los nuevos; los viejos delegan con POINT/null/LTR.
- `persistence/jpa/SchoolBlockJpaEntity.java`: columnas + setters (patrón discipline).
- `persistence/jpa/JpaSchoolBlockRepositoryAdapter.java`: mapear en ambos sentidos.
- `application/blocks/SchoolBlockUseCase.java`: `CreateBlockRequest` +
  `BlockDto` ganan `geometry`/`path`/`direction`; `create`/`update` los aplican
  (solo para BLOCK; PARKING/ZONE = POINT).
- `application/contribution/ContributionRequest.java` +
  `domain/model/PendingContribution.java` + su JPA + `SubmitContributionUseCase`:
  añadir `geometry`/`path`/`direction` (patrón discipline, constructor de compat).
- `application/contribution/ReviewContributionUseCase.java` (`createBlock`): fijar
  geometry/path/direction en la piedra materializada.

**Pasos**: copiar el patrón EXACTO de la feature `discipline` (sesión 2026-06-20),
es el mismo tipo de cambio aditivo en las mismas capas.

**Aceptación**: compila + `ClimbScoreCalculatorTest`/`SchoolBlockRepositoryFetchTest`
verdes. `POST /api/schools/{id}/blocks` con geometry=LINE + path se lee de vuelta
con esos campos en `GET .../blocks`.

## Fase 2 — Backend: propuesta de estado completo + diff + merge no destructivo
**Objetivo**: una contribución de muro lleva el estado completo (path + vías con
`lineId`/orden); al aprobar se hace **merge no destructivo** preservando ids; y se
expone un **diff** para que el admin vea qué cambia.

**Ficheros** (`MeteoMontanaAPI`):
- `bloquesJson`: cada entrada gana `lineId` (String|null), `faceOrder`, `sortOrder`.
- `ReviewContributionUseCase`: al aprobar un BOULDER con `targetBlockId` y muro:
  reconciliar a estado propuesto → actualizar vías existentes por `lineId`
  (grado/nombre/tipo/orden/foto), crear las nuevas (`lineId=null`), y las del muro
  actual ausentes del payload = borrar (omitir = quitar). Actualizar path/direction.
  Preservar ids (los journal por `lineId` siguen válidos).
- **Diff** (nuevo): `GET /api/admin/contributions/{id}/wall-diff` o incluir el diff
  en `ContributionResponse`. Estructura `WallDiff`: cambio de path (viejo/nuevo),
  lista de vías con estado `NEW|MOVED(oldPos,newPos)|REMOVED|MODIFIED(campo)|SAME`,
  orden de fotos viejo/nuevo, dirección. Se calcula comparando el payload con el
  estado ACTUAL del bloque destino.
- **Conflictos**: si el payload referencia un `lineId` que ya no existe → marcar
  ese nodo como conflicto en el diff (no fusionar a ciegas).
- **Propagación de grado/modalidad** (prepara fase 3): al aprobar, si una vía
  cambia de grado/modalidad, actualizar `journal_sessions` con ese `line_id`
  (cuando exista la columna, fase 3) — dejar el hook listo.

**Aceptación**: aprobar una propuesta de muro hace merge sin borrar lo no tocado;
el endpoint de diff devuelve added/moved/removed correctos en un test.

> ✅ HECHO: `reconcileWall` en `ReviewContributionUseCase` (se dispara con
> BOULDER + targetBlockId + geometry presente; preserva ids, actualiza/añade/
> quita por `lineId`, set sortOrder=orden, faceOrder por foto, path/dirección).
> `WallDiffCalculator` (lógica PURA) + `WallDiffCalculatorTest` (NEW/MOVED/
> MODIFIED/REMOVED/CONFLICT). El payload `bloquesJson` admite `lineId` por vía.
> **PENDIENTE para Fase 7**: exponer el diff por HTTP (endpoint o en
> `ContributionResponse`) y consumirlo en la UI del admin. La verificación
> end-to-end del merge llega con el editor (Fase 6) + prueba en dispositivo.

## Fase 3 — Backend: enganche del diario por `lineId` + propagación
**Objetivo**: el diario apunta a la vía por id estable; los cambios de grado/
modalidad de una vía se reflejan en TODOS los perfiles.

**Ficheros** (`MeteoMontanaAPI`):
- `V29__journal_line_id.sql`: `ALTER TABLE journal_sessions ADD COLUMN line_id VARCHAR(64)`.
- `JournalSession`/JPA/adapter + `CreateJournalRequest`/`JournalSessionDto`: campo
  `lineId` (patrón discipline).
- Al **aprobar** una contribución que cambia grado/modalidad de una vía: `UPDATE
  journal_sessions SET grade=?, discipline=? WHERE line_id=?` → re-sync de TODOS.
  (Para entradas viejas sin line_id, fallback por nombre opcional.)
- Al **borrar** una vía: NO tocar `journal_sessions` (las entradas se quedan como
  histórico; el cliente las pinta en gris si el id ya no resuelve).

**Aceptación**: cambiar el grado de una vía y aprobar → `journal_sessions` con ese
line_id quedan al nuevo grado (test).

## Fase 4 — Shared (KMP)
**Objetivo**: propagar todo lo de fases 1–3 al código compartido.
**Ficheros** (`shared/`): `Block.geometry/path/direction`; `BlockDto` + mapeo;
`ContributionRequest.geometry/path/direction`; `bloquesJson` con `lineId`/orden;
`JournalSession.lineId` + `CreateJournalRequest.lineId` + DTOs/mapeos; modelo
`WallDiff` + use case `GetWallDiffUseCase`; `GetJournalViaInfoUseCase` resuelve por
`lineId` (fallback nombre) y devuelve grado/foto/posición en vivo. Exponer lo nuevo
en `IosDependencyContainer`.
**Aceptación**: `shared` y `app` compilan (dummy google-services).

## Fase 5 — Android: render del muro
**Objetivo**: los muros se ven como polilínea, no como marcador; colapsados por sector.
**Ficheros**: `SchoolMap.kt` (+ `SchoolsMapPanel`, `FullScreenMapDialog`): si
`geometry==LINE` dibujar polilínea (patrón `CumbrePolyline`/MapLibre PolylineOptions)
con etiqueta en el midpoint; tap en la línea → `onBlockTap`. Muros de vía colapsados
bajo su sector por defecto.
**Aceptación**: un muro se ve como línea; al tocarlo abre su ficha; sin marcadores apelotonados.

## Fase 6 — Android: editor de muro
**Objetivo**: proponer/editar un muro: trazar la polilínea (varios taps), arrastrar
fotos/vías para ordenar, flag de dirección, **precargar estado actual**, enviar UNA vez.
**Ficheros**: `ProposeContributionFlow.kt` (+ `BoulderBloqueForm`, `EditBlockDialog`):
selector geometría PUNTO/MURO; modo "traza el muro" (acumula taps en el mapa →
`path`); reordenar fotos y vías con arrastre (Compose reorderable); toggle dirección;
preview de numeración. El submit manda el estado completo (vías con `lineId`).
**Aceptación**: se propone un muro con vías ordenadas; una sola propuesta; números coherentes.

## Fase 7 — Android: vista de diff del admin
**Objetivo**: el admin ve qué cambia antes de aprobar.
**Ficheros**: `ContributionCard.kt` / `AdminScreen`: mapa con polilínea vieja (gris)
+ nueva (sólida); lista de vías con badges NUEVA/MOVIDA(#a→#b)/QUITADA/MODIFICADA;
orden de fotos viejo→nuevo; dirección; aviso de conflicto. Usa `GetWallDiffUseCase`.
**Aceptación**: el admin distingue claramente longitud, orden y vías cambiadas.

## Fase 8 — Android: diario por id + en vivo + "vía eliminada"
**Objetivo**: el tic se engancha por `lineId`; el perfil muestra grado/foto/posición
en vivo; si la vía se borra, la entrada se queda en gris.
**Ficheros**: `SchoolDetailViewModel.toggleLine` (pasa `line.id` → `CreateJournalRequest.lineId`);
deep-link por `lineId` (no por nombre); `JournalEntriesScreen`/`ProfileScreen` pintan
en vivo (grado actual) y "vía eliminada" si el id no resuelve; outbox engancha el
`lineId` al sincronizar.
**Aceptación**: cambiar grado → perfil 6b; reordenar → deep-link correcto; borrar vía
→ entrada en gris, no desaparece.

## Fase 9 — iOS: paridad
**Objetivo**: réplica EXACTA de fases 5–8 en Swift (`MapLibreView`/`MarkerRenderer`,
`ProposeFlow`, `AdminView`, `SchoolDetailView`/`AccountView`/`UsersView`). OJO orden
SKIE. Verifica el CI de iOS.
**Aceptación**: el CI de iOS compila; comportamiento idéntico a Android.

## ❓ Decisiones abiertas menores (resolver al llegar)
- Convertir una piedra-punto existente en muro (probable sí, vía editar).
- Anclaje foto→tramo del muro (tocar el mapa abre la foto del tramo): fase posterior.
- Formato/límite exacto de `path`; UI concreta de arrastre (Compose reorderable / iOS `onMove`).
