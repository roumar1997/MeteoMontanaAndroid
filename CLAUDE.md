# MeteoMontana Android — contexto para Claude

App Android nativa (Kotlin + Jetpack Compose) que replica la PWA MeteoMontana
y se conecta al backend Spring Boot en `C:\Users\rouma\MeteoMontanaAPI`.

## Workflow de cada sesión

**Primer mensaje**: el usuario dirá *"léete CLAUDE.md y sigamos por donde lo
dejamos"*. Lees este archivo, miras **Estado actual** al final, y arrancas
desde ahí sin repetir el plan entero.

**Antes de cerrar**: actualiza **Estado actual** y **Bitácora reciente**.

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
  domain/model/      — modelos puros (School, ...)
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
✅ Mapa de escuela con markers por tipo (parking=azul, bloque=terra, zona=verde)  
✅ Popup al tocar marker con info + "Cómo llegar" para parkings  
✅ Flujo "+ PROPONER" para parkings (tap en mapa → form → backend)  
✅ Admin: cola de contribuciones con filtros, agrupación, mini-mapa, VER EN MAPA interactivo  
✅ Al aprobar contribución → aparece en mapa de escuela inmediatamente  
✅ Tema Cumbre con fuentes reales Google Fonts  
✅ Funciona en móvil físico (IP local) y emulador  

**Pendiente (próximas sesiones):**
- Flujo BOULDER, SECTOR, CORREGIR POSICIÓN en `ProposeContributionFlow`
  (TypePickerDialog ya los lista como "próximamente")
- `GET /api/users/search` — búsqueda de usuarios
- Follows (tabla + endpoints + UI)
- Diario personal en Android (backend ya tiene endpoints)
- TTL caché forecast (Caffeine `expireAfterWrite=30m` en back)
- `POST /api/me/photo` — foto de perfil

**Notas operativas:**
- Arranque back: `docker compose up -d` + `./mvnw spring-boot:run` en `MeteoMontanaAPI/api/`
- Migraciones Flyway hasta V12 aplicadas
- `serviceAccountKey.json` en `api/src/main/resources/` — excluido de git
- 191 escuelas + notas + bloques en Postgres
- Spring Security activo: GET `/api/schools/**`, `/api/users/**`, `/actuator/health` son públicos
