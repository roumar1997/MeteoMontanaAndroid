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
| `sort` | `DISTANCE` \| `GRADE_ASC` \| `GRADE_DESC` | por defecto: distancia si hay `lat/lon`, si no, por grado |

**Sin `q` y con al menos un filtro** → modo "explorar" (antes solo existía el
modo "buscar por texto"). Límite de página **30**, con `offset` para "cargar
más" — los límites fijos de 15/20 actuales no sirven para explorar sin texto,
donde puede haber cientos de coincidencias.

`LineHit` gana dos campos aditivos: `lat`, `lon` (de la piedra, para que el
cliente pueda mostrar/ordenar sin una segunda llamada).

---

## 4. Interfaz

### 4.1 La pestaña

`SchoolListScreen` gana un **selector Escuelas ⇄ Bloques** arriba, estilo
segmented control Cumbre (dos chips grandes, uno activo). Cambia solo la
lista de abajo; la cabecera (buscador, botones) se queda.

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

**Fase 2 — Android**: pestaña Escuelas/Bloques en `SchoolListScreen` +
`BlockSearchViewModel` (nuevo, reutiliza `SchoolFiltersBar` con la sección de
grado añadida) + lista de resultados reutilizando el estilo de
`SchoolListItem`.

**Fase 3 — iOS**: espejo exacto de la Fase 2, mismo nombre de componentes en
Swift, paridad de tabs y filtros.

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
