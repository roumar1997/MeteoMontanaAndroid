# APPROACH_DESIGN.md — Aproximaciones (del parking al sector)

> Estado: **diseño cerrado, sin implementar**. Documento hermano de
> `WALLS_DESIGN.md` y `MEETUPS_DESIGN.md`. Leer antes de tocar nada de
> aproximaciones, chinchetas o grabación de recorrido.

---

## 0. Resumen en una línea

El usuario **graba andando** el camino del parking al sector, lo **peina en el
mapa**, y otros lo **siguen sin cobertura** con su punto azul encima de la
línea; las **chinchetas** (foto y/o texto) resuelven las bifurcaciones donde
todo el mundo se pierde.

---

## 1. 🎯 Problema

Google Maps te lleva al parking y ahí te abandona. El monte no tiene calles:
la parte difícil de una escuela desconocida no es llegar en coche, son los
20 minutos siguientes. Y el problema real **no es la dirección general** —
es que hay tres cruces donde todo el mundo tira por donde no es.

Hoy Cumbre tiene el destino (`PARKING`, `ZONE`, `BLOCK`) pero no el
**camino entre ellos**.

---

## 2. ✅ Decisiones cerradas

### 2.1 Se graba, y LUEGO se peina en el mapa

Dibujar a mano un sendero de monte sobre el mapa es incómodo (probado).
Grabar andando da el trazo honesto pero sucio (el GPS salta bajo arbolado).

**Las dos cosas, en ese orden**: grabas → la app simplifica → tú arrastras
los cuatro puntos que se fueron. La grabación pone el esqueleto, el editor
lo peina.

### 2.2 Dos estados, no revisión previa obligatoria

Son 191 escuelas: exigir que un admin recorra cada aproximación antes de
publicarla mata la función.

| Estado | Quién lo pone | Cómo se ve |
|---|---|---|
| `UNVERIFIED` | automático al subir | línea **discontinua** + aviso "Sin verificar" |
| `VERIFIED` | admin, o consenso de N usuarios | línea **continua**, sin aviso |

Mostrar un dato sin verificar **avisando** es más seguro —legal y
éticamente— que afirmar que un acceso es correcto sin haberlo pisado.
Mismo patrón que el grado por consenso.

### 2.3 La chincheta admite foto Y/O texto — nunca vacía

Es el punto que más se puede torcer. Reglas:

- **Foto + texto** → lo ideal
- **Solo foto** → válido (una foto del cruce ya dice mucho)
- **Solo texto** → válido ("en la bifurcación, por la izquierda")
- **Ninguno de los dos** → **rechazado en cliente y en BD** (`CHECK`)

La foto **se coloca sola** leyendo sus coordenadas EXIF (ya existe
`readPhotoLocation`, de "piedra desde una foto"). Si la foto no lleva
coordenadas o cae a más de `RADIO_ESCUELA_KM` de la escuela, se pide colocar
la chincheta a mano sobre la línea.

### 2.4 Reutilizamos la polilínea que ya existe

`Block.linePath` ya guarda `"[[lat,lon],...]"` para los muros `LINE`. Una
aproximación **es esa misma estructura**. Y `simplifyStroke` (Douglas-Peucker,
en `shared/TopoRenderer.kt`) ya limpia trazos temblorosos: sirve igual para
el ruido del GPS.

### 2.5 Servicio en primer plano, NO permiso de segundo plano

`ACCESS_BACKGROUND_LOCATION` en Play exige formulario, vídeo demostrativo y
semanas de revisión con rechazos frecuentes. **No se pide.**

En su lugar, **servicio en primer plano con notificación permanente**
(`foregroundServiceType="location"`, `FOREGROUND_SERVICE_LOCATION`), arrancado
con la app abierta — así funcionan Strava y Wikiloc. En iOS,
`UIBackgroundModes: location` + `allowsBackgroundLocationUpdates` con la barra
azul, **manteniendo `WhenInUse`** (no se pide "Siempre").

### 2.6 El contenido sigue siendo del usuario (⚠️ NO tocar esto)

En julio de 2026 los términos se corrigieron a propósito para que el usuario
**conserve la titularidad** y solo conceda licencia. **No revertirlo.** La
exención de responsabilidad de plataforma (LSSI art. 16 / DSA) ampara a quien
**aloja contenido de terceros**; si declaras que lo subido pasa a ser tuyo,
pasas a responder de ello como contenido propio.

Lo que sí hace falta es **ampliar la licencia**, no la propiedad — texto
propuesto para la sección 4 de `terms.html`:

> Al aportar al **catálogo comunitario** (escuelas, sectores, aproximaciones y
> sus chinchetas), concedes una licencia irrevocable para conservar, **mostrar,
> modificar y corregir** ese dato dentro de Cumbre, incluso si cierras tu
> cuenta. El contenido personal (fotos de perfil, diario) sigue siendo
> retirable en cualquier momento.

Modelo OpenStreetMap/Wikipedia: lo personal se retira, lo aportado al catálogo
compartido se queda una vez otros lo han corregido.

> **PENDIENTE ANTES DE PUBLICAR**: que un abogado revise las secciones 4 y 6 de
> `terms.html`. Guiar gente por monte, con accesos en conflicto de por medio, no
> es lo mismo que mostrar un punto en un mapa.

### 2.7 GPX: importar sí, republicar no

Parsear GPX es XML trivial (`<trkpt lat lon>`) — parser a mano en `commonMain`,
sin dependencia nueva.

- **Importar un GPX propio para uso personal** → correcto.
- **Publicarlo en el catálogo comunitario** → **solo si lo grabaste tú**. Los
  tracks de Wikiloc tienen licencia de sus autores; republicarlos sin más no
  procede.

---

## 3. 📦 Modelo de datos (Postgres)

```sql
-- V60
CREATE TABLE approaches (
    id              UUID PRIMARY KEY,
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    from_block_id   UUID REFERENCES school_blocks(id) ON DELETE SET NULL, -- parking
    to_block_id     UUID REFERENCES school_blocks(id) ON DELETE SET NULL, -- sector/piedra
    name            VARCHAR(120),
    path_json       TEXT NOT NULL,           -- "[[lat,lon],...]" (= Block.linePath)
    distance_m      INTEGER,                 -- calculado al guardar
    ascent_m        INTEGER,                 -- desnivel +, si hay altitud
    duration_min    INTEGER,                 -- medido al grabar, o estimado
    source          VARCHAR(20) NOT NULL,    -- RECORDED / DRAWN / GPX
    status          VARCHAR(20) NOT NULL,    -- UNVERIFIED / VERIFIED
    author_uid      VARCHAR(128) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_approaches_school ON approaches(school_id);

CREATE TABLE approach_pins (
    id           UUID PRIMARY KEY,
    approach_id  UUID NOT NULL REFERENCES approaches(id) ON DELETE CASCADE,
    lat          DOUBLE PRECISION NOT NULL,
    lon          DOUBLE PRECISION NOT NULL,
    position_idx INTEGER NOT NULL,        -- orden a lo largo del track
    kind         VARCHAR(20) NOT NULL,    -- FORK / LANDMARK / HAZARD / KEY
    message      TEXT,                    -- puede ser NULL
    photo_path   VARCHAR(255),            -- ruta relativa R2, puede ser NULL
    author_uid   VARCHAR(128) NOT NULL,
    status       VARCHAR(20) NOT NULL,    -- UNVERIFIED / VERIFIED
    created_at   TIMESTAMP NOT NULL DEFAULT now(),

    -- Una chincheta SIEMPRE aporta algo. Este es el único CHECK del diseño.
    CONSTRAINT chk_pin_has_content
        CHECK (message IS NOT NULL OR photo_path IS NOT NULL)
);
CREATE INDEX idx_pins_approach ON approach_pins(approach_id, position_idx);
```

> **⚠️ Lección de `chk_start_type` (bug SEMI, 2.19.0)**: `kind`, `status` y
> `source` van **VARCHAR SIN `CHECK`**. Añadir un valor nuevo al enum no debe
> exigir migración. El único `CHECK` es el de contenido de la chincheta, que
> es una invariante de negocio real, no una lista cerrada.

**Tamaño**: 30 min a 1 punto/s = 1.800 puntos; tras simplificar ~150 → **~5 KB**
en JSON. Va directo en Postgres, **no toca R2** (solo las fotos de chincheta).

### Contribuciones

El enum del backend pasa a:

```java
public enum Type { PARKING, BOULDER, SECTOR, POSITION_CORRECTION,
                   ASSIGN_SECTOR, APPROACH, APPROACH_PIN }
```

Aditivo. Al aprobar: `APPROACH` materializa una fila en `approaches`,
`APPROACH_PIN` una en `approach_pins`.

---

## 4. 🔌 Endpoints

**Públicos**

| Endpoint | Qué hace |
|---|---|
| `GET /api/schools/{id}/approaches` | Aproximaciones + sus chinchetas |

**Auth**

| Endpoint | Qué hace |
|---|---|
| `POST /api/schools/{id}/contributions` | tipo `APPROACH` (payload con `pathJson`) |
| `POST /api/schools/{id}/contributions` | tipo `APPROACH_PIN` (payload con `approachId`) |
| `POST /api/photo/upload` | ya existe — prefijo nuevo `approach-pins/` |

**Admin**

| Endpoint | Qué hace |
|---|---|
| `GET /api/admin/contributions` | ya existe, ahora devuelve también los dos tipos nuevos |
| `POST /api/admin/contributions/{id}/approve` | ya acepta `editedBloquesJson`; se generaliza a `editedPayload` |
| `PATCH /api/admin/approaches/{id}` | corregir línea/nombre/estado sobre una ya publicada |
| `DELETE /api/admin/approaches/{id}/pins/{pinId}` | quitar chincheta |

---

## 5. 🛠️ Editor de admin (la parte delicada)

Requisito explícito de Rodrigo: **poder corregir la línea aunque sea un
poquito, quitar chinchetas, y VER de un vistazo qué trae cada una.**

### 5.1 Corregir la línea

Nunca se rehace entera. Cuatro gestos, todos sobre el mapa a pantalla completa:

| Gesto | Acción |
|---|---|
| Arrastrar un vértice | mover ese punto |
| Tocar un vértice | menú → **borrar punto** |
| Tocar un segmento | **insertar** un punto ahí |
| Barra inferior | **recortar inicio / recortar final** |

El recorte de extremos es el más usado: las grabaciones empiezan con el móvil
en el bolsillo dando vueltas por el parking.

Botones fijos: **DESHACER**, **SIMPLIFICAR** (reusa `simplifyStroke`),
**ORIGINAL** (vuelve al trazo tal como se subió).

### 5.2 Ver el diff antes de aprobar

Igual que en `WALLS_DESIGN.md` §6 — **el admin no aprueba a ciegas**:

- Trazo **propuesto** en terracota, trazo **original** en gris tenue debajo
- Cabecera con `distancia · desnivel · nº puntos` (antes → después)
- El payload editado **se persiste** al aprobar (auditoría). Igual que
  `editedBloquesJson` en "editar y aprobar".

### 5.3 Lista de chinchetas — que se vea QUÉ trae cada una

Debajo del mapa, una fila por chincheta. **Esta es la parte que hay que
cuidar**: el admin tiene que distinguir de un vistazo si hay foto, texto o
ambos, sin abrir nada.

```
┌──────────────────────────────────────────────────────────┐
│  ◆   Bifurcación del pino seco              ┌────────┐   │
│ FORK  "Aquí a la derecha, la de la          │ [foto] │   │
│       izquierda va al río"                  │ 88×88  │   │
│       FOTO + TEXTO · @jara · 12 ago         └────────┘ ⋮ │
├──────────────────────────────────────────────────────────┤
│  ▲   Paso resbaladizo                       ┌────────┐   │
│ HAZ   (sin texto)                           │ [foto] │   │
│       SOLO FOTO · @kari · 10 ago            └────────┘ ⋮ │
├──────────────────────────────────────────────────────────┤
│  ●   Cruce de la valla                      ┌ ─ ─ ─ ┐    │
│ LAND  "Salta por el paso de la izquierda,     SIN      ⋮ │
│        no por encima"                         FOTO         │
│       SOLO TEXTO · @rodrigo · 9 ago         └ ─ ─ ─ ┘    │
└──────────────────────────────────────────────────────────┘
```

Reglas de esa fila:

- **Izquierda**: icono por `kind` con su etiqueta mono (`FORK` / `HAZ` /
  `LAND` / `KEY`)
- **Centro**: el mensaje. Si no hay, `(sin texto)` en cursiva `ink3`
- **Derecha**: miniatura **88×88** si hay foto; si no, **recuadro punteado con
  "SIN FOTO"** — el hueco se ocupa igual, así la lista no baila y la ausencia
  se ve tanto como la presencia
- **Bajo el mensaje**, en `EyebrowTextStyle` (mono 10sp, tracking 1.8):
  **`FOTO + TEXTO`** / **`SOLO FOTO`** / **`SOLO TEXTO`**, seguido de autor y
  fecha
- **`⋮`** a la derecha del todo: **Editar texto · Cambiar foto · Mover en el
  mapa · Borrar chincheta**

La miniatura es pulsable → foto a pantalla completa (reusa
`FullScreenPhotoDialog` / `FullScreenPhotoView`).

Zonas de toque ≥ 40 dp en `⋮` y en la miniatura (regla ya aprendida en notas
y comentarios).

---

## 6. 📱 Interfaz de usuario

### 6.1 Ficha de escuela

Sección **APROXIMACIONES** entre el mapa y las piedras:

```
APROXIMACIONES
┌────────────────────────────────────────────┐
│ Parking alto → Sector Techos               │
│ 1,2 km · +180 m · ~25 min · 4 chinchetas   │
│ ✓ VERIFICADA                     [ SEGUIR ]│
└────────────────────────────────────────────┘
┌────────────────────────────────────────────┐
│ Parking bajo → Pradera                     │
│ 0,8 km · +60 m · ~15 min · 1 chincheta     │
│ ⚠ SIN VERIFICAR                  [ SEGUIR ]│
└────────────────────────────────────────────┘
        [ + GRABAR APROXIMACIÓN ]
```

### 6.2 Grabar

Pantalla completa sobre el mapa. Arriba: **origen → destino** (se eligen de
los bloques existentes). Abajo: **INICIAR / PAUSAR / TERMINAR**, cronómetro,
distancia y **precisión GPS actual en metros**.

Al terminar → pantalla de peinado (los mismos gestos del editor de admin,
§5.1) → **GUARDAR** → entra en la cola como `APPROACH` contribución.

**Salvaguardas:**
- Se descartan puntos con precisión **> 25 m** (bajo arbolado el GPS delira)
- **Auto-pausa** tras 5 min quieto; **auto-parada y aviso** tras 20 min
  (la gente se olvida de parar y se come la batería)
- Frecuencia **1 punto/s o 5 m**, lo que ocurra antes
- El servicio se cae con la app → el track a medias se guarda en local
  (SQLDelight) y se ofrece recuperar al volver

### 6.3 Seguir

Sin navegación giro a giro, sin voz: **la línea y tu punto azul**, que es el
99% del valor y el 1% del riesgo.

- Línea continua (verificada) o discontinua (sin verificar)
- Chinchetas sobre la línea, pulsables → hoja con foto grande y texto
- **Aviso al alejarte >60 m** de la línea: vibración + banner "Te has salido
  del camino". Nada más. Sin instrucciones de vuelta.

### 6.4 Añadir chincheta (cualquier usuario)

Desde una aproximación abierta → **+ CHINCHETA**:

1. Tipo: **Bifurcación / Referencia / Peligro / Paso clave**
2. Foto (opcional) — **Hacer foto** o **Galería**, se coloca sola por EXIF
3. Mensaje (opcional)
4. Posición: la del EXIF, o tocando sobre la línea

**Botón GUARDAR deshabilitado si no hay ni foto ni mensaje**, con el texto
"Añade una foto o un mensaje" bajo el botón. Nunca una chincheta vacía.

---

## 7. 🌐 Sin cobertura

Lo crítico ya está resuelto: `OfflineTileManager` existe en **ambas
plataformas** y se dispara al guardar una escuela. Falta:

1. Que **guardar escuela** arrastre también sus aproximaciones, chinchetas y
   **fotos de chincheta** (ahora solo baja tiles + bloques)
2. Tablas SQLDelight nuevas: `SavedApproach`, `SavedApproachPin`
3. Grabar y crear chinchetas **sin red** → `Outbox`, que ya existe

Sin esto la función no sirve: precisamente donde hace falta es donde no hay
cobertura.

---

## 8. 🧩 Cambios en KMP

`LocationProvider` solo tiene `current()` (un disparo). Hace falta el flujo
continuo, con el patrón bridge de `KMP_MIGRATION.md`:

```kotlin
interface LocationProvider {
    fun hasPermission(): Boolean
    @Throws(Exception::class) suspend fun current(): UserLocation?

    /** Flujo continuo mientras dure la grabación. Android: FusedLocation
     *  + servicio en primer plano. iOS: CLLocationManager con background mode. */
    fun track(): Flow<UserLocation>          // ← nuevo
}
```

`UserLocation` necesita `accuracyM` y `altitudeM` (para filtrar ruido y
calcular desnivel).

Nuevo en `shared`: `ApproachGeometry.kt` — distancia (reusa
`PhotoPlacement.kmBetween`), desnivel acumulado, distancia punto→polilínea
(para el aviso de "te has salido"; la proyección acotada **ya está resuelta**
en el imán de topos, `TopoRenderer`).

---

## 9. 🚦 Riesgos y cómo se cubren

| Riesgo | Cobertura |
|---|---|
| Track que cruza propiedad privada | Aviso "sin verificar" + **botón denunciar acceso** (reusa `content_reports`) + retirada diligente al aviso, ya en términos §4 |
| Espacio protegido (Guadarrama, nidificación) | Igual, más revisión admin prioritaria de las denuncias por acceso |
| Precisión GPS bajo arbolado | Filtro >25 m + simplificación + a medio plazo **consenso de varios tracks** |
| Batería | Auto-pausa y auto-parada |
| Rechazo en Play | Servicio en primer plano, **sin** `ACCESS_BACKGROUND_LOCATION` |
| `PrivacyInfo` de iOS declara `CoarseLocation` | **Ya pendiente** (`project_privacy_declarations_pending`), esta función lo agrava — arreglar en la misma release |

---

## 10. 🗺️ Plan de implementación

**Fase 0 — Términos** *(antes de escribir código)*
Redactar §4 y §6 con el texto de §2.6, pasar a abogado.

**Fase 1 — Ver** *(sin permisos nuevos, sin riesgo de tienda)*
V60 + endpoints de lectura + modelo compartido + sección APROXIMACIONES +
pintar línea y chinchetas en el mapa + seguir con el punto azul.
Se siembra con tracks grabados por el admin (GPX importado a mano).

**Fase 2 — Chinchetas**
Alta por usuario (foto y/o texto, EXIF), contribución `APPROACH_PIN`,
hoja de detalle, lista y borrado en admin (§5.3).

**Fase 3 — Grabar**
Puerto `track()` + servicio en primer plano en ambas plataformas + pantalla
de grabación + peinado + contribución `APPROACH`. Es la fase que toca ficha
de tiendas.

**Fase 4 — Admin completo**
Editor de línea (§5.1), diff (§5.2), `PATCH` sobre publicadas.

**Fase 5 — Offline + consenso**
`SavedApproach`/`SavedApproachPin`, fotos de chincheta al guardar escuela,
`Outbox`; verificación por consenso de N recorridos.

**Fase 6 — GPX**
Importar para uso personal; publicar solo lo grabado por uno mismo.

---

## 11. Notas de implementación

- La línea de aproximación **no es un `school_block`**: mezclarla contaminaría
  todas las consultas de piedras y muros. Tabla propia.
- El pintado reutiliza el patrón de `DrawOp.LinePath` (dash para "sin
  verificar", igual que las franjas de tramos compartidos).
- Regla de siempre: nada de medidas fijas, todo adaptable
  (`feedback_responsive_always`).
- Al tocar la cadena, **espejo exacto en iOS** — Android e iOS a paridad.
