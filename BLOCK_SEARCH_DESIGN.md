# BLOCK_SEARCH_DESIGN.md — Buscador de vías por grado, cercanía y roca

> Estado: **diseño cerrado, sin implementar**. Documento hermano de
> `WALLS_DESIGN.md`, `MEETUPS_DESIGN.md` y `APPROACH_DESIGN.md`.

---

## 0. Resumen en una línea

Dos filtros hermanos, uno global y uno local:

1. **Global** (§1-6): pestaña "Bloques" en Escuelas, filtra por grado/
   cercanía/roca **en todo el catálogo**, backend nuevo.
2. **Local** (§7): dentro de UNA escuela ya abierta, filtra por grado **sus
   propias piedras** (ej. "todos los 7A-7B de Zarzalejo") — **sin backend**,
   la escuela ya trae todas sus vías cargadas.

Se implementan por separado, empezando por el local (más barato, valor
inmediato) y luego el global.

---

## 1. Decisiones cerradas

### 1.1 La unidad del resultado es la LÍNEA (vía), no la piedra

Confirmado con Rodrigo. Una piedra con vías de 5A y 7B no "aparece entera" al
filtrar 7A-7C: **sale la vía de 7B**, con su piedra y escuela debajo. Si esa
piedra tiene además una vía de 5A, esa vía no sale (a menos que también caiga
en el filtro). Encaja con lo que ya existe: `LineSearchHit` ya es por línea.

### 1.2 Se reutiliza el buscador existente, no se crea uno nuevo

Ya existe `GET /api/search/lines?q=` (`LineSearchController` /
`SearchLinesService`), texto libre, límite 15+10. Se **amplía**, no se
duplica: el mismo endpoint acepta filtros opcionales. Sin `q` y sin filtros
seguiría devolviendo vacío (comportamiento actual preservado); con filtros
puestos, `q` pasa a ser opcional.

### 1.3 El grado se compara con la MISMA fórmula que ya existe

`gradeArgb()` en `shared/.../domain/util/TopoRenderer.kt` ya convierte un
grado francés (`[3-9][ABCD]?[+]?`) en un **score numérico**:
`score = número*100 + letra*10 + (+ ? 1 : 0)`. Se porta esa misma fórmula al
backend (Java) — un único criterio de orden en toda la app, cero divergencia.

**Decisión de rendimiento**: no se recalcula en cada consulta. Se añade una
columna `grade_score SMALLINT` a `block_lines`, calculada al guardar/editar
una vía (aditivo, `NULL` en vías con grado no reconocible tipo "PROY"),
**indexada** — así el filtro de rango es una consulta indexada normal, no un
regex por fila en cada búsqueda.

### 1.4 Distancia: desde la PIEDRA, no desde la escuela

`school_blocks` ya tiene `lat`/`lon` propios (piedra por piedra, más preciso
que el punto general de la escuela). El filtro de distancia usa esas
coordenadas, con la fórmula haversine que ya existe en
`PhotoPlacement.kmBetween`.

### 1.5 Tipo de roca: se hereda de la escuela

`rockType` es un campo de `School`, no de la piedra — no hay dato más fino.
El filtro de roca compara contra `school.rockType`, igual que ya hace
`SchoolFiltersBar` para las escuelas. Mismos valores (`ROCK_TYPES`), mismos
chips, cero código nuevo de UI para esa parte.

### 1.6 Disciplina (Vía/Bloque): ya existe en la piedra

`school_blocks.discipline` (BOULDER/ROUTE) ya está. El filtro reutiliza
`StyleFilter` (o el equivalente), mismo patrón que "ESTILO" en
`SchoolFiltersBar`.

---

## 2. Modelo de datos

```sql
-- V64 (siguiente libre tras V63)
ALTER TABLE block_lines ADD COLUMN grade_score SMALLINT;
CREATE INDEX idx_block_lines_grade_score ON block_lines(grade_score);

-- Backfill de las vías existentes (una vez, al desplegar)
-- lo hace un job/migración de datos con la misma fórmula portada a Java,
-- NO una expresión SQL: la fórmula vive en un solo sitio (GradeScore.java,
-- espejo de gradeArgb) para no divergir en dos lenguajes.
```

`grade_score` se recalcula cada vez que se crea o edita una vía (alta nueva,
"editar y aprobar" del admin, corrección de vía) — un solo punto de escritura
en el caso de uso de materialización, no en cada lectura.

---

## 3. Endpoint

`GET /api/search/lines` — parámetros nuevos, todos opcionales y aditivos:

| Parámetro | Tipo | Significado |
|---|---|---|
| `q` | string | texto libre (ya existe) |
| `gradeMin`, `gradeMax` | string (ej. `6A`, `7B+`) | se convierten a `grade_score` en el propio backend con la misma fórmula |
| `discipline` | `BOULDER` \| `ROUTE` | ya existe en `school_blocks` |
| `rockTypes` | lista | filtra por `school.rockType` |
| `lat`, `lon`, `maxDistanceKm` | double | filtra y ordena por cercanía a la piedra |
| `schoolIds` | lista (§8.2b) | restringe a escuelas concretas elegidas dentro del radio — aditivo sobre `maxDistanceKm`, no lo sustituye |
| `orientations` | lista (§8.1) | aspecto votado del bloque (`N`/`NE`/.../`NO`); sin voto = sale igual |
| `sort` | `DISTANCE` \| `GRADE_ASC` \| `GRADE_DESC` \| `SCHOOL_SCORE` (§8.5) | por defecto: distancia si hay `lat/lon`, si no, por grado |

**Sin `q` y con al menos un filtro** → modo "explorar" (antes solo existía el
modo "buscar por texto"). Límite de página **30**, con `offset` para "cargar
más" — los límites fijos de 15/20 actuales no sirven para explorar sin texto,
donde puede haber cientos de coincidencias.

`LineHit` gana dos campos aditivos: `lat`, `lon` (de la piedra, para que el
cliente pueda mostrar/ordenar sin una segunda llamada).

---

## 4. Interfaz

### 4.1 Sin pestaña — modo implícito (decisión final, Álvaro 2026-10-01)

Se probaron 3 mockups (pestañas grandes tipo segmented control; icono nuevo
que abre una pantalla aparte "Buscar por grado"; y este). Las dos primeras
exigían ponerle un NOMBRE fijo a la sección — "Bloques" no describe bien el
resultado (cada fila es una VÍA, con su piedra y escuela debajo, no una
piedra suelta), y ninguna palabra corta cubre bien "piedras tipo Bloque Y
piedras tipo Vía a la vez" (eso ya lo resuelve el filtro ESTILO de §4.2,
no hace falta que el nombre de una pestaña lo intente resolver también).

**La solución es no bautizar nada.** `SchoolListScreen` sigue siendo
"Escuelas" siempre, con su cabecera de siempre (título, "+ Aportar",
buscador, "VER MAPA") sin tocar. Se entra al modo vías tocando el rango de
GRADO dentro de FILTROS (§4.2) — en cuanto hay un `gradeMin`/`gradeMax`
puesto, la MISMA lista pasa a mostrar vías en vez de escuelas, con un aviso
quitable justo encima (reemplaza la fila de chips DISTANCIA/ESTILO/etc.,
no se apila con ella):

```
┌──────────────────────────────────────────┐
│ ▤  Viendo VÍAS · grado 7A—7B · 50 km   ✕ │
└──────────────────────────────────────────┘
```

Tocar la ✕ quita el filtro de grado y vuelve a Escuelas al instante — sin
navegación, sin pantalla que cerrar. El aviso describe LO QUE HAY, nunca una
categoría fija, así que nunca queda desactualizado si mañana se añade un
filtro nuevo (orientación, escuelas elegidas…) — se añade a la misma frase.

### 4.2 Filtros — se reutiliza `SchoolFiltersBar`, con una sección nueva

Mismo componente, mismas secciones DISTANCIA / ESTILO (→ discipline) / TIPO
DE ROCA que ya existen para escuelas. Se añade:

```
GRADO
┌──────┬──────┐
│ 3A   │ 8A+  │   ← dos selectores tipo rango (mínimo / máximo),
└──────┴──────┘      mismos chips de grado que ya se usan en el editor
```

### 4.3 Resultado

Reutiliza `SchoolListItem`-style pero por vía: nombre de la vía, grado (con
su color de `gradeArgb`, coherente con el resto de la app), nombre de la
piedra y escuela, distancia si hay ubicación. Tocar → mismo `onViaHit` que ya
navega a la escuela y abre esa vía. **Cero pantalla nueva de detalle.**

---

## 5. Plan de implementación (filtro GLOBAL)

**Fase 1 — Backend**: migración V64 + `GradeScore.java` (puerto de
`gradeArgb`) + ampliar `ContributionRequest`/casos de uso que crean/editan
vías para rellenar `grade_score` + ampliar `LineSearchController`/
`SearchLinesService`/`JpaLineSearchRepositoryAdapter` con los filtros +
backfill de vías existentes.

**Fase 2 — Android**: SIN pestaña nueva (§4.1) — `SchoolListViewModel` gana
el estado del modo vías (activo cuando `gradeMin`/`gradeMax` != null) +
`BlockSearchViewModel` o equivalente para pedir/paginar los resultados +
el aviso quitable + lista de resultados reutilizando el estilo de
`SchoolListItem`, todo dentro de `SchoolListScreen` ya existente.

**Fase 3 — iOS**: espejo exacto de la Fase 2 en `SchoolListView.swift`/
`SchoolListHeader.swift`, mismo nombre de componentes en Swift, paridad del
aviso y los filtros.

**Fase 4 — Pulido**: "cargar más" (paginación por `offset`), persistir el
último filtro usado (como ya se hace con `SchoolFilters`).

---

## 7. Filtro LOCAL — dentro de una escuela ya abierta

### 7.1 Por qué es distinto y más barato

`BlocksSection` ya recibe `blocks: List<Block>` con **todas** las vías de esa
escuela ya cargadas (cada `Block` trae sus `lines` con `grade`). No hace
falta llamar al servidor: es un `filter { }` en memoria, con la misma función
de score de §1.3 (`gradeArgb`, que ya vive en `shared/commonMain` — Android e
iOS la comparten sin puerto nuevo).

### 7.2 Interfaz

Encima de la lista/mapa de piedras de la escuela, una barra de chips de grado
colapsable — mismo patrón visual que `SchoolFiltersBar` pero con **dos**
selectores (mínimo/máximo), igual que §4.2. Por defecto oculta/plegada (no
todo el mundo quiere filtrar); un icono de embudo la despliega.

```
[ 🔽 Filtrar por grado ]
        ↓ (al tocar)
GRADO   3A ────●───────●──── 8A+
        (min: 7A)   (max: 7B)

Mostrando 4 vías de 23
```

### 7.3 Comportamiento — "poder ejecutarlas"

Con el filtro puesto:
- **En el mapa**: solo se resaltan/activan los marcadores de piedras que
  tengan AL MENOS una vía dentro del rango (el resto se atenúa, no
  desaparece — sigues viendo el contexto del sector).
- **En la lista de piedras** (si la vista es de lista): solo aparecen las
  piedras con alguna vía en rango, y **dentro de la ficha de esa piedra**,
  las vías fuera de rango se atenúan (mismo criterio que el mapa) — nunca se
  ocultan vías dentro de una piedra que sí se muestra, porque perderías
  contexto de qué más tiene esa pared.
- Tocar una piedra o vía filtrada **abre exactamente el mismo flujo que
  siempre** (`BlockDetailDialog`) — "ejecutarla" es el comportamiento normal
  de tocar, no hay pantalla nueva.

### 7.4 Plan de implementación (filtro LOCAL)

Una sola fase, sin backend:
- `shared`: función pura `filterLinesByGrade(blocks, min, max): Set<blockId>`
  (o similar), commonMain, testeable con `commonTest`.
- Android: estado de filtro en `SchoolDetailViewModel` (dos `MutableStateFlow`
  min/max, `null`/`null` = sin filtrar), aplicarlo en `BlocksSection`/
  `SchoolMap` (atenuar en vez de ocultar) y en la ficha de piedra.
- iOS: espejo exacto en `SchoolDetailView`/`SchoolMapSection`.
- Persistencia: NO se guarda entre sesiones (es un filtro de "ahora mismo
  quiero ver esto"), se resetea al salir de la escuela — distinto del filtro
  global de escuelas, que sí persiste.

---

## 6. Notas

- No hace falta tocar `chk_start_type` ni ningún `CHECK` — `grade_score` es
  nullable y no restringido, mismo criterio que `kind`/`status` en
  `APPROACH_DESIGN.md`: un valor nuevo de grado no debe romper nada.
- Sin cambios de permisos, sin riesgo legal — es una consulta sobre datos que
  ya son públicos y ya se muestran.
- Regla de siempre: nada de medidas fijas, todo adaptable
  (`feedback_responsive_always`). Paridad exacta Android/iOS.

---

## 8. Ampliación 2026-10-01 (Álvaro) — orientación, score, agrupado, reactivo

Decisiones nuevas sobre el filtro GLOBAL (§1-5), antes de implementar nada de
este documento. Arquitectura: sigue el mismo reparto hexagonal de siempre —
nada de esto crea una capa nueva, son campos/filtros que viajan por las
mismas piezas ya descritas (DTO → dominio → repositorio → use case → VM).

### 8.1 Filtro por ORIENTACIÓN (cara norte/sur/etc.) — para esquivar o buscar sol

Ya existe el dato: `GetSchoolOrientationsUseCase.invoke(schoolId): Map<String,
String>` (blockId → aspecto votado por la comunidad, `N`/`NE`/`E`/`SE`/`S`/
`SO`/`O`/`NO`). El filtro GLOBAL añade `orientations: List<String>?` como
parámetro opcional más (mismo patrón que `rockTypes`), comparando contra el
aspecto YA AGREGADO del bloque (no hace falta tocar la tabla de votos).

**Las piedras sin orientación votada SIEMPRE aparecen**, pase lo que pase el
filtro — no se ocultan por no tener dato. En su lugar, la fila de esa vía
lleva una línea aparte: `SIN ORIENTACIÓN ASIGNADA` (mono, gris, mismo tono que
`forecastCachedAt`/avisos de antigüedad) en vez del chip de cara normal. Así
quien busca "caras norte para el verano" ve también lo que nadie ha votado
todavía, en vez de que desaparezca sin explicación.

### 8.2 Resultado en vivo, sin botón "ver N vías"

Sin "VER 38 VÍAS": la lista se actualiza sola en cuanto cambia cualquier
filtro (igual que ya hace `SchoolFiltersBar` con las escuelas — no es un
patrón nuevo, es quitar el único sitio donde sí se había puesto un botón).
El contador ("38 vías encontradas") pasa de botón a texto pasivo encima de la
lista, que se actualiza con el resto. **Debounce de red** necesario: el campo
de grado/orientación no dispara una petición por cada toque, sino ~300ms
después del último cambio (mismo patrón que ya usa el buscador de texto in-app
en otros sitios) — detalle de implementación, no de interfaz.

### 8.2b Filtro por ESCUELAS concretas (elegir 1, 2, 3... dentro del radio)

Nuevo filtro multi-selección: dentro del radio elegido (los `maxDistanceKm`
de §1.4/§3), se listan las escuelas que caen en él **con su índice de
escalabilidad de hoy** — mismo cuadro de score que ya se usa en la lista de
Escuelas, mismo dato. El usuario marca 1, 2, 3 o las que quiera; sin ninguna
marcada = comportamiento de siempre (todas las del radio).

Resuelve justo lo que pide Álvaro: "estoy a 50 km, quiero ver con qué
escuelas se cumple lo que busco sin tener que entrar en cada una" — con el
filtro de escuelas + grado + orientación puestos a la vez, cada escuela
marcada muestra solo sus vías que cumplen, agrupadas (§8.3), sin tener que
abrir la ficha.

**De dónde sale el dato**: es el mismo `GetSchoolsUseCase(lat, lon, radioKm)`
que ya alimenta la lista de Escuelas — no hace falta ninguna llamada nueva,
la pestaña Bloques ya necesita conocer qué escuelas hay en el radio para
agrupar (§8.3), así que esta lista de selección es ese mismo resultado
convertido en chips, no un segundo fetch.

**Interfaz**: sección nueva en el panel de filtros, justo debajo de
DISTANCIA (es su refinamiento natural, no algo aparte): lista vertical
compacta (no chips en fila, para que quepan nombre + score + km sin cortarse),
cada fila con casilla de selección, nombre, score coloreado y distancia.
Scroll propio si el radio trae muchas escuelas.

**Aviso de ROCA MOJADA en la propia fila**: si esa escuela tiene el aviso
activo (mismo dato que la "●  MOJADA" de la lista de Escuelas y de §8.5), se
ve en la fila de selección — un punto + texto en terracota debajo del nombre,
igual que ya se pinta en todos los demás sitios. Así se decide si marcarla
ANTES de seleccionarla, no después de ver que sus vías no sirven de nada hoy.

### 8.3 Agrupar por escuela (con opción de desactivar)

Por defecto, los resultados se agrupan por escuela: cabecera por grupo con el
**nombre de la escuela + su índice de escalabilidad de HOY** (mismo cuadro de
score que ya se usa en la lista de Escuelas — color por tramo, "MUY BUENO"/
"BUENO"/etc.) y el aviso de ROCA HÚMEDA si aplica, **una sola vez por grupo**
en vez de repetirlo en cada vía. Las vías de esa escuela van debajo, sin
repetir escuela/score en cada fila.

Icono de "agrupar/desagrupar" en la cabecera de resultados (al lado del
contador): desactivado, la lista vuelve a ser plana por vía (como estaba en
§4.3), ordenada por el criterio que haya elegido el usuario (cercanía/grado).
Se recuerda la preferencia (agrupado o no) igual que ya se recuerdan los
filtros (§5 fase 4).

**Importante**: el score mostrado es el de la ESCUELA (el índice de
escalabilidad ya existente, meteorológico), no de la piedra — no existe un
score por piedra individual. Agrupar por escuela es precisamente lo que evita
tener que repetirlo vía a vía, que es el mismo problema que resuelve ya la
ficha de una escuela abierta.

### 8.4 Miniatura de foto — confirmado

Cada fila de vía lleva la miniatura de la foto de la piedra (la de su cara
real, `facesOrDerived()` — mismo criterio que el resto de la app). Ya estaba
contemplado de facto por "ver todas las imágenes" en la petición original;
queda aquí explícito.

### 8.5 Otras dos cosas que conviene añadir (propuesta, a validar)

- **Aviso de ROCA HÚMEDA por grupo**: si la escuela de ese grupo tiene el
  aviso activo (ya existe, es el mismo dato que pinta "ROCA HÚMEDA" en la
  ficha y en la lista de Escuelas), se repite en la cabecera del grupo — con
  el filtro de grado puesto, es fácil acabar centrado solo en el grado y
  olvidar que esa escuela concreta no es escalable hoy.
- **Ordenar por "mejor score"** como tercera opción en ORDENAR POR (además de
  cercanía y grado) — tiene más sentido todavía con el agrupado activado: ver
  primero las escuelas donde SÍ se puede escalar hoy con el grado que buscas.
