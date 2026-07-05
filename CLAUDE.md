# MeteoMontana Android — contexto para Claude

App Android nativa (Kotlin + Jetpack Compose) que replica la PWA MeteoMontana
y se conecta al backend Spring Boot.

> 🎯 **KMP completado**: la app comparte `domain/`+`data/` en Kotlin entre
> Android (Compose) e iOS (SwiftUI). **Paridad exigida**: iOS debe replicar
> Android verbatim (mismas pantallas, textos, orden, colores). Ver
> `KMP_MIGRATION.md` solo si hace falta el patrón de bridge para un puerto
> `suspend` nuevo (Auth/Location/Chat/BLE...) — el resto del doc es histórico.

> ## 📚 Documentos del repo
>
> - [`CLAUDE.md`](./CLAUDE.md) — este fichero. Contexto del proyecto.
> - [`KMP_MIGRATION.md`](./KMP_MIGRATION.md) — migración KMP **completada**;
>   queda como referencia técnica del patrón bridge Kotlin↔Swift.
> - [`DEPLOYMENT.md`](./DEPLOYMENT.md) — publicación (Railway, Firebase, Play,
>   App Store, keystore). **LEER ANTES DE PUBLICAR.**
> - [`DESIGN.md`](./DESIGN.md) — sistema de diseño Cumbre (tokens, espaciado,
>   tipografía) — espejo Android/iOS.
> - [`WALLS_DESIGN.md`](./WALLS_DESIGN.md) — diseño de **muros largos**,
>   **implementado**. Consultar antes de tocar geometría de vías/muros.
> - [`MEETUPS_DESIGN.md`](./MEETUPS_DESIGN.md) — diseño de **Quedadas**,
>   **implementado**. Consultar antes de tocar esa pestaña.
> - [`APP_STORE_CHECKLIST.md`](./APP_STORE_CHECKLIST.md) — checklist de
>   publicación en App Store (la app ya está en TestFlight; quedan pasos
>   de ficha).

> ## ⚠️ SI ABRES UNA SESIÓN NUEVA
>
> 1. Lee la sección **Estado actual** y **🔜 Pendiente** más abajo en este
>    mismo fichero.
> 2. Si la tarea toca algo del **historial resumido**, mira si hace falta más
>    detalle en `git log` del área concreta — no está todo repetido aquí.
> 3. Al terminar tu sesión, añade una línea al historial (no un bloque largo)
>    y actualiza "🔜 Pendiente".

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

## 🌐 Workflow GitHub-only (desde 2026-06-12)

Rodrigo trabaja desde **sesiones web de Claude Code** (claude.ai/code), no
en local. Reglas para la sesión:

1. **Crear la sesión con LOS DOS repos** (Android + API) si la tarea toca
   backend. Si solo dieron uno, decirlo al principio, no a mitad de tarea.
2. La sesión desarrolla en su rama `claude/**`. Al terminar: **CI en verde
   → merge a `main`** (Rodrigo lo aprueba diciendo "mergea a main").
   Los .md actualizados deben llegar a `main`, si no la siguiente sesión
   no los ve.
3. **CI** (`.github/workflows/android-ci.yml`): compila + tests en cada
   push y deja el **APK debug como artifact** (pestaña Actions → run →
   Artifacts → `app-debug-apk`). Rodrigo lo descarga e instala en el móvil
   sin Android Studio.
4. Para que ese APK tenga Firebase funcional (login Google, push) hacen
   falta DOS secrets en el repo (Settings → Secrets and variables →
   Actions):
   - **`GOOGLE_SERVICES_JSON`** con el contenido del `google-services.json`
     real. Sin él, el APK compila pero usa config dummy.
   - **`DEBUG_KEYSTORE_BASE64`** (⚠️ PENDIENTE de crear) con el
     `~/.android/debug.keystore` en base64
     (`[Convert]::ToBase64String([IO.File]::ReadAllBytes("$env:USERPROFILE\.android\debug.keystore"))`).
     Sin él, el runner firma cada build con un keystore aleatorio → el
     SHA-1 no coincide con Firebase → Google Sign-In falla con
     `DEVELOPER_ERROR (10)`. El step "Restore debug keystore" del workflow
     lo restaura si existe; si no, se salta (CI sigue verde).
     **Solo necesario si instalas el APK de Actions**; los APK compilados
     en local ya van firmados con el keystore de tu PC (login OK).
     La huella de ese keystore debe estar registrada en Firebase Console.
5. El backend en prod corre en **Railway** apuntando a `main` — verificar
   antes de mergear cambios de API que rompan compatibilidad con APKs ya
   instalados.
6. El "Arranque rápido" local de abajo sigue siendo válido si algún día se
   vuelve a trabajar en el PC.

---

## 🟢🟡 Entornos: STAGING vs PRODUCCIÓN (desde 2026-06-22) — LEER

Hay **testers reales** en la prueba cerrada de Play. Para desarrollar sin
romperles la app montamos un entorno **staging** aislado. **Regla nº1: pedir
OK antes de cualquier commit/merge, MUY especialmente en el backend.**

**Dos entornos de backend en Railway** (proyecto `zoological-wisdom`), cada uno
con **su propia base de datos** (aisladas — verificado):

| Entorno | Rama backend | URL | BD | Quién la usa |
|---|---|---|---|---|
| **production** | `main` | `api.climbingteams.com` | datos reales (191 escuelas) | **testers de Play en vivo** |
| **staging** | `develop` | `meteomontanaapi-staging.up.railway.app` | copia del catálogo (sin datos personales) | tú, para desarrollar |

**Split de las apps** (este repo) — NO es por rama, es por **tipo de build**:

| Build | Backend | Quién |
|---|---|---|
| **debug** (APK/`.ipa` de GitHub Actions) | **staging** | tú, desarrollar |
| **release** (AAB Play / `.ipa` App Store) | **producción** | testers / público |

- Android: `app/build.gradle.kts` (bloques `debug{}` / `release{}`).
- iOS: `iosApp/iosApp/DI/AppDependencies.swift` (`#if DEBUG`) + `project.yml`
  (config `Debug` define la condición `DEBUG`).

**Flujos:**
- **App** (Android/iOS): trabajas en `main`/`claude/**` de ESTE repo. El split lo
  hace el build. Release sigue en prod → tocar esto no afecta a los testers.
- **Backend** (`MeteoMontanaAPI`): push a **`develop`** → se prueba en staging →
  mergear a **`main`** SOLO tras validar (Railway redespliega prod = testers).

Firebase (auth/fotos/chat) sigue **compartido** entre staging y prod; lo aislado
es backend + BD. Sembrar/copiar staging: `psql` 16 nativo
(`C:\Program Files\PostgreSQL\16\bin`) + `\copy` CSV con
`SET session_replication_role=replica` (por el FK auto-ref de
`school_blocks.sector_block_id`). Docker Desktop no arranca en este PC.

---

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
1. Lee `KMP_MIGRATION.md` → sección **📍 ESTADO ACTUAL** y su bloque
   **"Qué hacer en una sesión nueva"**: te dice el estado y el siguiente paso.
2. Lee este `CLAUDE.md` para contexto general (stack, endpoints, convenciones).
3. **Empieza sin preguntar** salvo que el usuario pida algo distinto.

**Flujo de CADA cambio** (detalle en `KMP_MIGRATION.md` → "Cómo se trabaja"):
1. **Compila + tests** — `JAVA_HOME` al JBR de Android Studio (Java 21):
   `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug`.
   **Tests verdes antes de commit, SIEMPRE.** Si un test se rompe, se arregla
   primero; no se mergea en rojo. Tocar `shared/commonMain` recompila con
   SKIE (1ª vez ~40 min; luego incremental).
2. **Instala** en el móvil para ver el cambio:
   `adb install -r app\build\outputs\apk\debug\app-debug.apk` (adb en
   `%LOCALAPPDATA%\Android\Sdk\platform-tools`). Si tocaste el widget, quítalo
   y re-añádelo en el launcher.
3. **Commit + push a `main`** (backend también si lo tocaste; Railway
   redespliega solo). Mensaje descriptivo.
4. **Actualiza** `📍 ESTADO ACTUAL` + `Próximo paso` de `KMP_MIGRATION.md` y la
   **Bitácora** de este `CLAUDE.md`.

**Protocolo de edición**:
- Antes de editar un archivo, léelo (Read) — no asumas su contenido.
- Si tocas backend Y Android, empieza por el backend.
- Aplica los cambios directamente (Edit/Write), no pegues snippets a mano.
- **NO edites ficheros Gradle mientras hay un build corriendo** (desincroniza
  el catálogo de versiones → build roto).

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

## Estado actual

App Android + iOS a paridad completa en producción, con testers reales en
Play (prueba cerrada) y TestFlight. Backend Spring Boot en Railway
(producción + staging separados, ver sección Entornos arriba). Features
completas: catálogo de escuelas + forecast + mapa + score, piedras/muros con
vías y modalidad Bloque/Vía, contribuciones con revisión admin, diario
personal, perfiles + follows + chat (1-a-1 y grupos), notificaciones push,
favoritas + widget, modo offline (SQLDelight + outbox de sincronización),
Quedadas (3ª pestaña: chat de grupo + privacidad + caducidad + material),
i18n ES/EN completo, alerta de tiempo/quedadas configurable, ayuda/onboarding
contextual, comparador de escuelas, admin completo (propuestas, gestión de
bloques, stats, logs, push manual).

**🔜 Pendiente / acciones de Rodrigo (no bloqueante):**
- App Store: capturas de pantalla (mínimo 3, iPhone 6,5") + enviar a
  revisión con el build actual. Ver `APP_STORE_CHECKLIST.md`.
- Play Store: revisar progreso de la prueba cerrada (12 testers / 14 días).
- Seguridad (menor, ver revisión 2026-07-02): límite de tamaño a
  `gearJson` de quedadas, magic bytes en fotos, rotar contraseñas BD Railway.
- APNs (push iOS con app cerrada) — capability lista, falta activarla junto
  con la revisión de App Store.

## Historial (resumido)

Registro terso por sesión — el detalle línea a línea vive en `git log`. Solo
se apunta lo que no es obvio por el código: decisiones, causas raíz de bugs
difíciles, y qué se dejó a medias.

**2026-07-05 (3)** — Release **2.10.5** (vc37, build 49, EL de las tiendas — el envío con build 48 se canceló para incluirlo): botón UNIRME de quedadas FIJO abajo sin scroll (Android: anclado bajo la LazyColumn; iOS: safeAreaInset), mismo trato para AFORO COMPLETO / No Mixto; organizador/salir siguen inline.

**2026-07-05 (moderación)** — Release **2.11.0** (vc39, build 51) + backend V51/V52. Consola de moderación de admin: denuncia de quedada con **ELIMINAR QUEDADA** (resolve action delete) + VER AUTOR; ficha de usuario con contador de denuncias, **AVISO / SUSPENDER 7-30d / BANEAR** (login reversible, deshabilita en Firebase Auth) + **suspensión** que bloquea crear contenido (UserModerationService.ensureCanPost en CreateNote/CreateMeetup/LineComment). **Registro auditable con MOTIVO** (V52 moderation_actions): cada acción y cada borrado de nota/comentario guarda motivo + snapshot para justificar/revocar. CRASH-FIX previo (build 50/vc38): denunciar algo ya denunciado (409) crasheaba iOS por excepción no @Throws — guard try/catch en toda KtorModerationApi/reportMeetup (idempotente). OJO BUILD: Railway hace mvnw -DskipTests package que SÍ compila tests; añadir un parámetro a un constructor de use case rompe el build si un test lo instancia (CreateMeetupUseCaseTest) — verificar con mvnw test-compile antes de push, no solo compile.

**2026-07-05 (2)** — Backend: **denuncia de un ADMIN = moderación directa**
(el comentario/nota se borra al instante, la denuncia queda resuelta como
auditoría, sin cola ni push; USER sin acción automática). En prod.

**2026-07-05** — Release **2.10.4** (vc36, build 48, EL de las tiendas):
compartir perfiles (backend /s/u/{username|uid} + botón en perfil ajeno y
"Compartir mi perfil"; deep link "user" ya existía del push); zonas de toque
grandes (~40dp) en bandera/papelera/votos de notas y comentarios (eran
15-19dp); **bug real Android**: los ▲/▼ de notas comunitarias llamaban al
callback vacío — SchoolDetailScreen nunca pasó onVote (backend OK, verificado
con curl+token); topo ampliable en revisión de propuestas. assetlinks: los
móviles que instalaron ANTES del SHA-256 de Play cachean la verificación
fallida → reinstalar o Ajustes→Abrir enlaces.

**2026-07-04 (fase 2 admin)** — Release **2.10.2** (vc34, build 46, el que va
a App Store): admin Fase 2 completo (GESTIONAR abre la ficha real, STATS
pulsables con listas, PUSH con buscador de destinatario + confirmación "a
todos", pestaña DENUNCIAS, paridad iOS); iOS GPS pasa a seguimiento CONTINUO
con el mapa abierto (requestLocation cada 5 s enfriaba el chip → fixes
perdidos en montaña); permisos Android encadenados (notificaciones→ubicación,
PermissionsGate) + re-oferta de precisa; topo ampliable en revisión de
propuestas ("TOCA PARA AMPLIAR" + aviso SIN FOTO); zona de toque del nombre en
chats. assetlinks con SHA-256 de Play App Signing (App Links con AAB de Play).
OJO Xiaomi Android 11: no existe el diálogo de notificaciones ni el selector
precisa/aproximada (son de A13/A12+) — comportamiento correcto. MIUI cancela
`adb install` si no confirmas en pantalla (reintentar con el móvil delante).

**2026-07-04 (moderación)** — Release **2.10** (vc32, build 43): moderación
UGC completa (requisito App Store 1.2, envío CANCELADO para incluirla):
denunciar comentarios/notas (bandera) y usuarios (menú ⋯ del perfil) +
BLOQUEO (filtrado server-side de comentarios/notas + corte de chat en ambos
sentidos). Backend V50 content_reports (con snapshot) + user_blocks; push a
admins en denuncia nueva. Pestaña admin DENUNCIAS unifica contenido+quedadas
(iOS la estrena). Sembrado de datos de prueba en prod con usuario ficticio
tester-cumbre-ugc-1 (@chorreras_tester) minteando custom token con el
serviceAccountKey local → signInWithCustomToken (JIT /me falla sin email,
pero los POST funcionan). PENDIENTE Fase 2 admin: GESTIONAR con mapa nuevo
completo en modo admin, diff de propuestas con foto ANTES/DESPUÉS lado a
lado y todos los campos, STATS pulsables (cada cifra abre su lista), push
con buscador de destinatario, paridad iOS del panel.

**2026-07-04 (cierre)** — [build final iOS: 42, SOLO iPhone — TARGETED_DEVICE_FAMILY=1; ASC exigía capturas de iPad 13" y la app no tiene diseño de tablet]  Release final del día: **2.9.7 (vc31 / build 41)**.
AAB `cumbre-2.9.7-vc31.aab` entregado para Play y build 41 en TestFlight (el
que va a revisión de App Store). Últimos retoques: tocar sector nunca aleja
(max(zoom,15)), Z ancla como pin (mitad inferior del bitmap transparente),
radar arranca en SATÉLITE por defecto (ambas). Ficha App Store: descripción
actualizada con radar/boletín/alertas + atribución AEMET/Open-Meteo;
capturas 6,5" convertidas a 1284×2778 SIN canal alfa (ASC rechaza alfa).
Decisión de negocio: lanzar GRATIS (la suscripción mensual in-app se puede
añadir en cualquier momento; lo único irreversible sería pago-por-descarga,
que no es el modelo).

**2026-07-04 (noche 4)** — Release **2.9.5** (vc29, build 40): tocar
sector/parking usa ZOOM FIJO centrado (15.0/14.5) — los fitBounds calculados
oscilaban entre extremos según los datos; z-order de marcadores (piedras
debajo, Z/P/escuela encima); iconos iOS ~25% más pequeños y Z de Android
más grande (68 px). Lección de la tarde: los encuadres por bounds en mapas
pequeños son imprevisibles — zoom fijo siempre que se pueda.

**2026-07-04 (noche 3)** — Release **2.9.4** (vc28, build 39): bounds con
margen mínimo (inflatedBounds ~450 m) en todos los encuadres — tocar un
sector con piedras pegadas hacía fitBounds sobre un área diminuta → zoom
extremo; iOS conserva la cámara al entrar/salir de fullscreen (onCameraChange
→ el MapView recreado arranca donde estabas, sin auto-fit) y su encuadre
inicial da contexto; **Android: pestañas keep-alive como iOS** — las 5 tabs
viven compuestas en un host único "tabs" (zIndex/alpha, lazy por visita) →
sin flash ni recreación de mapas al cambiar de pestaña.

**2026-07-04 (noche 2)** — Release **2.9.3** (vc27, build 38): botón de
ubicación DENTRO de la botonera del mapa (34 dp compacta; separada se
solapaba/desbordaba de los 280 dp), iconos reales en Android (GpsFixed,
OpenInFull), zoom al tocar piedra/zona capado a 15.2/15.0 (Esri se queda sin
resolución y el mapa "desaparecía"), y capa background color papel en TODOS
los estilos raster — el flash azul al abrir Radar era el fondo por defecto de
MapLibre mientras cargaban los tiles.

**2026-07-04 (noche)** — Release **2.9.2** (vc26, build 37): botonera del mapa
arriba-derecha (se solapaba con ubicación) + botón re-centrar escuela; iOS ya
no salta el zoom al ocultar capas (auto-fit solo con marcadores NUEVOS —
lastFittedIds acumulativo en MapLibreView); buscador con placeholder claro y
sector en resultados (sectorName en /api/search/lines); "Editar piedra/muro"
Android pasa a ModalBottomSheet; CAUSA RAÍZ de la "imagen fantasma" entre
pestañas: el crossfade del NavHost (no el SurfaceView) → transiciones None.

**2026-07-04 (tarde)** — Release **2.9.1** (Android vc25, iOS build 36), ronda
de feedback sobre la 2.9 con maquetas aprobadas: comentarios rediseñados flat
(solo en vías, no en la piedra), botonera lateral del mapa SIEMPRE con las
formas reales de los marcadores + topo/satélite de un toque, clustering
ELIMINADO a petición (se ve todo; capas para limpiar), brújula (cono de
dirección en el punto azul), buscador GLOBAL de vías/bloques en Escuelas
(/api/search/lines), ✕ para borrar vías EXISTENTES en el editor (backend
reconcilia el borrado en piedras POINT si el payload trae targetLineId),
OPCIONES desplegable en ficha de piedra, estrellas grandes, roca ordenada
Granito/Caliza/Arenisca. OJO: onDelete de vías existentes estaba capado en
AMBAS apps porque el backend no borraba omitidas — ya no.

**2026-07-04** — Release **2.9** (Android vc24, iOS build 35): buscador de
vías/bloques en detalle; fix GPS (solo pedíamos COARSE + balanced en Android y
kCLLocationAccuracyKilometer en iOS → 500 m-1 km; ahora FINE/HIGH_ACCURACY +
refresco 5 s del punto azul); mapa de escuela con leyenda-toggle de capas,
pantalla completa estilo Radar y clustering de piedras; comentarios+votos en
piedras/vías (line_comments V49, patrón notes) + descripción opcional de vía
(block_lines.description, aceptada también en bloquesJson de contribuciones);
radar 4 descargas paralelas recientes-primero + caché disco (play en 1-2 s);
textureMode en MapLibre (imagen residual entre pestañas); términos con
descargo de ubicaciones aportadas por usuarios. OJO Swift: los data class de
Kotlin exportan init SIN defaults → añadir un campo a un DTO rompe todas las
llamadas Swift (AdminView). Pendiente: checklist de Rodrigo → OK → AAB vc24.

**2026-07-03 (noche)** — Release **2.8** (Android vc23, iOS build 33) con
invitación a quedadas por enlace (/s/q/{id}?i=token HMAC; FOLLOWERS se salta,
WOMEN nunca) y botón compartir en chat de grupo y detalle. **Push
multi-dispositivo** (backend): users.fcm_token era UN token por usuario →
login en el 2º móvil machacaba el del 1º (por eso iOS "no recibía"); tabla
user_devices (V48) + PushSender.sendToUser/sendDataToUser con fan-out y
limpieza de tokens inválidos; las apps no cambian. Segunda causa raíz (la
definitiva): con FirebaseAppDelegateProxyEnabled=false hay que pasar el token
APNs a Messaging A MANO en el AppDelegate — faltaba → iOS nunca generó token
FCM ni se registró. Fix en build 34 (TestFlight), VERIFICADO: push llega a
Android e iPhone a la vez. Descubierto: el boletín de
montaña AEMET es ESTACIONAL — en verano solo publican Pirineos+Riojana; los
otros 5 macizos devuelven 204 (no es bug). INVITE_SECRET pendiente de setear
en Railway (prod+staging).

**2026-07-03 (tarde)** — Releases **2.6/2.6.1/2.7** el mismo día. Notas
comunitarias plegables + votos ▲/▼ (tabla note_votes; OJO: SMALLINT vs int
tumbó prod 10 min — regla nueva: verificar staging UP antes de mergear main).
**Compartir vías/bloques y escuelas**: landings /s/* con Open Graph + foto
(proxy URL firmada), assetlinks + apple-app-site-association, App Links
Android (autoVerify) y Universal Links iOS (ShareLinkRouter + fullScreenCover);
texto según disciplina. **Push iOS ACTIVADO** (APNs key en Firebase prod,
capability + Associated Domains en App ID, perfil CI regenerado → secret
PROVISIONING_PROFILE_BASE64). Deep-link diario Android roto por inserción
tardía del boletín (LazyColumn destruía el diálogo) → boletín en paralelo.
Pendiente: SHA-256 de Play App Signing para assetlinks; dominio con marca
(cumbre.*) si Rodrigo lo compra; invitación a quedadas por enlace (tarea).

**2026-07-03** — Release **2.5** (Android vc19, iOS build 29). Pestaña **Radar propia**
(AEMET OpenData: recolector cada 10 min + compuesto España cosido de los 15
radares regionales, repintado en azules Cumbre, player HOY/AYER, retención 48h,
servido por /api/radar/*; coordenadas de antenas de la BD OPERA). **Boletín de
Montaña AEMET** en detalle de escuela (9 macizos, alerta de tormentas iluminada,
/api/mountain/bulletin). Pestaña **Perfil** en la tab bar (fuera de Escuelas).
Mini-fichas unificadas en mapas de Escuelas/Quedadas. Bugs cazados: atlas de
iconos de MapLibre corrupto (rayas gigantes → caché de Icons), zona horaria
UTC/Madrid escondía las últimas 2h del radar, .task en Group vacío (iOS) nunca
disparaba. Prod y staging comparten key AEMET → RADAR_CRON desfasa los crons.
Pendiente decidir: "segunda opinión" AEMET vs Open-Meteo; modelo de precio
(Rodrigo sopesa 1 mes gratis → suscripción).

**2026-07-02** — Se construyó completa la pestaña **Agarres** (dinamómetro BLE
WH-C06: medir máximos, entrenos, juego arcade) y **se eliminó entera** el
mismo día a petición de Rodrigo tras probarla. Recuperable en git si se
retoma (app `b69ccf4`, api `e4c8d18`) — NO reintroducir sin que lo pida.
Release **2.1** (Android 15, iOS build 22) sin Agarres, en TestFlight y Play.
Revisión de seguridad completa: sin fallos graves (ver 🔜 Pendiente arriba).
El pipeline de producción iOS (TestFlight) quedó resuelto la sesión anterior
tras una cadena de 7 problemas — **nunca usar el fork `akaffenberger` de
Firebase** (duplica el SDK → crash al arrancar); usar siempre el oficial.

**2026-06-30** — i18n ES/EN completo (Android+iOS, selector de idioma al
primer arranque + en Perfil). Material de escalada en Quedadas (quién lleva
qué). Filtros completos en la alerta de quedadas + fix de un bug real:
`matchesDays` comparaba día-de-semana contra fechas ISO → las alertas nunca
coincidían en producción. App Store Connect: cuenta aprobada, app creada como
"Cumbre Climbing".

**2026-06-22 a 2026-06-26** — Entorno **staging** aislado en Railway (BD
propia; debug→staging, release→prod — regla: pedir OK antes de tocar
backend/mergear). Revisión de seguridad (API keys restringidas, keystore
purgado del repo). Ayuda/onboarding contextual (hojas "?", coach-marks,
estados vacíos). Comparador de escuelas rediseñado. Chat offline (perfiles
cacheados, envío optimista). Fix de navegación Android: pantalla en blanco al
volver de un sheet (bug de `NavHost` interno).

**2026-06-15 a 2026-06-21** — **Muros largos** (geometría LINE, numeración
por dirección, diff de revisión admin, diario enganchado por `lineId`
estable). Modalidad **Bloque vs Vía** por piedra (stats separadas). Chat 1-a-1
y de grupo (Firestore) con modelo de privacidad real (el backend crea la
conversación, no el cliente). **App iOS arranca por primera vez** (primera
sesión con Mac) y alcanza paridad masiva con Android en las sesiones
siguientes, todo desarrollado principalmente **sin Mac** usando GitHub
Actions como único compilador/verificador — patrón de trabajo: lote de
cambios → push → CI verde/rojo es el feedback real. Instalación en iPhone sin
Mac vía AltStore (Sideloadly no funciona en este PC: crash de "anisette").
Login obligatorio al arrancar (paridad con `AppRoot.kt` de Android).

**2026-06-10 a 2026-06-15** — Preparación KMP (Fases A/B/C): `domain/` y
`data/` compartidos entre Android e iOS, DI en Kotlin
(`IosDependencyContainer`), SKIE para exponer `suspend`/`Flow` a Swift.
**Patrón bridge** establecido para todo puerto que toque una API nativa
(ubicación, auth, chat, BLE...): interfaz `suspend` en `commonMain` +
interfaz de callbacks en `iosMain` implementada en Swift + wrapper Kotlin que
convierte callbacks→suspend con `suspendCancellableCoroutine`/`callbackFlow`.
R8/minify activado en release. Offline con SQLDelight + outbox.

**Antes de 2026-06-10** — Base de la app: catálogo de escuelas, forecast con
score por hora, mapa con piedras/parkings/zonas, flujo de contribuciones
(proponer piedra/parking/sector/corrección con revisión admin), editor de
topos (líneas sobre foto), tema visual Cumbre, autenticación Firebase.

## Notas operativas

- Arranque local: ver "⚡ Arranque rápido" arriba.
- Migraciones Flyway del backend: consultar `api/src/main/resources/db/migration/`
  para la versión más reciente (no repetir aquí, cambia cada sesión).
- `serviceAccountKey.json` / `google-services.json` / `.env` / keystores —
  todos excluidos de git en ambos repos, verificado.
- Spring Security: endpoints públicos listados en `SecurityConfig.java`
  (catálogo de escuelas, perfiles públicos si `isPublic`, forecast, health);
  todo lo demás exige token Firebase; `/api/admin/**` exige rol admin.
