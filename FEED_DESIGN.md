# FEED_DESIGN.md — Pestaña Comunidad (feed social + ranking)

> **Estado: DISEÑO — sin implementar.** Desarrollo SOLO en staging
> (backend en `develop`, app en rama `claude/**` sin mergear a `main`
> hasta que Rodrigo lo valide). La app está publicada: nada de esto
> puede llegar a producción hasta el OK explícito.

## 🎯 Objetivo

Una pestaña nueva **"Comunidad"** en la tab bar (junto a Escuelas /
Quedadas / Radar / Perfil) tipo **feed de Instagram**: cuando alguien
marca una vía/bloque como **HECHO** (o sube una piedra/vía nueva),
aparece una tarjeta en el feed con la foto de la piedra y su línea
dibujada. Los demás pueden dar **me gusta** y **comentar**. Arriba, un
selector de tres vistas:

- **SIGUIENDO** — actividad solo de la gente a la que sigues.
- **TODOS** — actividad global, para descubrir gente y escuelas.
- **RANKING** — el ranking de contribuidores (la pantalla "Comunidad"
  que hoy vive en Perfil **se muda aquí**; la entrada de Perfil se
  elimina).

Se desliza verticalmente (scroll infinito con paginación).

**Frescura (requisito de Rodrigo)**: el feed no puede parecer estático.
- Orden cronológico puro, lo nuevo SIEMPRE arriba.
- **Pull-to-refresh** + recarga automática al volver la pestaña a
  primer plano (patrón ON_RESUME del panel admin, la lección del
  staleness del 2026-07-05).
- Hacia abajo, **"cargar más"** infinito para llegar a lo antiguo.

## 📱 UX

### Tab bar
Sexta pestaña **Comunidad** (icono: personas/actividad estilo Cumbre,
line-art). i18n: ES "Comunidad" / EN "Community".
⚠️ Con 6 pestañas la tab bar va justa en móviles estrechos — verificar
en el Xiaomi y en iPhone SE antes de dar por buena la disposición.
Alternativa si no cabe: Feed sustituye la posición de Radar y Radar se
mueve dentro de Escuelas (decisión de Rodrigo llegado el caso).

### Cabecera
- Título "Comunidad" + selector tipo chips/segmented (patrón del
  selector Bloque/Vía existente): **SIGUIENDO | TODOS | RANKING**.
  Persistir la última elección (DataStore / UserDefaults).
- RANKING reutiliza `CommunityScreen.kt`/`CommunityView.swift` (2.13.0)
  como contenido del tercer chip.
- Estado vacío de SIGUIENDO: "Aún no sigues a nadie" + botón que lleva
  al buscador de usuarios (`SearchUsersScreen`).

### Tarjeta de actividad (item del feed)
De arriba a abajo, estética Cumbre (borde `Rule` 1dp, radius 2dp, sin
sombras):

1. **Cabecera**: avatar + nombre (tap → perfil público) + tiempo
   relativo ("hace 2 h"). Eyebrow con el tipo: "BLOQUE HECHO" /
   "VÍA HECHA" / "PROYECTO CONSEGUIDO" / "PIEDRA NUEVA" / "VÍA NUEVA".
2. **Imagen**: foto de la cara de la piedra con **solo la línea de esa
   vía** dibujada encima (reutilizar el render de `ShareLineImage` /
   `TopoPhotoView`, que ya resuelve foto+trazo+color por grado). Si la
   vía no tiene foto/topo: tarjeta compacta sin imagen (nombre + grado
   + escuela), no se oculta.
3. **Texto**: «*Nombre de vía* · grado — piedra · sector · escuela».
   Tap → deep-link interno a la piedra (mismo router que `/s/v/...`).
4. **Acciones**: ❤ me gusta (contador, toggle, zona táctil ≥40dp) +
   💬 comentarios (contador; tap abre hoja de comentarios) + compartir
   (reutiliza `shareLineAsImage`).

### Comentarios
Bottom sheet con lista + campo de texto (patrón exacto de los
comentarios de vías: `line_comments`). Votos no; solo likes al post.

### Interacción con moderación existente (OBLIGATORIO)
- Usuarios **bloqueados** (`user_blocks`): sus posts/comentarios no se
  ven, filtrado **server-side** (patrón notas/comentarios).
- **Denunciar** post y comentario (bandera → `content_reports`, tipos
  nuevos `FEED_POST` / `FEED_COMMENT`) — requisito App Store 1.2, sin
  esto no se puede publicar.
- Usuarios suspendidos no pueden comentar (`ensureCanPost`).
- Perfil **privado** (`isPublic=false`) — **REQUISITO DURO (Rodrigo)**:
  sus posts NO salen en TODOS bajo ningún concepto; solo los ven sus
  seguidores aceptados (en SIGUIENDO), igual que hoy solo ellos ven su
  perfil. El filtrado es server-side y se evalúa en CADA lectura del
  feed (si el perfil pasa a privado después de publicar, sus posts
  desaparecen de TODOS al momento).

## 🗄️ Modelo de datos (backend)

**Decisión: tabla propia `feed_posts`, no derivar del diario con una
query.** Trade-off: derivarlo evita tabla nueva, pero el diario es
privado por naturaleza (nadie espera que su histórico se publique), no
permite likes/borrado del post sin tocar el diario, y hace imposible
paginar barato. Tabla propia = el diario sigue siendo privado y el feed
es **opt-out explícito por post**.

```sql
-- V<next>__feed.sql (Flyway, solo develop hasta validar)
CREATE TABLE feed_posts (
    id           BIGSERIAL PRIMARY KEY,
    user_uid     VARCHAR(64) NOT NULL,          -- autor (users.uid)
    school_id    BIGINT NOT NULL,
    block_id     BIGINT NOT NULL,               -- school_blocks.id
    line_id      BIGINT,                        -- block_lines.id (estable)
    kind         VARCHAR(16) NOT NULL,          -- TICK | PROJECT_DONE | NEW_BLOCK | NEW_LINE
    grade        VARCHAR(8),
    line_name    VARCHAR(120),                  -- snapshot (por si se renombra)
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted      BOOLEAN NOT NULL DEFAULT FALSE -- borrado por autor/moderación
);
CREATE INDEX idx_feed_posts_created ON feed_posts (created_at DESC);
CREATE INDEX idx_feed_posts_user    ON feed_posts (user_uid, created_at DESC);

CREATE TABLE feed_likes (
    post_id  BIGINT NOT NULL REFERENCES feed_posts(id),
    user_uid VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (post_id, user_uid)
);

CREATE TABLE feed_comments (
    id         BIGSERIAL PRIMARY KEY,
    post_id    BIGINT NOT NULL REFERENCES feed_posts(id),
    user_uid   VARCHAR(64) NOT NULL,
    body       VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted    BOOLEAN NOT NULL DEFAULT FALSE
);
```

- Comentarios **separados** de `line_comments`: un "¡qué máquina!" es
  sobre el ascenso de una persona, no información de la vía; mezclarlos
  ensuciaría la ficha de la piedra.
- La imagen NO se guarda: se compone en cliente con la foto del bloque
  + el trazo (`block_lines`), como ya hace el detalle. Cero storage.

### Creación del post
- **HECHO / proyecto conseguido**: en el flujo existente de marcar
  HECHO (entrada de diario con `lineId`), el cliente ofrece publicar y
  llama a `POST /api/feed` — por defecto **ON con opt-out visible**
  ("Publicar en el feed" ✓ desmarcable en el diálogo del tick).
  Trade-off: automático-silencioso daría más contenido pero publicaría
  actividad que la gente no espera hacer pública; manual-puro dejaría
  el feed vacío.
- **Piedra/vía nueva**: el post lo crea el **backend al APROBAR** la
  contribución (no al proponerla — si no, saldrían en el feed cosas
  que luego se rechazan). Autor del post = autor de la contribución.
- **Sin backfill**: los HECHO y piedras anteriores al estreno del feed
  NO se publican (nadie consintió publicar su histórico). El feed
  arranca vacío y se llena solo.

## 🌐 Endpoints

| Endpoint | Auth | Descripción |
|---|---|---|
| `GET /api/feed?scope=following\|all&before={cursor}&limit=20` | Bearer | Página del feed (cursor = `created_at` del último item). Devuelve post + autor (nombre/avatar) + likeCount + likedByMe + commentCount + datos para render (photoUrl firmada, strokeJson, grade) |
| `POST /api/feed` | Bearer | Publicar ascenso `{blockId, lineId, kind}` (server valida y snapshotea grado/nombre) |
| `DELETE /api/feed/{id}` | Bearer | Borrar post propio (admin: cualquiera) |
| `POST /api/feed/{id}/like` / `DELETE .../like` | Bearer | Like idempotente / quitar |
| `GET /api/feed/{id}/comments` | Bearer | Comentarios |
| `POST /api/feed/{id}/comments` | Bearer | Comentar (pasa `ensureCanPost`) |
| `DELETE /api/feed/comments/{id}` | Bearer | Borrar comentario propio (admin: cualquiera, con motivo → `moderation_actions`) |

Todo con Bearer (sin scope público): el feed es de la comunidad, y así
el filtrado de bloqueados/privacidad es trivial. `SecurityConfig`: nada
que abrir.

## 🧩 Arquitectura app (KMP, paridad Android/iOS)

- `shared/`: `FeedApi` (Ktor) + DTOs + mapping + `FeedRepository` +
  use cases (`GetFeed`, `PublishTick`, `ToggleLike`, `Comment...`) —
  patrón exacto de Social/Notes.
- **Android**: `ui/screens/feed/FeedScreen.kt` + `FeedViewModel`
  (Hilt). LazyColumn con paginación por cursor (cargar más al llegar
  al final). Render de tarjeta: extraer a componente común el dibujo
  foto+línea que ya usa `ShareLineImage.kt`.
- **iOS**: `FeedView.swift` (List + task de paginación), render espejo
  con el código de `TopoPhotoView`/`ShareLineImage.swift`.
- Tab nueva en `MainScreen.kt` (host keep-alive "tabs") y en el
  TabView de iOS.
- i18n ES/EN desde el primer commit (las dos apps).

## 🚦 Fases

1. **Backend en `develop`** → staging: migración + endpoints + tests
   (patrón `GetTopContributorsUseCaseTest`). Smoke con curl+token.
2. **App (rama `claude/**`)**: pestaña + lista + likes (sin comentarios
   aún). Probar APK debug (staging) en el Xiaomi.
3. **Comentarios + denuncias + push opcional** ("a X le gusta tu
   ascenso" — reutilizar `PushSender.sendToUser`, agrupado/limitado).
4. Paridad iOS + validación Rodrigo → recién entonces hablar de merge.

## ✅ Decisiones tomadas (Rodrigo, 2026-07-10)

1. **Ranking fusionado**: tercer chip RANKING dentro de la pestaña;
   la entrada "Comunidad" de Perfil se elimina.
2. **Nombre de pestaña**: **Comunidad** / Community.
3. **Sin backfill**: solo actividad desde el estreno.
4. **Eventos**: HECHO/proyecto conseguido **+ piedras/vías nuevas**
   (estas al aprobarse la contribución).
5. **Frescura**: pull-to-refresh + recarga en ON_RESUME + paginación
   hacia lo antiguo; lo nuevo siempre arriba.

6. **6 tabs OK** (Rodrigo): es un icono más en la barra, igual en
   Android e iOS que Quedadas/Radar. Verificar visualmente en el
   Xiaomi y en un iPhone pequeño cuando haya maqueta, pero no bloquea.
