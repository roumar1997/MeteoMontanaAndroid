# Cumbre — referencia (Android)

> Mapa del código, endpoints y decisiones de componentes. **No se carga en cada
> sesión**: se abre cuando vas a tocar esa zona. Las reglas normativas de
> arquitectura están en `ARCHITECTURE.md`; el porqué histórico, en `HISTORIAL.md`.

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


## 📱 Probar la app iOS en el iPhone SIN MAC (flujo oficial)

Validado el 2026-06-16. Este es el flujo de SIEMPRE para que Rodrigo pruebe
cambios de iOS en su iPhone. No hace falta Mac para nada.

**Resumen**: GitHub Actions compila el `.ipa` → se descarga al PC → un
mini-servidor web lo sirve → Safari del iPhone lo descarga → **AltStore** lo
instala. (Sideloadly NO funciona en este PC: su provisión "anisette" crashea —
access violation en `CoreADI.dll`. Por eso AltStore.)

**Requisitos ya instalados en el PC de Rodrigo** (no reinstalar):
- iTunes + iCloud (versiones web de apple.com, NO Microsoft Store).
- **AltServer** Windows (`C:\Program Files (x86)\AltServer\AltServer.exe`) →
  vive en la bandeja del sistema (icono rombo ◇). AltStore ya está instalado en
  el iPhone.
- Regla de firewall "ipa-serve 8000" para el servidor web.

**Pasos cuando hay un build verde nuevo (lo hace Claude desde el PC):**
1. Descargar el `.ipa` del último run verde:
   `gh run download <id> --name ios-app-unsigned-ipa --dir C:\Users\rouma\ipa-serve`
   (o copiarlo a `C:\Users\rouma\ipa-serve\MeteoMontana.ipa`).
2. Servir esa carpeta (aislada, no exponer iCloud entero):
   `cd C:\Users\rouma\ipa-serve; python -m http.server 8000 --bind 0.0.0.0`
   (déjalo corriendo en background).
3. Decirle a Rodrigo la URL. IP del PC: **192.168.0.12** (Ethernet, misma WiFi
   que el iPhone) o 172.20.10.2 si comparte internet por el móvil.

**Pasos que hace Rodrigo en el iPhone:**
1. Safari → `http://192.168.0.12:8000/MeteoMontana.ipa` → Descargar.
2. AltStore → pestaña **My Apps** → **`+`** → Examinar → Descargas →
   `MeteoMontana.ipa`. Pide Apple ID + **contraseña específica de app**
   (account.apple.com → Seguridad → Contraseñas de aplicaciones; por el 2FA).
3. Si "desarrollador no fiable": Ajustes → General → VPN y gestión de
   dispositivos → confiar en su Apple ID.

**Cadencia recomendada**: acumular varias features → un build → reinstalar una
vez (la app caduca a 7 días con Apple ID gratuito; reinstalar la renueva).

Detalle técnico y troubleshooting completo en la memoria
`project_ios_install_sin_mac.md`.

### CI iOS (`.github/workflows/ios-ci.yml`)
- Runner **macos-15** (Xcode 16: xcodegen genera el proyecto en formato 77).
- `xcodegen generate` → `xcodebuild -sdk iphoneos CODE_SIGNING_ALLOWED=NO` →
  empaqueta `.app` en `.ipa` sin firmar (Payload/ + zip) → artifact
  **ios-app-unsigned-ipa**. Caché de `~/.konan`. `concurrency` cancela builds
  viejos. Build ~6 min con caché.
- **El CI COMPILA el Swift** → es el feedback real sin Mac. Flujo de desarrollo
  iOS: lote de cambios → push a `main` → el build (verde/rojo) verifica.
- Secret **`GOOGLE_SERVICE_INFO_PLIST`** (ya creado) = `GoogleService-Info.plist`
  real de Firebase climbingteams (para que el login Google funcione en el `.ipa`).

---

