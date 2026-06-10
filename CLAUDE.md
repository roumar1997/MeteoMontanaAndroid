# MeteoMontana Android — contexto para Claude

App Android nativa (Kotlin + Jetpack Compose) que replica la PWA MeteoMontana
y se conecta al backend Spring Boot.

> 🎯 **Decisión 2026-06-06**: la app evolucionará a **Kotlin Multiplatform
> (KMP)** para soportar también iOS. Compartiremos `domain/` y `data/` en
> Kotlin entre Android e iOS. UI Android sigue siendo Jetpack Compose;
> UI iOS será SwiftUI consumiendo el módulo compartido.

> ## 📚 Documentos del repo
>
> - [`CLAUDE.md`](./CLAUDE.md) — este fichero. Contexto del proyecto.
> - [`KMP_MIGRATION.md`](./KMP_MIGRATION.md) — plan migración KMP + backlog iOS.
> - [`DEPLOYMENT.md`](./DEPLOYMENT.md) — guía para publicar la app (hosting
>   backend, dominios, keystore, Play Console, política de privacidad,
>   costes). **LEER ANTES DE PUBLICAR.**
> - [`DESIGN.md`](./DESIGN.md) — sistema de diseño Cumbre (tokens, espaciado,
>   tipografía) — espejo Android/iOS.

> ## ⚠️ SI ABRES UNA SESIÓN NUEVA, LEE ESTO PRIMERO
>
> 1. Abre [`KMP_MIGRATION.md`](./KMP_MIGRATION.md).
> 2. Ve directamente a la sección **📍 ESTADO ACTUAL DE LA MIGRACIÓN** del
>    principio del documento. Ahí verás:
>    - Qué fases están hechas (`[x]`) y cuáles pendientes (`[ ]`).
>    - La sub-tarea marcada con `← SIGUIENTE`.
> 3. Lee el "Próximo paso" al final del mismo documento — es la descripción
>    concreta de la tarea siguiente.
> 4. Lee el resto de este `CLAUDE.md` para entender el contexto general del
>    proyecto (stack, endpoints, convenciones).
> 5. **NO repreguntes** "¿por dónde íbamos?". Está todo escrito.
> 6. **Modelo a usar**: Sonnet para refactors mecánicos (Fases 1.x y 2.x:
>    partir interfaces, mover modelos, redirigir inyecciones, actualizar tests).
>    Opus solo si hay decisiones de arquitectura ambiguas, bugs difíciles de
>    diagnosticar, o diseño desde cero sin plan previo.
> 7. Al final de tu sesión, **actualiza el checklist** y el "Próximo paso"
>    de `KMP_MIGRATION.md` antes de commitear. Esto es lo que hace que la
>    siguiente sesión sepa por dónde seguir.
>
> Durante Fases 1 y 2 la app Android sigue funcionando igual; el usuario
> no nota cambios visuales. Cualquier feature nueva Android se puede añadir
> sobre la marcha — no congela el desarrollo.

---

## 🗺️ Mapa de repos — LEER PRIMERO

| Repo | Ruta local | GitHub |
|---|---|---|
| **Android** (este repo) | `C:\Users\rouma\MeteoMontanaAndroid` | `roumar1997/MeteoMontanaAndroid` |
| **Backend** Spring Boot | `C:\Users\rouma\MeteoMontanaAPI` | `roumar1997/MeteoMontanaAPI` |
| **PWA** JS (referencia visual) | `C:\Users\rouma\Desktop\MeteoMontana` | (no es este repo) |

**Regla de oro**: cuando algo falla en Android y parece un problema del backend
(nuevo campo, endpoint inexistente, lógica de negocio), ve a editar
`C:\Users\rouma\MeteoMontanaAPI`. Cuando algo falla visualmente o en la UI,
edita este repo. Los dos repos se trabajan juntos en la misma sesión.

---

## ⚡ Arranque rápido (cada sesión)

```powershell
# 1. Levantar Postgres (desde la raíz del backend)
cd C:\Users\rouma\MeteoMontanaAPI
docker compose up -d

# 2. Arrancar el backend
cd api
./mvnw spring-boot:run
# → escucha en http://localhost:8080
# → Flyway aplica migraciones automáticamente al arrancar

# 3. Android Studio: abrir C:\Users\rouma\MeteoMontanaAndroid
#    Sync Gradle si hay cambios en build.gradle.kts o libs.versions.toml
#    Run → instala en emulador o móvil físico
```

**Verificar que el back funciona:**
```
GET http://localhost:8080/actuator/health  →  {"status":"UP"}
GET http://localhost:8080/api/schools      →  array de 191 escuelas
```

---

## 📁 Ficheros clave — dónde tocar qué

### Android (este repo)
```
app/build.gradle.kts          → dependencias, API_BASE_URL (emulador vs móvil físico)
gradle/libs.versions.toml     → versiones de todas las deps
app/src/main/res/xml/
  network_security_config.xml → IPs permitidas para HTTP cleartext

ui/theme/
  Color.kt     → paleta Cumbre (copia exacta de tokens.css de la PWA)
  Type.kt      → fuentes Google Fonts + EyebrowTextStyle
  Spacing.kt   → escala de espaciado compartida
  Shape.kt     → radius 0/2/4dp
  Theme.kt     → MeteoMontanaTheme

data/api/
  SchoolApi.kt  → todos los endpoints de escuelas, forecast, notas, contributions
  AdminApi.kt   → endpoints admin (submissions, contributions, push, logs)
  dto/          → DTOs Moshi para cada respuesta del backend
```

### Backend (`C:\Users\rouma\MeteoMontanaAPI`)
```
api/src/main/java/com/meteomontana/api/
  domain/model/          → entidades de negocio puras (School, PendingContribution...)
  domain/port/           → interfaces de repositorio
  application/           → casos de uso (lógica de negocio)
    forecast/            → GetForecastUseCase, ForecastResponse
    contribution/        → SubmitContributionUseCase, ReviewContributionUseCase
    admin/               → AdminGuard (usa ensureAdmin(uid), NO check(user))
  infrastructure/
    persistence/jpa/     → entidades JPA + repos Spring Data
    web/                 → controllers REST (ContributionController, etc.)
    weather/             → OpenMeteoClient (URL + parsing)
    security/            → FirebaseTokenFilter, FirebaseUser(uid, email, name)

api/src/main/resources/
  db/migration/          → migraciones Flyway (V1..V12)
  serviceAccountKey.json → credenciales Firebase (excluido de git, NUNCA subir)

.env                     → contraseña Postgres (excluido de git)
docker-compose.yml       → Postgres 16 en puerto 5432
```

### PWA (referencia visual, NO tocar)
```
C:\Users\rouma\Desktop\MeteoMontana/
  css/style.css    → variables CSS: --bg, --terra, --ink, --font-sans/serif/mono...
  css/tokens.css   → clases eyebrow, score-cell, spinner, etc.
  js/utils/weather-icons.js  → wmoSvg() — origen de WmoWeatherIcon.kt
  js/widgets/hourly-heatmap.js — origen de HourlyScoreGrid.kt
  js/sectors/map-panel.js    — origen de SchoolsMapPanel.kt
```

---

## Workflow de cada sesión

**Primer mensaje (sesión nueva sin contexto)**:
1. Lee `KMP_MIGRATION.md` → sección **📍 ESTADO ACTUAL** → busca el
   `← SIGUIENTE`.
2. Lee también el "Próximo paso" al final del documento.
3. Lee este `CLAUDE.md` para contexto general.
4. Ya sabes qué tarea toca. **Empieza sin preguntar** salvo que el usuario
   pida algo distinto explícitamente.

**Antes de cerrar la sesión**:
1. **Actualiza `KMP_MIGRATION.md`**:
   - Marca con `[x]` las sub-tareas completadas en el checklist.
   - Mueve el `← SIGUIENTE` a la próxima sub-tarea.
   - Cambia la "Última actualización" al inicio de la sección.
   - Reescribe la sección "Próximo paso" al final con la próxima tarea
     concreta (no genérico — específico).
2. Actualiza **Estado actual** y **Bitácora reciente** de este CLAUDE.md si
   hubo cambios visibles para el usuario.
3. Commit + push a `main` (Android y backend si tocaste los dos).
4. Sincroniza al worktree si trabajaste fuera de él.

**Protocolo de edición** (lo que funcionó bien):
1. Antes de editar un archivo, léelo con Read para no asumir su contenido.
2. Si el cambio toca backend Y Android, empieza siempre por el backend
   (compilar back → reiniciar → luego tocar Android).
3. Cuando el usuario pega un error de compilación, lee la línea exacta
   antes de proponer el fix.
4. Aplica los cambios directamente (Edit/Write) — el usuario aprecia no
   tener que copiar snippets manualmente.
5. Al terminar un bloque de cambios, haz commit + push de ambos repos.

## Cómo trabaja el usuario

Junior developer aprendiendo. Quiere entender cada línea. Reglas:

1. **Idioma: español.** Código en **inglés**.
2. **Paso a paso.** Una cosa a la vez. Esperas confirmación antes del siguiente.
3. **Verifica antes de proponer.** Lee el código existente antes de tocar algo.
4. **Trade-offs explícitos** en decisiones de diseño.

## Stack

- **Lenguaje**: Kotlin, **UI**: Jetpack Compose, **Nav**: Navigation Compose
- **Red**: Retrofit + OkHttp + Moshi, **DI**: Hilt, **Async**: Coroutines + Flow
- **Imagen**: Coil, **Mapas**: MapLibre Android SDK
- **Auth/Push/Chat**: Firebase (Auth, FCM, Firestore para chat)
- **Min SDK**: API 26 (Android 8.0)

## Conexión con el backend

Base URL configurada en `app/build.gradle.kts`, bloque `debug {}`:

```kotlin
// Emulador Android Studio (VM interna del PC):
buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8080/api/\"")

// Móvil físico en la misma red WiFi/Ethernet que el PC:
buildConfigField("String", "API_BASE_URL", "\"http://192.168.0.12:8080/api/\"")
```

**IP del PC de Rodrigo**: `192.168.0.12` (Ethernet, `ipconfig` lo confirma).
Cambia entre uno y otro, haz **Sync Gradle → Run**.

**Cleartext HTTP** permitido en `app/src/main/res/xml/network_security_config.xml`.
Si añades una IP nueva, añádela también ahí o Android 9+ la bloquea.

Firebase project: **climbingteams** (Auth provider: Google).
Config en `google-services.json` (excluido de git).

## Diseño visual — tema Cumbre

Sistema Cumbre: papel, tinta, terracota. Sin gradientes, sin blur, radius 0/2/4dp.
Tokens en `ui/theme/`: `Color.kt`, `Type.kt` (Google Fonts: Inter / Source Serif 4 /
JetBrains Mono), `Shape.kt`, `Spacing.kt`, `Theme.kt`.

Reglas:
- **Sin elevation shadows** — bordes `1.dp` color `Rule`
- **Cards**: fondo `Paper`, borde `Rule` 1dp, radius `2dp`
- **Botón primario**: fondo `Terra`, texto blanco
- **Eyebrow** (headers tipo "DISTANCIA", "VER MAPA"): usar `EyebrowTextStyle`
  (Mono 10sp Bold tracking 1.8sp). **No usar `labelMedium` para esto** — su
  tracking 0sp es para dígitos (km/h, horas).
- Doc completo: `DESIGN.md` en la raíz.

## Endpoints del backend (todos disponibles)

**Públicos:**
| Endpoint | Descripción |
|---|---|
| `GET /api/schools[?region&style&rockType&lat&lon&radioKm]` | Catálogo con filtros |
| `GET /api/schools/{id}` | Detalle |
| `GET /api/schools/{id}/notes` | Notas comunitarias |
| `GET /api/schools/{id}/photos` | Fotos (URLs firmadas 60min) |
| `GET /api/schools/{id}/forecast` | Tiempo + score por hora (cache back) |
| `GET /api/schools/{id}/blocks` | Bloques/parkings/zonas del mapa |
| `GET /api/users/{uid o username}` | Perfil público |
| `GET /actuator/health` | Healthcheck |

**Auth (Bearer Firebase token):**
| Endpoint | Descripción |
|---|---|
| `GET/PUT /api/me` | Perfil privado (JIT provisioning en primer login) |
| `PUT /api/me/fcm-token` | Token FCM para push |
| `POST /api/schools/{id}/photos` | Subir foto (multipart, max 5MB) |
| `DELETE /api/photos/{photoId}` | Borrar foto propia |
| `POST /api/submissions` | Proponer escuela nueva |
| `GET /api/submissions/me` | Mis propuestas de escuela |
| `POST /api/schools/{id}/contributions` | Propuesta de mejora (PARKING/BOULDER/SECTOR/POSITION_CORRECTION) |
| `GET /api/contributions/me` | Mis propuestas de mejora |
| `POST /api/schools/{id}/notes` | Crear nota |
| `POST /api/journal` | Nueva entrada de diario |
| `GET /api/journal/me` | Mi diario |

**Admin (`is_admin=true` en BD):**
| Endpoint | Descripción |
|---|---|
| `GET /api/admin/submissions` | Cola de escuelas nuevas pending |
| `POST /api/admin/submissions/{id}/approve\|reject` | Revisar escuela nueva |
| `GET /api/admin/contributions` | Cola de mejoras pending |
| `POST /api/admin/contributions/{id}/approve` | Aprueba → materializa en mapa |
| `POST /api/admin/contributions/{id}/reject` | Rechaza |
| `GET /api/admin/logs` | Auditoría |
| `POST /api/admin/push` | Push manual |

**Materialización al aprobar contribuciones:**
- `PARKING` → crea `school_block` tipo `PARKING` (aparece en mapa escuela)
- `BOULDER` → crea `school_block` tipo `BLOCK`
- `SECTOR`  → crea `school_block` tipo `ZONE`
- `POSITION_CORRECTION` + `targetBlockId` → mueve ese bloque a `proposedLat/Lon`
- `POSITION_CORRECTION` + `targetBlockId=null` → mueve la escuela entera

**Queda en Firebase (decisión consciente):**
- Auth SDK, Storage (fotos), FCM (push), Firestore (chat 1-a-1 realtime)

## Arquitectura de la app

```
app/src/main/java/com/meteomontana/android/
  data/api/          — interfaces Retrofit + DTOs (SchoolApi, AdminApi)
  data/repository/   — repositorios
  domain/model/      — modelos puros (Fase 1.2 KMP completa):
    School, SchoolScore, Note, Forecast (+ Current, HourForecast,
    DayForecast, BestDay, OptimalWindow, ScoreFactor), Block, BlockLine,
    Contribution, Submission, AdminStats, AdminLog, AdminPushResult,
    PrivateProfile, PublicProfile, FollowStatus, Notification, Inbox,
    FavoriteSchool, FavoritesGrid, FavoriteRow, DayCell, JournalSession,
    JournalStats, SchoolStats.
    UiStates y pantallas usan estos modelos, NO los *Dto. Mapping via
    extension functions en data/api/dto/*Mapping.kt (toDomain()).
    Solo *Request siguen siendo DTOs (Moshi → Kotlinx Serialization en
    Fase 2).
  domain/usecase/    — casos de uso (Fase 1.1 KMP completa):
    schools/         — GetSchools, GetSchoolById, GetTodayScores
    forecast/        — GetForecast
    blocks/          — GetBlocks, CreateBlock, UpdateBlock, DeleteBlock
    contributions/   — SubmitContribution
    notes/           — GetNotes, CreateNote
    favorites/       — GetMyFavorites, AddFavorite, RemoveFavorite
    notifications/   — GetMyNotifications
    profile/         — GetMyProfile
    admin/           — GetAdminStats, GetPendingSubmissions,
                       GetPendingContributions, GetAdminLogs,
                       ApproveSubmission, RejectSubmission,
                       ApproveContribution, RejectContribution, SendPush
    (los 3 VMs dependen solo de use cases para sus llamadas core; solo
    AdminViewModel mantiene SchoolApi para getSchools() del tab GESTIONAR)
  ui/
    screens/
      schools/       — SchoolListScreen + SchoolListViewModel + SchoolFiltersBar
                       + SchoolsMapPanel (mapa desplegable con filtros)
      detail/        — SchoolDetailScreen + VM + ProposeContributionFlow
      admin/         — AdminScreen + AdminViewModel (contribuciones + submissions)
      weather/       — WeatherScreen
      profile/       — ProfileScreen, EditProfileScreen, JournalEntriesScreen
      users/         — PublicProfileScreen, SearchUsersScreen, FollowListScreen
      chat/          — ChatListScreen, ChatScreen
      login/         — LoginScreen
      notifications/ — NotificationsScreen
      submissions/   — SubmitSchoolScreen, MySubmissionsScreen
      radar/         — RadarScreen
      topo/          — TopoEditorScreen
    components/
      SchoolListItem.kt      — item de la lista de escuelas
      SchoolMap.kt           — mapa colapsable de escuela con "+ PROPONER"
      HourlyScoreGrid.kt     — grid "Próximas 16h" con WmoWeatherIcon
      WmoWeatherIcon.kt      — iconos SVG meteo por código WMO (= PWA)
      FullScreenMapDialog.kt — mapa a pantalla completa (admin "VER EN MAPA")
      BlocksSection.kt       — sección de bloques en detalle escuela
      NotesSection.kt        — sección de notas
      ForecastBody.kt        — hero score + ventana óptima + heatmap
    theme/
      Color.kt       — paleta Cumbre light+dark
      Type.kt        — Google Fonts (Inter/SourceSerif4/JetBrainsMono) + EyebrowTextStyle
      Shape.kt       — radius 0/2/4dp
      Spacing.kt     — escala xs/sm/md/lg/xl/xxl/xxxl
      Theme.kt       — MeteoMontanaTheme
  di/                — módulos Hilt (NetworkModule, etc.)
```

## Componentes clave — decisiones de diseño

### SchoolMap.kt
Mapa colapsable en detalle de escuela. Markers por tipo:
- `PARKING` → cuadrado azul con "P" blanca. Al tocar: popup con nombre,
  descripción, coords y botón **"CÓMO LLEGAR"** → Google Maps `dir/?api=1&destination=lat,lon`
- `BLOCK` → pin terra con "B"
- `ZONE` → pin verde con "Z"

Botón **"+ PROPONER"** (esquina inferior derecha, fondo Terra) abre
`ProposeContributionFlow`.

Problema histórico: `MapView` dentro de `LazyColumn` roba gestos. Fix:
`setOnTouchListener { v, event → v.parent?.requestDisallowInterceptTouchEvent(...) }`

### ProposeContributionFlow.kt
Flujo de propuesta de mejora. Pasos:
1. `TypePickerDialog` — elige PARKING / BOULDER (próx) / SECTOR (próx) / CORREGIR (próx)
2. Banner "PULSA EN EL MAPA" — el usuario toca el mapa para fijar coords
3. `ParkingFormDialog` — nombre (opt), coords auto, notas (opt)
4. `POST /api/schools/{id}/contributions` con `targetBlockId=null`
5. `SuccessDialog` — "PROPUESTA ENVIADA · 24-48h" + botones CERRAR / VER MIS PROPUESTAS

### WmoWeatherIcon.kt
SVG line-art por código WMO (mismo que `wmoSvg()` en `js/utils/weather-icons.js`).
Usa `DrawScope.scale()` para escalar el viewport 24×24. Sin emojis.
`HourlyScoreGrid.kt` lo usa para el grid de "Próximas 16h".
El backend expone `weatherCode` en `HourForecastDto` (campo añadido en esta sesión).

### SchoolsMapPanel.kt
Mapa desplegable en la lista de escuelas (antes de los filtros). Markers de escuelas
con pin diamante coloreado por score. Tap → popup con nombre, score, tags, y botones
"CÓMO LLEGAR" (Google Maps) y "VER DETALLE ▸" (navega a SchoolDetailScreen).
Se sincroniza con los filtros activos.

### AdminScreen.kt
Tab "PROPUESTAS" tiene dos secciones:
- Escuelas nuevas (`SubmissionDto` de `GET /api/admin/submissions`)
- Mejoras de escuelas (`ContributionDto` de `GET /api/admin/contributions`)

Filtros de chips: TODAS / PIEDRAS / SECTORES / PARKINGS / MOVER ESCUELA.
Agrupado por escuela con badge de cantidad.
Cada card: badge tipo, nombre, notas, coords, autor, mini-mapa estático (160dp),
botones VER EN MAPA (abre `FullScreenMapDialog`) / RECHAZAR / APROBAR.

### FullScreenMapDialog.kt
Dialog a pantalla completa con MapLibre (tiles topográficos OpenTopoMap).
Usado en Admin para ver dónde está una propuesta. "✕ CERRAR" en esquina superior.

## Bitácora reciente

### Gestión completa de bloques desde el admin (esta sesión)

- **Mapa de bloque mejorado** — `pinBitmapBoulder` dibuja un polígono irregular
  (forma de roca) con el **nombre del bloque** dentro. Parking sigue siendo
  círculo azul "P", zona círculo verde "Z". Iconos compartidos entre el mapa
  público del usuario y el admin.
- **`BlockDetailDialog`** (nuevo, `ui/components/`): dialog único compartido
  entre usuario y admin. Al tocar cualquier marker muestra:
  - Badge tipo (PARKING/ZONA/PIEDRA o PROPUESTA)
  - Foto + líneas dibujadas (vía `TopoPhotoCanvas` compartido)
  - Lista de vías (número con color por grado, grado, tipo, nombre)
  - Botón **"→ CÓMO LLEGAR"** → Intent a Google Maps con destino
  - **"+ AÑADIR VÍAS"** (solo BLOCK, todos los usuarios): abre `AddLinesFlow`
  - **"✎ EDITAR"** y **"🗑 BORRAR"** (solo admin desde el panel admin)
- **`AddLinesFlow.kt`** (nuevo): el usuario añade vías a una piedra existente
  sin tener que crear una nueva. Reusa la foto del bloque y abre
  `ContributionTopoDialog` precargado. Genera una contribución BOULDER con
  `targetBlockId = block.id`.
- **Backend — añadir vías a un block existente**: al aprobar contribución
  BOULDER con `targetBlockId != null`, `ReviewContributionUseCase` busca el
  bloque y **añade** las nuevas líneas (sortOrder continuo) en lugar de crear
  una piedra nueva.
- **Materialización de líneas al aprobar**: `parseAndAttachLines` parsea
  `bloquesJson` con Jackson y crea las filas en `block_lines` con la foto.
  Maneja la conversión PIE↔STAND, LANCE↔JUMP.
- **`EditBlockDialog.kt`** (nuevo): edición admin de cualquier bloque. Permite
  cambiar nombre, descripción, coordenadas (manual o pegando texto Google
  Maps tipo `40.4168, -3.7038`), y para BLOCK también editar las líneas
  reusando `ContributionTopoDialog` precargado. Botón **"📍 MOVER PULSANDO EN
  EL MAPA"** vuelve al mapa con banner Terra "PULSA LA NUEVA POSICIÓN" y al
  tap actualiza coords con un PUT.
- **Backend — admin puede editar/borrar bloques ajenos**: tanto
  `SchoolBlockUseCase.update()` como `.delete()` aceptan ahora **creador OR
  admin** (`userRepository.findByUid(uid).map(u -> u.isAdmin())`). Borrar cae
  en cascada a `block_lines` por FK.
- **Tab "GESTIONAR" en AdminScreen** (nuevo): SearchBar de escuelas por nombre,
  lugar o región. Al elegir una abre el `FullScreenMapDialog` con todos sus
  blocks; el admin puede tocar cualquiera y borrar/editar/mover.
- **Pegar coordenadas Google Maps**: campo "PEGAR COORDENADAS (GOOGLE MAPS)"
  después de TIPO DE ROCA en `SubmitSchoolScreen` y dentro de
  `EditBlockDialog`. Parser tolerante (`-?\d+[\.,]?\d*`) que rellena lat/lon
  automáticamente al detectar 2 números válidos.
- **Markers click handler en `FullScreenMapDialog`**: `setOnMarkerClickListener`
  abre `BlockDetailDialog` en vez del callout default de MapLibre. Cada
  marker se asocia a su `BlockDto` via un `Map<Marker, BlockDto>` interno.
- **Distinción visual de propuesta vs existentes**: el marker de la propuesta
  (admin) es amarillo `#F59E0B` con ★ o nombre, los blocks existentes con su
  color por tipo. La propuesta ALSO funciona como block-fantasma para que el
  admin la abra con el mismo dialog.
- **`TopoPhotoCanvas.kt`** (nuevo, `ui/components/`): canvas compartido para
  dibujar foto + líneas con badge numérico + badge de tipo de inicio. Acepta
  tanto `parseBloquesJson(json)` (formato propuesta) como `lines.toTopoLines()`
  (lista de BlockLineDto del backend).
- **`ContributionResponse` y `ContributionDto` extendidos**: exponen ahora
  `targetBlockId` para que el admin distinga propuestas "añadir vías".
- **Admin ve foto + líneas existentes + nuevas al revisar "añadir vías"**:
  `ContributionCard` detecta `targetBlockId != null`, busca el bloque destino
  en `existingBlocks` cargados via LaunchedEffect, y dibuja existentes +
  nuevas líneas superpuestas en la misma foto.
- **`AdminViewModel.loadAllSchools / updateBlock / deleteBlock`**: cache
  `schoolBlocks: Map<String, List<BlockDto>>` para que el tab GESTIONAR no
  recargue cada vez.
- **Aspect ratio 4:3 + Crop fijos** en `TopoPhotoCanvas`/editor: las
  coordenadas normalizadas mapean al mismo rectángulo en todos los sitios →
  las líneas que dibuja el usuario coinciden exactamente con lo que ve el
  admin y con lo que verán otros usuarios.
- **Fix iconos LANCE no visibles tras edición**: `drawStartIcon` y los chips
  de selección ahora aceptan tanto los valores de app (PIE/SIT/LANCE/TRAV)
  como los del backend (STAND/SIT/JUMP/TRAV).

### Sesión anterior

- **Flujo BOULDER completo**: TypePickerDialog activa "AÑADIR PIEDRA" → tap en mapa →
  `BoulderFormDialog` (nombre, coordenadas auto, lista de bloques con grado+tipo de inicio,
  foto picker, botón "DIBUJAR LÍNEAS") → `ContributionTopoDialog` (canvas drag sobre foto,
  colores por grado via `gradeStyle()`, iconos PIE/SIT/LANCE/TRAV, DESHACER, GUARDAR LÍNEAS)
  → ENVIAR PROPUESTA (sube foto a Firebase Storage → POST backend).
- **Backend V13**: migración añade `photo_url`, `bloques_json`, `topo_lines_json` a
  `pending_contributions`. `PendingContribution`, `PendingContributionJpaEntity`,
  `ContributionRequest`, `ContributionResponse`, `SubmitContributionUseCase`,
  `ReviewContributionUseCase` actualizados.
- **AdminScreen BOULDER**: muestra foto (180dp crop) + lista de bloques con dot de color
  por grado, grado, tipo de inicio y nombre.
- **Firebase Storage**: `StorageUploadHelper.kt` inyectado en `SchoolDetailViewModel`.
  Provider añadido en `NetworkModule.kt`. Ya estaba en `build.gradle.kts`.
- **Decisión arquitectura offline**: Room para caché offline (no Firestore para datos de
  escuelas). Foto en Firebase Storage, cacheable por Coil sin código extra.

**Estado actual tras esta sesión:**

✅ Flujo "Nueva Piedra" (BOULDER) completo end-to-end
✅ Editor de líneas con drag sobre foto, colores por grado, iconos de tipo de inicio  
✅ Admin ve foto + lista de bloques en la card de contribución BOULDER
✅ Al aprobar BOULDER → `school_block` tipo BLOCK con `photoUrl` materializado


- **Paso 0 (tipografía)**: Google Fonts provider (Inter/SourceSerif4/JetBrainsMono)
  cargado via `ui-text-google-fonts`. `Spacing.kt` con escala compartida.
  `EyebrowTextStyle` separado de `labelMedium` (tracking ancho partía dígitos).
  `DESIGN.md` creado como spec espejo Android↔iOS.

- **Fix MapLibre crash**: `MapLibre.getInstance(this)` en `MeteoMontanaApp.onCreate()`
  — sin esto el `MapView` crashea al inflarse.

- **SchoolListItem**: score badge con fondo paper claro (no pintado con scoreColor),
  nombre en Source Serif 4, "MOJADA" en lugar de "HÚMEDA", heatmap 12dp.

- **SchoolListScreen**: header PWA (título + count + botón outlined "+ Enviar escuela"),
  banner "¿Te ayuda la app? Apóyanos" (fondo TerraBg), toggle "VER MAPA",
  iconos chat/notif/perfil en fila separada arriba.

- **SchoolsMapPanel**: mapa desplegable en lista de escuelas. Markers diamante
  coloreados por score. Popup con "Cómo llegar" + "Ver detalle". Sincronizado
  con filtros del VM.

- **WmoWeatherIcon + weatherCode**: iconos SVG por código WMO (no emojis).
  Backend actualizado: `OpenMeteoClient` pide `weather_code`, `ForecastResponse`
  y `HourForecastDto` exponen `weatherCode`.

- **ProposeContributionFlow**: flujo completo PARKING. TypePickerDialog →
  banner tap en mapa → ParkingFormDialog (coords auto) → SuccessDialog.
  Backend: tabla `pending_contributions` (V11), endpoints
  `POST /api/schools/{id}/contributions` y admin review.

- **Materialización al aprobar**: `ReviewContributionUseCase` crea `school_block`
  (PARKING/BLOCK/ZONE) o mueve coords al aprobar. V12 añade `target_block_id`.

- **Markers con icono por tipo**: PARKING=cuadrado azul "P", BLOCK=pin terra "B",
  ZONE=pin verde "Z". Popup al tocar con info + "CÓMO LLEGAR" para parkings.

- **AdminScreen reescrito**: filtros de contribuciones, agrupación por escuela,
  mini-mapa en cada card, "VER EN MAPA" abre `FullScreenMapDialog` interactivo.

- **Fix scroll mapa**: `requestDisallowInterceptTouchEvent` en `setOnTouchListener`
  de todos los `MapView` para que el padre (LazyColumn) no robe gestos de pan.

- **Conexión móvil físico**: IP del PC `192.168.0.12`. Config en `build.gradle.kts`
  + `network_security_config.xml` (cleartext para IP local).

## Estado actual

**La app está funcional con las siguientes características operativas:**

✅ Lista de escuelas con filtros + mapa desplegable sincronizado con filtros  
✅ Score heatmap con iconos SVG meteo (no emojis)  
✅ Detalle de escuela: forecast hero, ventana óptima, heatmap 16h, notas, mapa  
✅ Mapa de escuela con markers diferenciados: parking=azul "P", piedra=polígono terra con nombre, zona=verde "Z"  
✅ Tocar marker → BlockDetailDialog: foto + líneas dibujadas + lista de vías + CÓMO LLEGAR  
✅ Botón "+ AÑADIR VÍAS" a piedras existentes (usuario y admin) via AddLinesFlow  
✅ Flujo "+ PROPONER" para parkings (tap en mapa → form → backend)  
✅ Flujo "+ PROPONER PIEDRA" (BOULDER): nombre + bloques (grado+tipo) + foto + editor topo  
✅ Editor topo: drag sobre foto, colores por grado, badges PIE/SIT/LAN/TRV en extremo  
✅ Admin tab PROPUESTAS: cola con filtros, agrupación por escuela, mini-mapa, VER EN MAPA  
✅ Admin tab GESTIONAR: search escuela → mapa con todos los blocks → BORRAR/EDITAR/MOVER  
✅ EditBlockDialog: nombre, coords (manual o pegando Google Maps), líneas, mover en mapa  
✅ Materialización al aprobar BOULDER: photo_url + bloques_json → school_block + block_lines  
✅ Tema Cumbre con fuentes reales Google Fonts  
✅ Funciona en móvil físico (IP local) y emulador  

**Pendiente (próximas sesiones):**
- Flujo SECTOR, CORREGIR POSICIÓN en `ProposeContributionFlow`
- `GET /api/users/search` — búsqueda de usuarios
- Follows (tabla + endpoints + UI)
- Diario personal en Android (backend ya tiene endpoints)
- TTL caché forecast (Caffeine `expireAfterWrite=30m` en back)
- `POST /api/me/photo` — foto de perfil

**Offline futuro — decisión tomada (2026-06-05):**
- Fotos → Firebase Storage (ya integrado, URLs se cachean con Coil automáticamente)
- Datos de escuelas/bloques offline → **Room** (cola local que sincroniza al volver la conexión)
- NO migrar contribuciones a Firestore: el admin usa Spring Boot, mantener consistencia
- Room se añade en una sesión futura cuando se aborde el modo offline

**Notas operativas:**
- Arranque back: `docker compose up -d` + `./mvnw spring-boot:run` en `MeteoMontanaAPI/api/`
- Migraciones Flyway hasta V12 aplicadas
- `serviceAccountKey.json` en `api/src/main/resources/` — excluido de git
- 191 escuelas + notas + bloques en Postgres
- Spring Security activo: GET `/api/schools/**`, `/api/users/**`, `/actuator/health` son públicos
