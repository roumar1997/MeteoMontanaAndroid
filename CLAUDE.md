# MeteoMontana Android — contexto para Claude

App Android nativa (Kotlin + Jetpack Compose) que replica la PWA MeteoMontana
y se conecta al backend Spring Boot.

> 🎯 **Decisión 2026-06-06**: la app evolucionará a **Kotlin Multiplatform
> (KMP)** para soportar también iOS. Compartiremos `domain/` y `data/` en
> Kotlin entre Android e iOS. UI Android sigue siendo Jetpack Compose;
> UI iOS será SwiftUI consumiendo el módulo compartido.
>
> 🎯 **Objetivo de paridad (2026-06-15)**: **la app iOS debe ser EXACTAMENTE
> IGUAL que Android** — todas las pantallas, cada interacción, cada detalle de
> diseño (textos, orden, colores, estados). Al implementar iOS, replicar el
> comportamiento Android verbatim; no inventar variantes. Checklist de paridad
> en `KMP_MIGRATION.md`.

> ## 📚 Documentos del repo
>
> - [`CLAUDE.md`](./CLAUDE.md) — este fichero. Contexto del proyecto.
> - [`KMP_MIGRATION.md`](./KMP_MIGRATION.md) — plan migración KMP + backlog iOS.
> - [`IOS_PARITY_FEEDBACK.md`](./IOS_PARITY_FEEDBACK.md) — **doc vivo**: feedback
>   de Rodrigo por feature iOS (qué debe hacer + qué mejorar + estado). LEER al
>   trabajar paridad iOS; actualizar el estado de cada punto al tocarlo.
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

## Bitácora reciente

### Sesión 2026-06-20 (2) — modalidad Bloque vs Vía por piedra (Fase 1: backend) ✅

Feature nueva: separar en el perfil **BLOQUES** y **VÍAS** (las dos modalidades
de escalada). Discriminador: **al crear una PIEDRA se elige su modalidad**
(bloque/vía); todas sus vías (líneas) heredan esa modalidad. Cuando el usuario
marca una vía hecha, su entrada de diario cuenta como bloque o vía según la
piedra. Decidido con Rodrigo: piedras existentes = BOULDER por defecto; el admin
puede cambiar la modalidad de una piedra ya creada (vía el editar bloque).

> ⚠️ **Nomenclatura**: en el código "block/bloque" YA significa "piedra"
> (`SchoolBlock.Type = BLOCK/PARKING/ZONE`). La modalidad nueva es un concepto
> APARTE: `SchoolBlock.Discipline = BOULDER (bloque) | ROUTE (vía)`. NO reusar
> "block" para la modalidad. Etiquetas UI en español: BLOQUE / VÍA.

**Fase 1 — backend (`MeteoMontanaAPI`), HECHA y verificada (compila + tests):**
- **Migración V27** (`V27__block_discipline.sql`): columna `discipline VARCHAR(16)`
  en `school_blocks` (NOT NULL default `'BOULDER'`), y nullable en
  `pending_contributions` y `journal_sessions`.
- **Dominio** `SchoolBlock`: enum `Discipline {BOULDER, ROUTE}` + campo + getter
  (constructores viejos siguen valiendo, default BOULDER).
- **JPA** `SchoolBlockJpaEntity`: campo `discipline` con `setDiscipline` (default
  BOULDER) → no toca los constructores. Adapter mapea en ambos sentidos.
- **`SchoolBlockUseCase`**: `CreateBlockRequest.discipline` + `BlockDto.discipline`;
  `create`/`update` resuelven la modalidad (`parseDiscipline`, default BOULDER).
  Esto cubre el **admin editando piedras existentes**.
- **Contribución**: `ContributionRequest.discipline` → `PendingContribution`
  (nuevo constructor con discipline; el viejo delega con null) → JPA → al
  materializar (`ReviewContributionUseCase.createBlock`) se fija en la piedra.
- **Diario**: `JournalSession.discipline` (snapshot) + JPA + adapter;
  `CreateJournalRequest.discipline`, `JournalSessionDto.discipline`.
  `JournalStatsDto` ahora trae `boulderCount` + `routeCount` (+ `blockCount` =
  total, por compat). `statsFor` cuenta ROUTE vs BOULDER.
- **Retrocompatible**: apps ya instaladas que NO mandan `discipline` → se trata
  como BOULDER (default en todas las capas). Se puede desplegar sin romper nada.

Además: **grado máximo separado por modalidad** (decisión de Rodrigo: en
escalada bloque y vía usan escalas distintas). `JournalStatsDto` añade
`maxBoulderGrade` + `maxRouteGrade` (+ `maxGrade` global por compat).

**Fase 2 — shared (KMP), HECHA (compila shared + app, salvo el recurso
`default_web_client_id` que necesita el google-services real del CI):**
- `Block.discipline` ("BOULDER"/"ROUTE", default BOULDER) + `BlockDto.discipline`
  + `CreateBlockRequest.discipline` + mapeo `toDomain`.
- `JournalSession.discipline`; `JournalStats` con `boulderCount`/`routeCount`/
  `maxBoulderGrade`/`maxRouteGrade` (+ `blockCount`/`maxGrade` globales por
  compat); DTOs y mapeos al día. `CreateJournalRequest.discipline`.
- `ContributionRequest.discipline` + `ContributionDto.discipline` (para que el
  admin pueda ver/mostrar la modalidad propuesta).
- Android `ProfileCache`: snapshot offline persiste los nuevos contadores y
  grados (construcción pasada a args con nombre).
- OJO: todas las construcciones posicionales de `Block`/`JournalStats` revisadas;
  los tests con 11 args posicionales siguen válidos (defaults).

**PENDIENTE (próximas fases):**
- **Fase 3 — Android**: selector "¿BLOQUE o VÍA?" al proponer/crear piedra
  (`ProposeContributionFlow`/`BoulderBloqueForm`); perfil con 2 contadores
  (BLOQUES / VÍAS) + 2 grados máx (`ProfileScreen`/`JournalEntriesScreen`);
  pasar `discipline` al marcar vía (`SchoolDetailViewModel.tickLine` + outbox);
  admin: editar modalidad en `EditBlockDialog`. Mostrar modalidad en la ficha de
  piedra. (Offline `SavedBlock` sin columna discipline → de momento BOULDER
  offline; añadir columna SQLDelight es opcional, baja prioridad.)
- **Fase 4 — iOS**: réplica EXACTA de lo de Android (paridad). OJO: el
  `ProfileCache.swift` y cualquier `JournalStats(...)` en Swift necesitarán los
  nuevos parámetros (SKIE regenera el init).

### Sesión 2026-06-20 — fix temperatura "ahora" del forecast (backend)

- **Bug**: en el detalle de escuela la tarjeta grande mostraba una temperatura
  "ahora" que no coincidía con la fila horaria (p.ej. 17° "ahora" vs 24° a las
  17:00), y la ventana óptima proponía franjas ya pasadas ("Óptimo 00:00–03:00").
- **Causa**: `GetForecastUseCase` y `GetForecastByLocationUseCase` asumían que
  `hours.get(0)` era "ahora". Pero Open-Meteo (`forecast_days=7`, sin
  `timezone`) devuelve el array horario **desde las 00:00 GMT de hoy**, así que
  índice 0 = medianoche, no la hora actual.
- **Fix (2 partes)**:
  1. **Zona horaria**: la causa de fondo era que `OpenMeteoClient` pedía las
     horas sin `timezone` → llegaban en **UTC** y la app pintaba el grid con
     etiquetas UTC (1-2h de desfase con la hora real de Madrid). Ahora se pide
     `timezone=auto` → Open-Meteo devuelve todo en **hora local del sitio** y
     `utc_offset_seconds` (añadido a `OpenMeteoResponse`). El grid ya muestra la
     hora real.
  2. **Índice "ahora"**: helper `findNowIndex(hourly, utcOffsetSeconds)` que
     localiza la hora presente comparando con la hora **local** (`now(UTC+offset)`).
     Se usa en `buildCurrent` (temp/hum/viento/lluvia24-72h) y en
     `pickOptimalWindow` (que solo mira horas presentes/futuras, `i >= nowIndex`).
- Ojo: el forecast está cacheado 90 min (Caffeine) por escuela; tras el deploy
  una escuela ya cacheada puede tardar hasta 90 min en reflejar el cambio (la
  caché en memoria se vacía al redesplegar Railway, así que en la práctica es
  inmediato tras el redeploy).
- Backend compila y `ClimbScoreCalculatorTest` verde. Cambio solo en
  `MeteoMontanaAPI`. Sin tocar la app (Android/iOS) → se ve solo al redesplegar.

### Sesión 2026-06-18 (2) (varias fotos por piedra = CARAS + botones follow en listas)

- **Botones de acción en las listas de follow** (Android+iOS): cada fila de
  Seguidores/Siguiendo (de cualquiera) lleva **Seguir/Siguiendo/Solicitado** para
  seguir sin entrar al perfil; en MI lista de Seguidores además **Eliminar**.
  Sin backend (deriva de `getFollowing(miUid)` + reconcilia con `getFollowStatus`).
- **Multi-foto por piedra (CARAS)** — una piedra grande no cabe en una foto:
  - **Modelo**: cada vía (`block_lines`) guarda su `photo_path` + `face_order`
    (V26, aditivo: las vías existentes heredan la foto del bloque = cara 0). Una
    **cara** = grupo de vías con la misma foto. `BlockDto.faces` agrupa por foto;
    shared `Block.faces`/`facesOrDerived()` + `BlockLine.photoPath`.
  - **Contribución**: NO cambia el esquema — cada vía del `bloquesJson` lleva su
    `photoUrl`; la materialización (`ReviewContributionUseCase`) reparte en caras
    por orden de aparición. La portada del bloque = primera cara con foto.
  - **Viewer** (Android `BlockDetailDialog`, iOS `BlockInfoSheet`): pinta cara a
    cara — FOTO 1 + sus vías marcables, FOTO 2 + las suyas… en scroll.
  - **Editor** (Android `BoulderFormDialog`/`BoulderFaceForm`, iOS
    `BoulderFormSheet`/`BoulderFaceForm`): pestañas FOTO 1/2 + "+ AÑADIR FOTO";
    cada cara su foto, sus vías y su editor de líneas. `submitBoulderFaces…` sube
    la foto de cada cara y construye el `bloquesJson` con `photoUrl` por vía.
  - **Deep-link del diario**: al pulsar una vía, su **foto/cara** se muestra la
    primera (Android `highlightVia`, iOS `BlockInfoSheet.highlightVia`).
  - **PENDIENTE**: que el **admin** revise propuestas multi-cara **agrupadas por
    foto** (hoy la card del admin muestra la 1ª foto + todas las vías; al aprobar
    SÍ se materializan bien las caras). Como Rodrigo (admin) auto-aprueba sus
    propias propuestas, no bloquea probar el flujo. Añadir editor de "+ vías a una
    cara concreta" / corregir eligiendo cara también queda para después.

### Sesión 2026-06-18 (eliminar seguidor + notifs/navegación + push app-cerrada + nº piedra/sector)

Lote de feedback de Rodrigo (5 frentes) sobre Android+iOS+backend. Todo en `main`.

- **#1 Eliminar seguidor (no solo dejar de seguir)** — backend+shared+Android+iOS:
  - Backend `MeteoMontanaAPI`: `DELETE /api/me/followers/{uid}` +
    `FollowUseCase.removeFollower(miUid, followerUid)` (= `remove(followerUid, miUid)`,
    mismo borrado que rechazar solicitud; idempotente). Protegido (anyRequest auth).
  - Shared: `KtorSocialApi.removeFollower`, `SocialRepository`, `KtorSocialRepository`,
    `RemoveFollowerUseCase`; expuesto en `IosDependencyContainer` (`removeFollower`).
  - UI: botón "Eliminar" por fila SOLO en MI lista de Seguidores
    (`canRemove = mode==followers && uid==miUid`), optimista con rollback.
    Android `FollowListScreen`, iOS `FollowListView` (botón junto al NavigationLink).
- **#2 Enrutado notifs iOS**: `NotificationsView.swift` añade casos `chat`/`message`
  → `ChatView`, `submission`/`contribution` → `MySubmissionsView` (paridad con
  Android). `compare` queda documentado (solo push; APNs iOS off). Los follows ya
  enrutaban (user/follow_request).
- **#3 Atascado en "Solicitudes" (Android)**: el deep-link a `FOLLOW_REQUESTS`
  apilaba duplicados → atrás solo quitaba una copia. Fix: `launchSingleTop` en
  TODAS las navegaciones del deep-link de `MainScreen`. iOS verificado OK
  (`FollowRequestsView` va en `NavigationStack` → atrás del sistema funciona).
- **#4 Push de chat con app CERRADA**: `FcmService` (backend) ahora manda
  **prioridad ALTA** (`AndroidConfig.Priority.HIGH` + canal/icono/color) en ambos
  métodos, y `sendDataToToken` (follows/alertas) lleva además bloque `notification`
  → fiable con app muerta (Xiaomi/MIUI ya no lo descarta); el `data` sigue para el
  deep-link al tocar y el avatar en primer plano. Android `MainActivity`: el
  launcher de `POST_NOTIFICATIONS` pasa a CAMPO de la Activity (antes se registraba
  en onCreate y podía no pedirse nunca → ninguna push). **iOS push (APNs) sigue OFF**
  (cuenta Apple de pago) — código `PushManager` listo pero desactivado.
- **#5 Nº de piedra + sector en las vías del perfil** (Android+iOS): nuevo
  `GetJournalViaInfoUseCase` (shared) que resuelve **en vivo** del catálogo de
  bloques `entryId -> (nº piedra = Block.name, sector = nombre de la ZONA por
  `sectorBlockId`)`, localizando la vía por nombre en `block.lines`. No se guarda
  (el catálogo se recicla). Subtítulo de la fila: "Escuela · Piedra N · Sector".
  Android `JournalEntriesScreen`; iOS `JournalRow` + se hila `viaInfo` por
  `JournalStatsNav`/listas y se calcula en `AccountViewModel`/`PublicProfileViewModel`.
  Sin red → solo la escuela (graceful).

> **OJO despliegue**: el #1 y el #4 necesitan el backend en Railway (main). El #4
> de notificaciones es solo-backend (vale al redesplegar). Probar push con app
> cerrada en el Xiaomi requiere conceder el permiso de notificaciones + (MIUI)
> Inicio automático activado.

**Follow-up (mismo día) — botones de acción en las listas de follow** (Android+iOS):
en `FollowListScreen`/`FollowListView`, cada fila de Seguidores/Siguiendo (de
cualquiera) lleva botón **Seguir/Siguiendo/Solicitado** para seguir desde ahí sin
entrar al perfil; en MI lista de Seguidores además **Eliminar**. El conjunto "a
quién sigo" se obtiene de `getFollowing(miUid)` (1 llamada; mi propia lista de
Siguiendo ya ES ese conjunto). Seguir es optimista y reconcilia con
`getFollowStatus` (perfil privado → "Solicitado"). Sin cambios de backend.

> **PENDIENTE DE DISEÑO — varias fotos por piedra** (idea de Rodrigo, NO implementado):
> una piedra grande no cabe en una foto → poder añadir VARIAS caras/fotos, cada una
> con sus líneas y sus vías marcables; al abrir la piedra se scrollea cara a cara;
> al proponer se añade foto→vías, foto→vías; correcciones por cara (elegir qué foto
> cambias y sus vías). Propuesta: entidad `block_faces (id, block_id, photo_url,
> sort_order)` + `block_lines.face_id`; migración = 1 cara por bloque existente;
> `BlockDto.faces` aditivo (mantener photoUrl/lines = primera cara para PWA/clientes
> viejos). Fases: 1) backend+lectura, 2) viewer multi-cara, 3) editor/proponer
> multi-cara, 4) correcciones+admin por cara. Pendiente de OK de Rodrigo.

### Sesión 2026-06-17 (9) (chat: notificaciones + modelo de privacidad real, seguridad Firestore)

Diagnóstico de Rodrigo: al escribir a otra cuenta NO llegaban notificaciones
push. Causa raíz: `POST /api/chat/notify` solo mandaba push si había follow en
algún sentido → a un receptor **público** no le llegaba. Y al revisar a fondo,
el modelo de privacidad del chat **no se cumplía en ninguna capa** (la UI
`canWrite` era siempre true; las reglas Firestore solo miraban "eres
participante del convId"). Modelo deseado: a un **privado** solo le escribe
quien él aceptó como seguidor; a un **público** cualquiera; y si la
**conversación ya existe**, ambos pueden seguir (p.ej. un público responde a un
privado que le escribió primero).

Enfoque elegido (con Rodrigo): **el backend crea la conversación** (no espejar
follow/isPublic a Firestore → sin backfill ni desincronización).

- **Backend** (`MeteoMontanaAPI`, prod Railway):
  - `PublicProfileDto`/`UserDtoMapper` exponen `isPublic`.
  - `FirebaseConfig`: nuevo bean `Firestore` (Admin SDK ya tenía credenciales).
  - Puerto `ChatRepository` + `FirestoreChatRepository` (`conversationExists`,
    `ensureConversation`; convId = uids ordenados unidos por `_`).
  - **`POST /api/chat/start`**: puerta de autorización. Permite si
    `receptor.isPublic || iFollow || theyFollow || conversaciónExiste`; crea el
    doc `conversations/{convId}` vía Admin SDK.
  - **`/chat/notify`**: misma condición (antes solo follow) → **arregla el bug**
    de que a los públicos no les llegaba push.
- **Firestore rules** (`Desktop/MeteoMontana/firestore.rules`): crear
  conversación = `false` (solo backend); crear mensaje = solo si la conversación
  **ya existe** + participante → excepción "ya hay chat" a prueba de clientes.
- **Shared/Android**: `PublicProfile.isPublic`; `KtorChatPushApi.startConversation`;
  `ChatViewModel.canWrite = isPublic || iFollow || theyFollow` (+ true si ya hay
  mensajes); `send()` llama a `start` antes del primer mensaje.
- **iOS**: `chatPushApi` expuesto en el container; `ChatView` llama a `start`
  antes de enviar y dispara `notify` (iOS no lo hacía).
- De paso: arreglado `SchoolListViewModelTest` (constructor desfasado desde que
  se añadieron `getRangeScores`/`chatService` al VM). Tests Android en verde.

> **OJO orden de despliegue**: backend (retrocompatible) → instalar apps
> actualizadas en ambos dispositivos → reglas Firestore AL FINAL. Tras desplegar
> las reglas, una app NO actualizada no puede iniciar chats nuevos (los
> existentes siguen). El fix de NOTIFICACIONES es solo-backend: vale en cuanto
> Railway redespliega, sin reinstalar.

### Sesión 2026-06-17 (8) (chat nav/borrar-para-mí, piedras numeradas, admin directo, deep-link, push iOS listo)

Lote grande de feedback de Rodrigo probando en ambos dispositivos. Backend en
Railway; APK/`.ipa` apuntan a prod. Regla de trabajo: NO generar `.ipa` salvo
que lo pida; al servirla, reabrir también Android (ver [[feedback_ipa_then_open_android]]).

- **Chat estilo WhatsApp (cont.)**: borrar = "solo para mí" (soft-delete
  `cleared_<uid>` en la conversación; no destruye la del otro; reaparece si te
  escriben). Reglas Firestore: `conversations` delete=false, mensajes solo el
  autor. Buscador "Nuevo mensaje" (seguidores ∪ seguidos). Teclado iOS ya no tapa
  los mensajes. Tocar el nombre del chat → perfil del otro.
- **Piedras = NÚMERO automático único por escuela**: el proponente no pone
  nombre; al materializar (aprobar o crear admin) se asigna el **menor número
  libre** de la escuela (se recicla al borrar; único por escuela). Quitado el
  campo nombre en proponer. Backend: `nextBlockNumber` en ReviewContribution +
  SchoolBlockUseCase.
- **Admin = todo directo**: `SubmitContributionUseCase` auto-aprueba
  (materializa) si el proponente es admin → crear piedra/parking/sector y
  corregir posición (incl. escuela) se publican al instante, en ambas apps, sin
  cambio de UI. No se auto-envía email (approve(notify=false)). Mensaje en la app
  "PUBLICADO" (no "propuesta 24-48h"). Admin puede **eliminar** desde la ficha de
  un bloque en el mapa (BlockDetailDialog/BlockInfoSheet, onDelete si admin).
- **Deep-link diario → piedra**: tocar un bloque del diario abre la escuela con
  el mapa desplegado y la **piedra abierta** (busca el bloque por nombre de vía).
  Android: ruta `schools/{id}?via=`, VM autoOpenVia, SchoolMap auto-abre. iOS:
  SchoolDetailView/SchoolMapSection openVia + SchoolLoaderView. En ESCUELAS del
  perfil: "VER ESCUELA" abre la escuela; cada vía abre su piedra.
- **Diario limpio**: dedup por escuela|sector|vía; cola offline idempotente (no
  recrea vías existentes al reconectar); ya no se guarda/mostra "Piedra: N" ni el
  sector (catálogo inestable: se borra/recicla). La fila muestra solo la escuela.
- **iOS app se llama "Cumbre"** (CFBundleDisplayName; PRODUCT_NAME sigue
  MeteoMontana para el empaquetado del `.ipa`). Notificaciones Android: icono
  grande = logo Cumbre a color cuando no hay avatar; título por defecto "Cumbre".
- **Perfil offline** (sesión previa, ya en main): ProfileCache Android+iOS.
- **Pulido UI**: quitado el banner "¿Te ayuda la app? Apóyanos" de Escuelas
  (Android+iOS). **Modo oscuro iOS**: los `Cumbre.*` se resuelven por
  `UITraitCollection` y los sheets no heredaban `.preferredColorScheme` →
  `ThemeManager.applyToWindows()` fuerza `overrideUserInterfaceStyle` en todas
  las ventanas (al arrancar/activar/cambiar modo). Además, OJO patrón: NO usar
  `Cumbre.ink` como FONDO (es el color de TEXTO: se invierte a crema en oscuro y
  deslumbra) — el botón "+ AÑADIR BLOQUE" pasó a `Cumbre.terra`.
- **PUSH iOS — CÓDIGO LISTO, DESACTIVADO** (`PushManager.swift`, `enabled=false`):
  registra remote notifications + token FCM → `PUT /api/me/fcm-token`
  (updateFcmToken expuesto en el container). FirebaseMessaging añadido a
  project.yml. Las notificaciones IN-APP (campana) ya funcionan; el PUSH del
  sistema NO hasta activar APNs.

> ### 📌 PENDIENTE — TAREAS ANTES DE PUBLICAR (bloqueantes)
> **iOS push (APNs) — ACTIVAR antes de sacar la app:**
> 1. Cuenta Apple Developer de pago → crear APNs Auth Key (.p8).
> 2. Subir la key a Firebase (climbingteams → Cloud Messaging → APNs).
> 3. project.yml: descomentar `aps-environment` + `UIBackgroundModes:
>    [remote-notification]` + `FirebaseAppDelegateProxyEnabled: true` (o reenviar
>    apnsToken a Messaging desde un AppDelegate).
> 4. `PushManager.enabled = true`.
>    (OJO: la capability rompe el sideload con Apple ID gratis → solo al publicar.)
> **iOS Sign in with Apple** — código listo, activar igual (entitlement
> `applesignin` comentado + Firebase Apple provider). Ver sesión (7).
> **iOS `PrivacyInfo.xcprivacy`** ya creado; **firma real** + cuenta Developer.
> **Play**: generar AAB firmado + Data Safety + cuenta ($25).
> **Borrado de cuenta** YA hecho (DELETE /api/me + UI) — declararlo en consolas.
> **Seguridad**: endurecer secretos en Railway (env vars) — acción de Rodrigo.
> **Deuda**: resto refactor a puertos (WeatherProvider/FileStorage) + tests.

### Sesión 2026-06-17 (7) (chat WhatsApp, modo oscuro iOS, Apple sign-in, puerto PushSender + tests)

Continuación de la (6). Backend en Railway; APK/`.ipa` apuntan a prod.
**Nota de flujo: el usuario pidió NO generar `.ipa` hasta acumular cambios** —
todo queda en `main` y se hace una sola instalación cuando lo pida.

- **Chat estilo WhatsApp (Android + iOS)**:
  - Puerto `ChatService` (shared) + `markUnread(convId)` y `deleteConversation(convId)`.
    Impl Android (`FirebaseChatService`) e iOS (`IosChatBridge`/`IosChatService` +
    `ChatBridge.swift`): markUnread pone `unread_<me>=1`; delete borra mensajes
    (lote) + doc.
  - **Badge de no-leídos en el icono de mensajes** del header (suma de unread de
    todas las conversaciones), igual que la campana. Android: `SchoolListViewModel`
    observa `observeMyConversations`. iOS: idem en su `SchoolListViewModel`
    (`startObservingChats`). Se marca leído al abrir (ya existía).
  - **Swipe**: izquierda → borrar, derecha → marcar no leído. Android
    `SwipeToDismissBox`; iOS `.swipeActions`.
- **iOS modo oscuro**: el toggle ahora alterna SOLO claro↔oscuro (quitado el
  modo "sistema"); valores antiguos pasan a claro. (`ThemeManager.swift`)
- **iOS "VER MAPA"** en el detalle: era eyebrow gris casi invisible → ahora fila
  con icono de mapa, borde y bold. (`SchoolDetailView.swift`)
- **iOS filtros**: "Favoritos"/"Guardados" sin emoticonos (ya en la (6)).
- **Sign in with Apple (iOS) — CÓDIGO LISTO, capability DESACTIVADA**:
  `AuthBridge.signInWithApple` (AuthenticationServices + nonce + CryptoKit +
  `OAuthProvider.appleCredential`) + botón en `LoginView`. El entitlement
  `com.apple.developer.applesignin` está **comentado** en `project.yml` porque
  rompería el sideload con AltStore (Apple ID gratis no soporta la capability).
  Para ACTIVAR al publicar: (1) descomentar entitlement, (2) cuenta Apple
  Developer de pago + capability, (3) habilitar Apple en Firebase Auth. El botón
  aparece pero fallará hasta entonces → NO probarlo aún.
- **iOS `PrivacyInfo.xcprivacy`** (privacy manifest, obligatorio App Store):
  tracking=false, tipos de datos (ubicación/email/nombre/fotos) y required-reason
  APIs (UserDefaults CA92.1, FileTimestamp C617.1).
- **iOS usage strings cámara/fotos** en `project.yml` (evita crash al subir foto).
- **Refactor backend — puerto `PushSender`** (DIP): `domain/port/PushSender`,
  `FcmService` lo implementa, los 5 use cases de `application` inyectan la
  interfaz. Sin cambio de comportamiento. (Primer paso del refactor a puertos;
  faltan `WeatherProvider` y `FileStorage` — este último filtra `MultipartFile`,
  requiere repensar la firma.)
- **Tests backend**: añadidos a `ClimbScoreCalculatorTest` (orden de secado por
  roca arenisca<conglomerado<granito, dryBoost viento/calor, cap por prob alta).

> ### 📌 PENDIENTE (próxima sesión)
> **Refactor a puertos (continuar):** `WeatherProvider` (OpenMeteoClient → port;
> ojo: requiere mapear OpenMeteoResponse a un tipo neutro) y `FileStorage`
> (StorageService → port; cuidado: `upload` usa `MultipartFile` de Spring web →
> cambiar la firma a bytes+contentType para no filtrar el framework). Más tests
> de use cases una vez existan los puertos (mockear).
> **Publicar — bloqueantes que quedan:** activar Sign in with Apple (cuenta de
> pago + entitlement + Firebase provider); generar AAB firmado + Data Safety
> (Play); cuenta Apple Developer + firma real + nutrition labels (App Store).
> **Seguridad recomendada:** endurecer secretos en Railway (env vars).
> **Otros:** APNs (push app cerrada); limpieza iOS (`JournalView`/`JournalStatsNav`).

### Sesión 2026-06-17 (6) (perfil pulible, perfil offline, selector de días, seguridad, borrado de cuenta)

Lote grande. Backend (`MeteoMontanaAPI`) en prod Railway; APK debug y `.ipa`
apuntan a `https://api.climbingteams.com` (NO al PC), así que los cambios de
backend afectan a ambas apps en cuanto Railway redespliega.

- **Perfil iOS — stats pulsables**: en `AccountView` la fila BLOQUES/ESCUELAS ya
  no vuelca el listado; al pulsar BLOQUES o ESCUELAS abres su lista (con borrar
  dentro). Componentes `AccountJournalStatsNav`/`AccountBlocksList`/
  `AccountSchoolsList`/`AccountSchoolBlocksList` (observan el VM → refresco al borrar).
- **Borrar vía offline NO reaparece** (iOS + Android): el diario del perfil
  descontaba solo borrados por clave; ahora también por **uid de la entrada**.
  Nuevo `OutboxType.JOURNAL_DELETE_ID` (payload = uid). iOS `AccountViewModel`
  encola por uid al fallar la red y filtra `pendingJournalDeleteIds`; Android
  `JournalEntriesViewModel` filtra los `JOURNAL_DELETE` pendientes.
- **Perfil OFFLINE (Android + iOS)**: `ProfileCache` (Android SharedPreferences /
  iOS UserDefaults, snapshots JSON que reconstruyen los modelos Kotlin). Se
  guarda al cargar online y se usa como fallback offline con banner
  "SIN CONEXIÓN · datos guardados". (Resuelve el pendiente #2 de la sesión 5.)
- **Pantalla Tiempo = paridad**: Android ya carga el grid de comparación de
  favoritas (faltaba inyectar `GetFavoritesGridUseCase`) y lo pinta ARRIBA como
  iOS. El grid muestra el **día de la semana** (LUN/MAR/…) en ambas (iOS ya no
  pone la fecha numérica).
- **Selector de días en Escuelas (Android + iOS)** — feature nueva:
  - Backend: `GET /api/forecast/range-scores?ids=&dates=` → `GetRangeScoresUseCase`.
    Devuelve `combinedScore` = **media de los días**, `dayScores`, qué días llueve
    (`rainy` si mm≥1 o prob≥60), `rainDays`, `maxRainMm`. Cacheado por (ids,dates).
  - **Clave de diseño**: NO se penaliza la lluvia aparte. El score diario YA
    modela lluvia + **secado por tipo de roca** (`ClimbScoreCalculator` usa
    `recentRain` en ventana de `RockDryingProfile`: arenisca 72h, granito 12h…),
    así que un día tras lluvia ya sale bajo en arenisca y se recupera antes en
    granito. El combinado = media de esos días (sin doble castigo).
  - App: chips de hasta 5 días; con días elegidos la lista pasa a "modo tramo"
    (badge = score combinado, celda por día con marca roja en días de lluvia,
    "LLUEVE X" + máx mm). `SchoolListViewModel`/`SchoolListView` + `GetRangeScoresUseCase`
    en shared, expuesto en ambos DI. Sin días = modo "hoy" de siempre.
- **iOS filtros sin emojis**: `ShowMode` "Favoritos"/"Guardados" (antes ★/⬇).
- **Revisión SOLID/seguridad/stores** (3 agentes). Notas: arquitectura 7/10,
  seguridad 6/10, Play 6/10, App Store 3/10. **Secretos verificados: NO en git**
  (`serviceAccountKey.json`/`.env` ignorados y sin historial).
- **Seguridad — ALTOS arreglados (backend)**:
  - `POST /api/schools/{id}/blocks` ahora **solo admin** (antes cualquiera creaba
    bloques sin revisión). Los usuarios usan `/contributions`.
  - `SearchUsersUseCase` ya no hace `findAll()` (DoS público): query LIKE Top100
    en BD + cap `limit` a 50 en el controller.
  - `POST /api/chat/notify` exige **relación de seguimiento** + saneo del preview.
  - `SecurityConfig`: regla explícita `/api/admin/**` (defensa en profundidad;
    cada endpoint admin ya llamaba a `AdminGuard.ensureAdmin`).
- **Borrado de cuenta (requisito de tiendas)**: `DELETE /api/me` +
  `DeleteMyAccountUseCase` (borra perfil, favoritas, follows ambos sentidos,
  diario, notifs, alerta, propuestas, notas + Firebase Auth best-effort). Botón
  "Eliminar cuenta" con confirmación en `ProfileScreen` (Android) y `AccountView` (iOS).

> ### 📌 PENDIENTE (próxima sesión) — qué falta para publicar + deuda
> **BLOQUEANTES de tienda:**
> - **iOS — Sign in with Apple**: Apple lo EXIGE por ofrecer login Google. No
>   implementado. + entitlement `com.apple.developer.applesignin`.
> - **iOS — `PrivacyInfo.xcprivacy`** (privacy manifest, obligatorio). No existe.
> - **iOS — cuenta Apple Developer ($99) + firma real**: `project.yml` firma
>   ad-hoc (`CODE_SIGN_IDENTITY "-"`), el `.ipa` actual NO es subible a la store.
> - **iOS — usage strings cámara/fotos** en `info.properties` (faltan → crash al
>   subir foto) + crear `Info.plist`/`.entitlements` que `project.yml` referencia.
> - **Android — generar AAB** (`bundleRelease`) firmado + confirmar `.jks`/passwords
>   en CI; rellenar **Data Safety** y crear cuenta Play ($25).
> - **Borrado de cuenta**: YA hecho (endpoint + UI) — declararlo en ambas consolas.
> **SEGURIDAD pendiente (no crítica, recomendada):**
> - **Endurecer secretos en Railway**: mover `serviceAccountKey.json` y
>   `POSTGRES_PASSWORD` a variables de entorno (el backend debe leerlas de env, no
>   de fichero). Requiere acción de Rodrigo en la consola de Railway.
> - MEDIOS: validar fotos por magic bytes (no solo MIME); decidir si el diario
>   público debe verse sin login (`/api/users/*/journal` es permitAll).
> **REFACTOR arquitectura (deuda, no urgente):**
> - Backend: la capa `application` importa `infrastructure` (JPA/FCM/Storage/
>   OpenMeteo) en vez de puertos. Plan: definir puertos `WeatherProvider`/
>   `PushSender`/`FileStorage` en `domain/port` + adaptadores; inyectar interfaces.
> - **Tests de dominio backend casi nulos** (solo `ClimbScoreCalculatorTest`).
>   Hacer ESTO PRIMERO (añadir tests = cero riesgo) y luego mover a puertos con red.
> **Otros (de sesiones previas, siguen abiertos):** chat restringir por seguidores
> (decisión de producto), limpieza iOS (`JournalView`/`JournalStatsNav` sin uso),
> APNs (push con app cerrada).

### Sesión 2026-06-17 (5) (vías hechas robustas offline + perfil iOS con diario inline)

- **"Vía hecha" robusta offline (iOS + Android)** — varias iteraciones tras feedback:
  - **No duplicar sin conexión**: el ✓ se calculaba solo con el diario del
    servidor; offline esa consulta falla → dejaba re-marcar. Nuevo **registro
    local** (`JournalDoneStore`: SharedPreferences en Android / UserDefaults en
    iOS) de claves `escuelaId|nombreVía`, que funciona sin red → dedup correcto.
  - **Toggle marcar/desmarcar** (ya estaba) + **desmarcar offline sincroniza**:
    nuevo `OutboxType.JOURNAL_DELETE` (payload = clave). Al desmarcar sin red se
    encola el borrado; el flusher (Android) / `flushJournalOutbox` (iOS) lo
    resuelve contra el diario y borra al reconectar. Marcar cancela borrados
    pendientes y viceversa. `doneViaKeys`/`loadDone` descuentan borrados
    pendientes para que el ✓ no reaparezca.
  - Helpers nuevos en `IosDependencyContainer`: `dequeueJournal` (devuelve Bool),
    `enqueueJournalDelete`, `dequeueJournalDelete`, `pendingJournalDeleteKeys`.
    OJO SKIE: Kotlin `Boolean` → `KotlinBoolean` en Swift (usar `.boolValue`).
- **Perfil iOS = diario inline (como Android)**: `AccountView` muestra stats
  BLOQUES/ESCUELAS/GRADO MÁX + "+ AÑADIR BLOQUE" + lista con BORRAR directamente
  (quitado el enlace "Mi diario"). `AddBlockSheet`/`JournalRow`/`JournalStatsRow`
  pasaron a internal para reutilizarse. `JournalView`/`JournalStatsNav` quedan
  sin uso (se pueden limpiar).
- **Instalación local Android con login real**: `google-services.json` real está
  en `C:\Users\rouma\Downloads\google-services (6).json` (proyecto climbingteams,
  package com.meteomontana.android). Copiar a `app/` + `local.properties` con
  `sdk.dir=C:/Users/rouma/AppData/Local/Android/Sdk` para compilar; el APK local
  se firma con el debug.keystore del PC (SHA-1 registrado en Firebase → login OK).
  La app instalada del CI está firmada con otra clave → para instalar el APK local
  hay que **desinstalar primero** (se pierden datos offline) y aceptar el diálogo
  de instalación por USB en el Xiaomi.

> ### 📌 PENDIENTE (para próxima sesión)
> 1. **Mensajes (chat) — decisión de producto + implementación si se quiere
>    restringir.** Hoy el chat (Firestore) NO restringe por seguidores: cualquiera
>    con perfil visible (público) puede escribir y el otro responder; los privados
>    no son visibles para no-seguidores → no les pueden escribir. Si Rodrigo quiere
>    exigir **seguir** o **seguimiento mutuo** para escribir, hay que añadir la
>    regla en UI (botón MENSAJE condicionado) + reglas de seguridad Firestore.
>    PENDIENTE: que Rodrigo decida (abierto vs. seguir vs. mutuo).
> 2. **Perfil y diario OFFLINE (cachear).** Hoy el perfil propio no carga sin
>    conexión (pantalla vacía). Propuesta: cachear el `PrivateProfile` y el diario
>    (lista + stats) la última vez que cargan online y mostrarlos offline con aviso
>    "sin conexión"; avatar cacheable en disco como las fotos de piedras. El
>    registro local de vías hechas (`JournalDoneStore`) ya existe y puede ayudar.
> 3. **Limpieza menor iOS**: borrar `JournalView`/`JournalStatsNav` (sin uso tras
>    mover el diario al perfil) y los structs `AddLinesSheet`/`EditLineSheet`
>    antiguos si siguen sin uso.
> 4. **APNs** (push real con app cerrada) sigue pendiente; lo in-app funciona.

### Sesión 2026-06-17 (4) (lote feedback: toolbar, tic toggle, grados, notifs)

- **Android**: botón "guardar offline" movido al **toolbar** superior (como iOS).
- **Tic "vía hecha" → TOGGLE** (iOS+Android): marca/desmarca, **sin duplicar**
  (si ya está hecha, al tocar la quita: borra la entrada subida y/o la pendiente
  en la cola). El tic de Android ya **no** muestra el diálogo de "propuesta
  enviada" (causaba el "se va a solicitudes").
- **Fix grados blancos sobre blanco**: grados ≤5c (blancos) ahora con texto
  oscuro en el diario (iOS `JournalView`) y en la lista de vías (Android
  `BlockDetailDialog`).
- **Notificaciones**: al salir de la bandeja se marcan todas leídas y el badge
  de la campana se refresca al volver (iOS onDismiss/onDisappear; Android
  DisposableEffect + lifecycle ON_RESUME). iOS: tocar una notif **FOLLOW_REQUEST**
  abre la lista de solicitudes (aceptar/rechazar); antes no hacía nada.
- **Pendiente / decisión de producto**: el chat (Firestore) NO restringe por
  seguidores — cualquiera con perfil visible puede escribir y responder. Si se
  quiere exigir seguir/seguimiento mutuo, hay que añadir esa regla (UI + reglas
  Firestore). Ver respuesta a Rodrigo.

### Sesión 2026-06-17 (3) ("vía hecha" persistente + cola offline en AMBOS)

- **El tic de "vía hecha" queda marcado (✓) de forma persistente** y funciona
  **sin conexión** en iOS y Android (decisión de Rodrigo: cola offline en ambos):
  - Tipo nuevo `OutboxType.JOURNAL` en el outbox compartido.
  - **Android**: `OutboxFlusher` sube las JOURNAL al recuperar red (ya observaba
    conexión). `SchoolDetailViewModel.tickLine` POSTea si hay red, si no ENCOLA.
    `doneViaKeys` (combine diario + cola pendiente) → `SchoolMap` pinta ✓ al abrir.
  - **iOS**: nuevos helpers en `IosDependencyContainer` (`enqueueJournal`,
    `pendingJournalKeys`, `flushJournalOutbox`). `BlockInfoSheet.tick` encola si
    falla; `loadDone` marca ✓ desde diario + cola. `MeteoMontanaApp` drena la cola
    al arrancar y al volver a primer plano (`scenePhase == .active`).
  - Match vía por "escuela|nombreVía" (mismo nombre que se guarda al marcar).
  - Android compila (build+tests verdes local); iOS pendiente de CI.

### Sesión 2026-06-17 (2) (offline: vínculo sector + etiqueta + "vía hecha")

- **Vínculo piedra↔sector ahora se guarda offline** (era el bug gordo): la tabla
  `SavedBlock` (SQLDelight) no tenía `sectorBlockId` → offline las piedras no
  pertenecían a su sector (tocar "La Isla" no colapsaba sus piedras, etc.).
  Añadida la columna + en `insertBlock`, `saveOffline` y `toBlock`. BD regenerada
  (`meteomontana_sql_v4.db` en ambas plataformas; caché regenerable, hay que
  re-descargar las guardadas).
- **Nombre del sector legible al hacer zoom** (online y offline) sin pulsarlo:
  `MarkerRenderer.zone(name:)` pinta el nombre bajo el pin "Z" cuando
  `showName`; `SchoolMapSection` y `OfflineSchoolView` activan `showName` para
  ZONE con `mapZoom >= 13.5` (tracking vía `onZoomChange`).
- **Marcar una vía como HECHA** (iOS + Android, paridad): tic por vía en la ficha
  de la piedra → crea una entrada de diario (`POST /api/journal`, ya existía) con
  escuela/sector/nombre vía/grado. iOS: tic en `BlockInfoSheet` →
  `createJournalEntry`. Android: `onTickLine` en `BlockDetailDialog` →
  `SchoolDetailViewModel.tickLine` (nuevo, inyecta `CreateJournalEntryUseCase`).
  Sin cambios de backend. Android compila local; iOS pendiente de CI.

### Sesión 2026-06-17 (iOS: offline completo en el detalle)

- **Detalle offline ahora carga piedras + vías + fotos** (antes solo el mapa con
  el pin de la escuela). En `SchoolDetailView.swift`, `SchoolMapSection` usaba
  `getBlocks.invoke` (solo red) → sin internet devolvía `[]`. Nuevo helper
  `loadBlocksOnlineOrOffline()` (usado por `.task(id: expanded)` y `reloadBlocks`):
  intenta red; si falla o vuelve vacío, cae a `savedSchools.loadOffline(id)` y
  mapea con `repo.toBlock(entity:lines:)`. Las vías vienen en `snap.lines`; las
  fotos las resuelve `TopoPhotoView` desde `ImageCache` (disco). Espejo de
  `OfflineSchoolView`. Tiles del mapa siguen necesitando red (futuro).
- ⚠️ Solo Swift (no toca `shared`); pendiente de que el CI iOS compile (sin Mac).

### Sesión 2026-06-16 (admin iOS: pulido de correcciones + GESTIONAR a fondo)

Rama `claude/sleepy-gagarin-b8a8f8`. Tres mejoras del panel admin iOS, todo
**pendiente de que el CI compile** (sin Mac; no probado en pantalla).

- **Visualización de CORREGIR clara**: `ContributionMapSheet` ahora encuadra
  **ambos** marcadores (✕ viejo gris + ★ nuevo ámbar) al cargar — nuevo
  `MapLibreView.fitToCoordinatesOnLoad` + delegate `mapViewDidFinishLoadingMap`
  (fit una sola vez, cap a zoom 16.5 si las coords están casi pegadas). Antes el
  mapa se centraba en la coord vieja a zoom 15 fijo y el destino podía quedar
  fuera de pantalla. La card distingue **"MUEVE LA ESCUELA ENTERA"**
  (`targetBlockId == nil`) de "MUEVE «‹bloque›»".
- **GESTIONAR — mover bloque pulsando en el mapa**: botón "📍 MOVER PULSANDO EN
  EL MAPA" en `BlockManageSheet` → banner Terra "PULSA LA NUEVA POSICIÓN" →
  `MapLibreView.onMapTap` fija coords y hace `updateBlock` preservando vías.
- **GESTIONAR — editar descripción del bloque**: campo DESCRIPCIÓN en
  `BlockManageSheet`. Sorteado el choque `NSObject.description` de SKIE con un
  alias en el modelo compartido: `Block.descriptionText` (`get() = description`)
  en `shared/commonMain` (Android sigue usando `description`, sin ripple).
- ⚠️ Toqué `shared/commonMain/.../Block.kt` → recompila con SKIE. Cambio trivial
  (un getter). `IOS_PARITY_FEEDBACK.md` actualizado (admin completo + propose
  PIEDRA/SECTOR/CORREGIR marcados ✅, que estaban desfasados a ⬜).
- **Pendiente admin**: APNs (push con app cerrada); lo in-app funciona.
- **Offline en iOS — primera versión** (a validar): reutiliza `SavedSchoolRepository`
  de `shared` (ya tenía todo: SavedSchool/Block/Line/Forecast). Expuesto
  `savedSchools` en el container. Botón **descargar** en el toolbar del detalle →
  `saveOffline(school, blocks, forecast)` + **pre-descarga de fotos** (`ImageCache`
  Swift, FNV hash estable en `Caches/photo-cache`). `TopoPhotoView` lee de
  `ImageCache` (fotos sin red). **SavedSchoolsView** (lista, desde el perfil) →
  **OfflineSchoolView** (lee `loadOffline`: forecast + mapa con marcadores +
  piedras + fotos de la caché). ⚠️ Los **tiles del mapa** sí necesitan red (cachear
  tiles = extra futuro; Android usa `OfflineTileManager`). El catálogo/lista ya
  cacheaba (`CachedSchoolsRepository`).
- **Chat (Firestore) en iOS — primera versión** (pendiente de validar en device):
  bridge `IosChatBridge`/`IosChatService` (iosMain) que envuelve Firestore en
  `Flow`/`suspend`, con DTOs de nivel superior (`IosConvDto`/`IosMsgDto`) para no
  construir clases anidadas desde Swift. Swift `ChatBridge.swift` (FirebaseFirestore,
  misma estructura que `FirebaseChatService` de Android: colección `conversations`
  + subcolección `messages`). `chatService` cableado en `IosDependencyContainer` y
  `AppDependencies`. Pantallas `ChatView` (conversación + enviar) y `ChatListView`
  (mis conversaciones, resuelve nombres con getPublicProfile). Entradas: botón
  **MENSAJE** en el perfil público + icono de chat del header → lista. Falta probar
  en iPhone y el push real (APNs/FCM token). El backend `notifyMessage` (push) lo
  llama Android; iOS aún no.
- **Editor unificado de vías** (iOS, `EditLinesSheet`): un botón "✎ EDITAR /
  AÑADIR VÍAS" en `BlockInfoSheet` abre un editor con TODAS las vías (existentes
  precargadas + "+ NUEVA VÍA"); tocas cualquiera para cambiar nombre/grado/tipo o
  redibujarla (selector por chips en `TopoEditorView`), y al enviar manda una
  corrección por cada vía existente modificada (diff) + una propuesta con las
  nuevas. Sustituye el lápiz por vía y el flujo "+ AÑADIR VÍAS" sueltos.
  `BoulderBlockForm` gana `existingLineId`. (Los structs `AddLinesSheet`/
  `EditLineSheet` quedan sin uso → limpiar.)
- **Tocar zona oculta/muestra piedras** (mapa escuela, iOS + Android): tocar un
  marcador ZONA con piedras colapsa/expande las de ese sector. iOS
  `SchoolDetailView`, Android `SchoolMap` (tap centralizado `onBlockTap` +
  `visibleMarkers`).
- **Líneas piedra→sector en el mapa del admin** (iOS): `MapLibreView` ahora dibuja
  **polilíneas** (`CumbrePolyline` + delegates stroke/width/alpha). En ASSIGN_SECTOR
  traza piedra→sector viejo (gris) y piedra→sector nuevo (verde) en el mini-mapa y
  el mapa a pantalla completa.
- **Difuminado SOLO de la vía que cambia** (editor + admin): las demás vías
  existentes se ven **normales** (sólidas, con número y tipo de inicio); solo la
  **versión vieja de la vía que se corrige** va difuminada, para que se distinga
  cuál cambió (si todo iba difuminado, el admin no sabía cuál era). `TopoPhotoView`
  separa `normalLines` (sólidas) de `referenceLines` (difuminada); `TopoEditorView`
  separa `normalLines` de `fadedLines`. Al **añadir** vías, las existentes quedan
  tal cual.
- **Editor de vías: redibujar empieza de cero** (no alarga la línea): cada trazo
  nuevo limpia la línea del bloque seleccionado (`TopoEditorView.drawingActive`).
  Al **corregir** (`EditLineSheet`) la línea editable arranca **vacía** y la vieja
  se ve **difuminada** (referencia = todas las vías); si no se redibuja, se
  conserva el trazo original al enviar.
- **Mapa del admin con contexto**: `ContributionMapSheet` y un **mini-mapa inline**
  en la card cargan los bloques de la escuela → pintan los existentes **atenuados**
  (ver si la propuesta pisa algo) y resaltan el cambio. `ASSIGN_SECTOR` muestra en
  el mapa la **piedra ★** + **sector viejo ✕ gris** + **sector nuevo ★ verde**
  (antes el admin no veía qué piedra ni de/ a qué sector). `contributionMarkers`
  ahora recibe `blocks` y `contextMarkers`/`blockTypeColor`/`markerKindFor` nuevos.
- **Admin ve QUÉ cambia en vías (iOS, espejo de ContributionCard.kt)**: la card
  de BOULDER al revisar **corregir/añadir vías** carga la piedra destino
  (getBlocks por schoolId), usa SU foto y dibuja **existentes difuminadas +
  nuevas sólidas** (`TopoPhotoView.referenceLines`), con texto ORIGINAL/PROPUESTA
  (nombre·grado·tipo). `ASSIGN_SECTOR` ahora muestra PIEDRA → SECTOR por nombre.
  El editor (`TopoEditorView`) pinta el **badge de tipo** (PIE/SIT/LAN/TRV) al
  final de cada línea mientras dibujas (antes "desaparecía"). Antes el admin solo
  veía las nuevas y con `photoUrl` nil (corregir/añadir) no veía nada.
- **CAMBIAR SECTOR de una piedra ya asignada** (iOS + Android, paridad): antes el
  botón solo salía si la piedra no tenía sector. Ahora sale si hay ≥1 sector
  distinto al actual; etiqueta "+ ASIGNAR SECTOR" (sin sector) o "CAMBIAR SECTOR"
  (ya tiene); el picker excluye el sector actual. El backend ya sobrescribía el
  sector al aprobar `ASSIGN_SECTOR` (sin cambios). iOS: `BlockInfoSheet` +
  `AssignSectorSheet`; Android: `BlockDetailDialog` + caller `SchoolMap`.
  **Ajuste**: el botón aparece si la escuela tiene **algún** sector (antes exigía
  uno distinto al actual → en escuelas con 1 solo sector no salía nada); el picker
  excluye el actual y, si no hay otro, avisa de crear uno con "+ PROPONER → SECTOR".

### Sesión 2026-06-16 (mapas iOS a fondo + proponer/editar + seed prod)

Rama `claude/stoic-moser-40955c` (merge a `main` por push directo; ver workflow).
Trabajo iOS de paridad de **mapas** y **contribuciones**, todo verificado por CI
(verde) y probado por Rodrigo en iPhone. **Estado: HECHO salvo ADMIN (siguiente).**

- **Mapas (MapLibreView.swift + MarkerRenderer.swift)**:
  - Toggle **Topográfico / Satélite** (Esri) reutilizable (`MapStyleChips`) en
    mapa de detalle y de lista.
  - Marcadores con FORMA por tipo (parking cuadrado "P", zona pin "Z", piedra
    polígono de roca con nombre, escuela triángulo, usuario punto azul) +
    **diamante con score y nombre** en la lista. Drawing en `MarkerRenderer`.
  - **Fix raíz**: ya no re-centra/re-crea marcadores en cada update (causaba que
    el mapa de la lista se "perdiera"); diff por firma. `autoFitToMarkers`
    (fitBounds al cambiar el set, blindado contra encuadre degenerado = el
    "pillado" al filtrar favoritas). Etiquetas con zoom ≥ 8.5.
  - **Fix tap**: el `UITapGestureRecognizer` (fijar posición) robaba el tap a la
    selección de marcadores → se desactiva salvo en proponer/corregir. Ahora se
    puede tocar escuela (lista→popup) y bloque/parking/zona (detalle→panel).
  - Mapa de detalle reubicado **entre "tiempo actual" y "Próximas 16 h"**
    (`ForecastBodyView.mapSlot`). Punto azul de mi ubicación también en detalle.
- **Tocar piedra (BlockInfoSheet)**: foto con vías dibujadas (`TopoPhotoView` ≈
  TopoPhotoCanvas/renderTopo, parsea linePath JSON), lista de vías por grado,
  coords, CÓMO LLEGAR.
- **Contribuciones (ProposeFlow.swift)** — espejo de ProposeContributionFlow:
  - Proponer **PIEDRA** (BoulderFormSheet: nombre, sector opcional, bloques con
    grado+tipo, foto, **editor de líneas** TopoEditorView con arrastre), **SECTOR**,
    **PARKING**, **CORREGIR POSICIÓN** (elige marcador → nueva posición → acepta).
  - **+ AÑADIR VÍAS** a piedra existente (AddLinesSheet, reusa foto + vías de
    referencia) → BOULDER con `targetBlockId`.
  - **✎ CORREGIR VÍA** (EditLineSheet, precarga la vía) → BOULDER con
    `targetBlockId+targetLineId`. **+ ASIGNAR SECTOR** (AssignSectorSheet) →
    `ASSIGN_SECTOR`. Fila de bloque compartida = `BoulderBlockRow`.
- **Backend V25** (`MeteoMontanaAPI`, prod Railway): seed de pruebas — usuario
  público falso `demo-cumbre-001` (cumbre_demo, con foto) + notificación
  `NEW_FOLLOWER` para Rodrigo (por email). Reversible borrando esas 2 filas.
- **Notas técnicas SKIE**: campos `Double?`/`Int` de Kotlin llegan como
  `KotlinDouble?` / `Int32` → envolver/convertir (`KotlinDouble(double:)`,
  `Int(...)`). `block.description` choca con `NSObject.description` (no usar).
- **Flujo sin Mac**: lote de cambios → push a `main` → CI iOS compila (verde/rojo
  es el feedback real) → `.ipa` a `C:\Users\rouma\ipa-serve\` servido por
  `python -m http.server 8000` → AltStore. IP PC: 192.168.0.12.

- **ADMIN iOS completo** (`AdminView.swift`, commits "paso 1..4"):
  - Use cases expuestos en `IosDependencyContainer`: getAdminStats, getAdminLogs,
    sendPush, updateBlock, deleteBlock (getSchools ya estaba).
  - Tabs: **PROPUESTAS** (filtros TODAS/PIEDRAS/SECTORES/PARKINGS/MOVER +
    agrupación por escuela; cards ricas que muestran QUÉ cambia: CORREGIR
    ✕actual→★nueva con coords, PIEDRA foto+líneas de bloquesJson, ASIGNAR SECTOR;
    "VER EN MAPA" a pantalla completa con viejo gris ✕ + nuevo amarillo ★),
    **GESTIONAR** (buscar escuela→mapa con bloques→editar nombre/coords
    preservando vías, o borrar), **STATS**, **ACTIVIDAD** (logs), **PUSH** (manual).

> **SIGUIENTE / pendientes iOS** (menores, no bloqueantes):
> - Admin GESTIONAR: mover bloque pulsando en el mapa (ahora se edita por
>   coords/texto); editar la descripción del bloque (se omite por el choque
>   `NSObject.description` en SKIE — buscar el accesor real o un alias).
> - **APNs** (push real al iPhone cerrado) — sigue pendiente; las notificaciones
>   in-app sí funcionan.
> - Bridges aún pendientes: chat (Firestore), Sign in with Apple.
> - Quitar el seed de pruebas V25 del backend cuando ya no haga falta.

### Sesión 2026-06-16 — iOS: paridad masiva (login al arrancar + features) + instalación sin Mac

- **Instalación en iPhone sin Mac VALIDADA**: el `.ipa` de GitHub Actions se
  instala con **AltStore** (no Sideloadly: su provisión "anisette" crashea en
  Windows — access violation en CoreADI.dll, bug conocido). AltServer (PC) +
  AltStore (iPhone). Para pasar el `.ipa` al móvil: **mini-servidor web** en el
  PC (`python -m http.server` en carpeta aislada) y descargar desde Safari del
  iPhone (misma WiFi) → Archivos → AltStore `+`. (iCloud Drive también vale pero
  tarda; Gmail bloquea adjuntos `.ipa`.)
- **CI iOS afinado**: `concurrency` (cancela builds viejos) + **AppIcon 1024**
  generado desde `logo_cumbre` (el catálogo de assets lo exige). Build ~6 min
  con caché de konan. Cada push a `main` deja un `.ipa` nuevo en Artifacts.
- **Login obligatorio al arrancar** (ver sesión (4) abajo).
- **Features nuevas iOS** (todas con use cases que YA estaban en `shared`; solo
  hubo que exponerlos en `IosDependencyContainer` + escribir SwiftUI):
  - **Favoritas**: estrella optimista en lista y detalle (revierte si falla red).
  - **Notas** de escuela: leer + publicar texto (foto pendiente de bridge Storage).
  - **Perfil** real (`AccountView`): avatar, nombre, usuario, bio, grado, badges
    admin/premium + enlaces a mis propuestas / contribuciones / solicitudes.
  - **Notificaciones** (`NotificationsView`): inbox + marcar leídas (campana).
  - **Modo oscuro**: colores `Cumbre` dinámicos (UIColor traitCollection) +
    `ThemeManager` persistido; la luna del header cicla sistema/claro/oscuro.
  - **Distancia "· N KM"** en la lista (Geo.haversineKm desde tu ubicación).
  - **Caché de escuelas** (SQLDelight, stale-while-revalidate): la BD la crea
    Swift con `DatabaseFactory().create()` y se pasa al container (`database:`).
    No se puede crear en el container porque el `DatabaseFactory` de Android
    necesita `Context` (expect/actual con firmas distintas).
  - **Grid de favoritas** en el tab Tiempo (score medio por día).
  - **Social**: buscar usuarios (lupa del header), perfil público con seguir/
    dejar de seguir optimista + contadores, solicitudes de seguimiento.
  - **Mis propuestas / mis contribuciones**: listas de solo lectura en el perfil.
- **Patrón de trabajo sin Mac**: como el CI de iOS COMPILA el Swift, cada push
  verifica de verdad. Se desarrolla en lotes, push a `main`, y el build (verde/
  rojo) hace de feedback. Errores que pilló el CI: formato de proyecto 77
  (→ macos-15 + Xcode 16) y el AppIcon que faltaba.
- **Segunda tanda (paridad masiva, auditoría)**: se lanzó un Workflow de
  auditoría (16 agentes) comparando cada pantalla Android vs iOS → lista maestra
  de gaps. Implementado todo lo de alto valor SIN bridge:
  - **Editar perfil** (UpdateProfile, campos texto) · **Seguidores/Seguidos**
    (contadores tappables) · **Iconos WMO reales** (mini-parser SVG, no SF Symbols)
  - **Filtros de lista**: DISTANCIA (Geo.haversineKm), toggle FAVORITAS, ORDENAR
    (score/cercanía) — paridad con SchoolFiltersBar.
  - **Comparar escuelas**: long-press selección (máx 3) + barra flotante +
    `CompareView` (columnas lado a lado).
  - **DayDetail** (`DayDetailView` como sheet desde los días del forecast).
  - **Chips de favoritas en Tiempo** (alternar ubicación / escuela favorita).
  - **Badge de no leídas** en la campana · **Donate dialog** (ko-fi) ·
    **JIT provisioning** al login (getMyProfile en RootView).
  - **Submissions**: tipo de roca + 'Motivo:' del rechazo.
  - **Diario** (`JournalView`: stats bloques/escuelas/grado, + AÑADIR BLOQUE,
    borrar) + **GradeColor.swift** (color por grado, espejo exacto).
  - Patrón siempre el mismo: exponer use case (ya en `shared`) en
    `IosDependencyContainer` + SwiftUI. Verificado vía CI (compila).
- **Pendiente** (necesita bridges nativos, próximas sesiones con Mac): mapas
  (MapLibre: mapa de escuela, SchoolsMapPanel, proponer/topo), subir fotos
  (Storage: foto perfil/notas), chat (Firestore), push. Sin bridge pero pendiente:
  SavedSchools/offline, banners de forecast stale, admin queue, onboarding,
  Sign in with Apple, stats mensuales (si el use case existe en shared).

### Sesión 2026-06-15 (5) — iOS CI: .ipa sin firmar para Sideloadly (sin Mac)

- **Objetivo**: poder probar la app iOS en el iPhone de Rodrigo **sin Mac** y
  sin depender de MacInCloud (lento). Sideloadly (en Windows) instala un `.ipa`,
  pero NO lo compila → lo compila GitHub Actions en un runner macOS.
- **`.github/workflows/ios-ci.yml`** (nuevo): runner `macos-14`, JDK 21,
  `brew install xcodegen`, `xcodegen generate`, `xcodebuild -sdk iphoneos
  CODE_SIGNING_ALLOWED=NO build`, empaqueta el `.app` en `.ipa` sin firmar
  (truco `Payload/` + zip) y lo sube como artifact **ios-app-unsigned-ipa**.
  Caché de `~/.konan` para no re-descargar el toolchain de Kotlin/Native.
  Triggers: push a main/claude/** + `workflow_dispatch` (lanzar a mano).
- **Secret PENDIENTE de crear por Rodrigo**: `GOOGLE_SERVICE_INFO_PLIST` con el
  contenido del `GoogleService-Info.plist` real (Firebase climbingteams). Sin
  él, el `.ipa` compila pero usa un plist dummy → la app arranca pero el login
  de Google falla (igual filosofía que `GOOGLE_SERVICES_JSON` en Android CI).
- **Flujo para Rodrigo**: Actions → run de iOS CI → Artifacts →
  `ios-app-unsigned-ipa` → descargar → Sideloadly → instalar en iPhone con su
  Apple ID (caduca a 7 días con Apple ID gratuito; al publicar, Apple Developer
  $99/año y deja de caducar).
- **OJO primera ejecución lenta** (~hasta 90 min: SKIE compila el framework KMP
  desde cero). Runs siguientes mucho más rápidos por la caché.

### Sesión 2026-06-15 (4) — iOS: login obligatorio al arrancar (paridad con Android)

- **Problema detectado**: en iOS el login era opcional (se abría como sheet al
  tocar el icono de persona). En Android `AppRoot.kt` mete TODA la app detrás
  del login — sin sesión solo se ve `LoginScreen`, no hay modo invitado.
- **Fix — gate de login al arrancar** (espejo de `AppRoot.kt`):
  - `RootView.swift` (nuevo): observa `SessionStore` (movido a nivel de app en
    `MeteoMontanaApp` con `@StateObject` + `.environmentObject`). Sin sesión →
    `LoginView`; con sesión → `MainTabView`.
  - `LoginView.swift` reescrito como **pantalla de marca a pantalla completa**
    (logo `logo_cumbre` en círculo + "CUMBRE" serif 36 tracking 4 + "MeteoMontana"
    + "Tiempo para escalar", botón oscuro "Continuar con Google" con la G a
    color, legal TÉRMINOS/PRIVACIDAD abajo). Sin botón "Cerrar" — es el gate.
  - `AccountView.swift` (nuevo): perfil + CERRAR SESIÓN; se abre desde el icono
    de persona del header de la lista (`.sheet`). Lee nombre/email del
    `authBridge` directo (evita el problema de `@EnvironmentObject` en sheets).
- **Asset**: `logo_cumbre.png` copiado de Android a
  `iosApp/iosApp/Assets.xcassets/logo_cumbre.imageset/` (XcodeGen lo recoge solo
  por estar bajo `sources: iosApp`).
- **Pendiente Mac**: `xcodegen generate` (hay ficheros .swift y .xcassets
  nuevos) antes de compilar. Validar firmas SKIE no aplica aquí (es SwiftUI
  puro + bridges ya existentes). Sign in with Apple aún pendiente.

### Sesión 2026-06-15 (3) — iOS: bridge AuthService (login Google) + sesión

- **AuthService bridge** (mismo patrón que ubicación):
  - `shared/src/iosMain/.../data/auth/IosAuthService.kt`: interfaz
    `IosAuthBridge` (currentUid/Email/DisplayName, currentIdToken(cb),
    signOut(cb), observeAuthState(cb)) + `IosAuthService` que mantiene el
    `StateFlow<AuthState>` y traduce callbacks a suspend.
  - `iosApp/iosApp/DI/AuthBridge.swift`: impl Swift con FirebaseAuth.
    `signInWithGoogle()` (no es parte del port, lo dispara la UI) usa el SDK
    **GoogleSignIn** + `GoogleAuthProvider.credential` + `Auth.signIn`.
  - `project.yml`: paquete SPM `GoogleSignIn-iOS` + `CFBundleURLTypes` con el
    REVERSED_CLIENT_ID del GoogleService-Info.plist.
  - `MeteoMontanaApp`: `.onOpenURL { GIDSignIn.sharedInstance.handle($0) }`.
- **LoginView** (`Screens/LoginView.swift`): pantalla de cuenta. Sin sesión →
  "CONTINUAR CON GOOGLE"; con sesión → nombre/email + "CERRAR SESIÓN".
  `SessionStore` observa FirebaseAuth nativo para el gating de UI (robusto, sin
  depender del mapeo de enums SKIE). Accesible desde el icono "person" de
  `SchoolListView` (ahora botón → sheet).
- **`authService` cableado al `IosDependencyContainer`** → el tokenProvider del
  HttpClient ya manda el ID token; los endpoints autenticados funcionarán en
  cuanto haya sesión.
- Build iOS OK (GoogleSignIn resuelto por SPM) + app arranca sin crash con el
  listener de auth. ⚠️ **Login interactivo NO probado** (requiere tap + cuenta
  Google real). Rama `claude/ios-location-bridge`.

### Sesión 2026-06-15 (2) — iOS: primer bridge `suspend` (ubicación) + tab Tiempo

- **Hito**: el **patrón bridge** para implementar ports `suspend` de Kotlin
  desde Swift queda VALIDADO end-to-end con el primero: `LocationProvider`.
  - `shared/src/iosMain/.../data/location/IosLocationProvider.kt`: define la
    interfaz `IosLocationBridge` (callbacks, sin suspend) que implementa Swift,
    y `IosLocationProvider` que la envuelve con `suspendCancellableCoroutine`
    para cumplir el port `LocationProvider` (suspend). Equivalente iOS del
    `AndroidLocationProvider` (FusedLocation).
  - `iosApp/iosApp/DI/LocationBridge.swift`: impl Swift con `CLLocationManager`
    (NSObject + CLLocationManagerDelegate). `hasPermission()`, `current(cb)` y
    `requestPermission()`. Conformance Swift→protocolo Kotlin CONFIRMADA
    (compila + linka + corre en simulador).
  - `AppDependencies.swift` crea el `LocationBridge` y lo pasa al
    `IosDependencyContainer` (nuevo param `locationProvider`).
- **Tab Tiempo cableado**: `WeatherView` reescrito con `WeatherViewModel`
  (Swift) que usa `bridge.hasPermission()` + `container.locationProvider.current()`
  (async vía SKIE) + `GetForecastByLocationUseCase` compartido. Estado
  needPermission con botón "ACTIVAR UBICACIÓN". Verificado en simulador:
  muestra forecast real en tu ubicación (Madrid en la prueba).
- **Refactor**: extraído `ForecastBodyView` de `SchoolDetailView.swift`
  (reutilizado por detalle de escuela y tab Tiempo; `directions` opcional).
- **Receta para los demás bridges** (FileReader/Auth/Chat/Storage): copiar el
  par `IosXxxBridge` (Kotlin iosMain) + impl Swift + wire en AppDependencies.
- Android sigue verde (103 tests, `:app:testDebugUnitTest`). Build iOS OK
  (`linkDebugFrameworkIosSimulatorArm64` + xcodebuild). Rama
  `claude/ios-location-bridge`.

### Sesión 2026-06-15 — 🎉 PRIMERA SESIÓN EN MAC: la app iOS arranca

- **Hito**: la app iOS **compila, instala y arranca en el simulador** mostrando
  las **191 escuelas reales** del backend de Railway. Validación end-to-end de
  toda la arquitectura KMP escrita a ciegas en Windows: Ktor desde iOS + SKIE
  (suspend→async) + DI Kotlin (`IosDependencyContainer`) + SQLDelight nativo.
  La pantalla es SwiftUI pelado (sin diseño Cumbre todavía) — es la plantilla
  MVP `SchoolListView`; el resto de pantallas se replican en próximas sesiones.
- **Errores de Fase E2 resueltos** (los que Windows no podía ver):
  - **ABI klib iOS**: Ktor 3.1.3→**3.0.3** y kotlinx-serialization 1.8.1→**1.7.3**
    (las versiones nuevas exigen Kotlin 2.1.x; el proyecto va con 2.0.21).
    Android sigue verde: 103 tests OK con estas versiones (`testDebugUnitTest`).
  - `gradlew` sin bit de ejecución (se perdió al traer el repo de Windows) →
    `chmod +x`.
  - `iosApp/project.yml`: faltaba `PRODUCT_NAME` (producto `.app` sin nombre →
    "Multiple commands produce"); faltaba `-lsqlite3` en `OTHER_LDFLAGS` (el
    driver nativo SQLDelight/sqliter referencia símbolos `sqlite3_*`).
  - Las firmas SKIE de `SchoolListView` compilaron sin tocar nada.
- **Fase D hecha**: app iOS registrada en Firebase `climbingteams`,
  `GoogleService-Info.plist` en `iosApp/iosApp/` (añadido a `.gitignore`).
- **Herramientas Mac**: Xcode 26.5, Java 21 (Homebrew). `xcodegen` instalado en
  `~/bin/xcodegen` (plan B sin Homebrew: `/opt/homebrew` es del usuario `temp`
  y `sudo` no funciona en esta cuenta). `MeteoMontana.xcodeproj` se regenera con
  `xcodegen generate` (no se versiona).
- **Rareza del entorno**: `xcrun simctl launch` se cuelga en este Mac (macOS
  26.3 / Xcode 26.5; afecta también a apps del sistema, NO a la nuestra). Para
  arrancar la app iOS: **tocar el icono a mano** en la ventana del Simulator.
- Flujo build iOS: `xcodegen generate` → `xcodebuild ... -sdk iphonesimulator
  -destination 'id=25D70E56-...' CODE_SIGNING_ALLOWED=NO build` →
  `xcrun simctl install booted <ruta>.app` → tocar icono.
- **OJO lentitud**: en este Mac `simctl install` tarda ~1-2 min y `launch`
  hasta ~3 min (NO está colgado, es lento por macOS/Xcode 26 nuevos). Usar
  timeouts largos (≥240s). Tras instalar build nueva: `simctl terminate` antes
  de `launch` o reusa el proceso viejo.
- **Pantallas iOS hechas** (`iosApp/iosApp/`): `SchoolListView` (lista + score
  badge coloreado + buscador + filtros estilo/roca, orden por score),
  `SchoolDetailView` (forecast: hero score, condiciones, ventana óptima, mejor
  día, heatmap horas), `Theme/CumbreTheme.swift` (tokens Cumbre + scoreColor +
  eyebrow). DI: `IosDependencyContainer` expone getSchools/getSchoolById/
  searchSchools/getForecast/getTodayScores. Para añadir un use case nuevo a
  iOS, exponerlo ahí.
- `xcodegen` en `~/bin/xcodegen`. Si borras un .swift, REGENERA el proyecto
  (`xcodegen generate`) antes de compilar o xcodebuild busca el fichero viejo.
- **UI iOS clavada a Android (2026-06-15, 2ª tanda)**: `SchoolListView` réplica
  fiel de SchoolListScreen.kt (fila iconos, header "Escuelas"+count+"+Enviar
  escuela", banner ☕, buscador, chips ESTILO/ROCA, fila rica: badge tintado +
  rank + nombre serif + estrella + subtítulo + heatmap 10 celdas + ●SECA/MOJADA).
  `SchoolDetailView` réplica de ForecastBody.kt (veredicto SÍ/NO, ÍNDICE/100,
  banda de roca, desglose factores, tiempo actual, 16h con icono WMO≈SFSymbol,
  8 celdas de condiciones, 7 días, mejor día).
- **Fuentes bundladas** en `iosApp/iosApp/Fonts/` (Source Serif 4 + JetBrains
  Mono, registradas en UIAppFonts del project.yml). Helpers `Cumbre.serif()` /
  `Cumbre.mono()`. La sans del cuerpo es la del sistema (≈Inter). Si añades una
  pantalla, usa esos helpers para clavar la tipografía.
- **PENDIENTE iOS para paridad total**: modo oscuro (toggle luna no funciona),
  mapa de escuela (MapLibre iOS), filtros completos (distancia/favoritos/orden),
  iconos WMO SVG reales, y todo lo privado (login/ubicación/fotos → ports bridge).

### Sesión 2026-06-13 (2) — preparación pre-Mac (KMP Fases A/B/C-base)

- Se ejecutó el **PLAN DE ATAQUE PRE-MAC** de `KMP_MIGRATION.md` (leer su
  `📍 ESTADO ACTUAL` para el estado al 100%). Objetivo: dejar listo lo máximo
  posible en Windows para que el Mac sea solo "rematar".
- **Fase A** (lógica compartida, verificada): `Geo.haversineKm` unificado en
  `shared/commonMain` (eliminó 3 copias); `LocationProvider` pasó a interfaz
  en `commonMain` + `AndroidLocationProvider` en `app/`; la lógica del widget
  Favoritas salió a `GetFavoritesWidgetDataUseCase` en `shared`. Barridos:
  cero `android./java.` en `commonMain`.
- **Fase B**: `iosMain` Kotlin sin UI — `DatabaseFactory` + `IosNetworkMonitor`.
- **Fase C (base)**: SKIE (plugin Gradle) + framework `Shared` + motor
  `ktor-client-darwin` + `IosDependencyContainer` (DI en Kotlin). `iosApp/`
  con XcodeGen, Firebase SPM, app entry, DI Swift y pantalla MVP `SchoolListView`.
- Decisión clave: los ports `suspend` (location/files/Firebase) NO se escriben
  en Swift a ciegas — patrón *bridge*, a hacer con Mac (documentado en la Guía
  iOS del `KMP_MIGRATION.md`).
- Todo en `main`. Builds de Android verdes (SKIE no rompe nada).

### Sesión 2026-06-13 — fix crash widget + rediseño a tarjetas + firma CI

- **Fix crash widget Favoritas**: faltaba aplicar el plugin
  `kotlin.plugin.serialization` en `:app`. El `@Serializable WidgetState`
  compilaba pero no generaba serializer → en runtime lanzaba
  `SerializationException` y Glance pintaba "no se puede mostrar el
  contenido". Una dependencia de kotlinx-serialization NO basta: el plugin
  es lo que genera el código del serializer.
- **Rediseño del widget a tarjetas** (`ui/widget/FavoritesWidget.kt`): cada
  favorita es una tarjeta idéntica (bloque de score coloreado + nombre serif
  + línea "KM · estilo · roca" + heatmap horario a todo el ancho), ordenadas
  por score. Antes había una escuela "hero" distinta del resto (al usuario
  no le gustaba esa asimetría). Distancia por Haversine desde
  `LocationProvider.current()` a las coords del catálogo cacheado
  (SQLDelight); estilo/roca también del catálogo. Sin red extra (se quitó la
  llamada de forecast que tenía la hero).
- **Adaptable**: `SizeMode.Exact` + `LocalSize` → muestra las tarjetas que
  caben, resto "+N MÁS EN LA APP". Tamaño por defecto subido a 4x4.
- **Esquinas redondeadas**: drawables `widget_bg.xml` y `widget_card.xml`
  (+ colores en `values/` y `values-night/`) porque
  `GlanceModifier.cornerRadius()` solo redondea en Android 12+ (el móvil de
  pruebas es 11). El cuadrito del score sí usa cornerRadius → sale cuadrado
  en 11, redondeado en 12+.
- **`fix(ci)`**: nuevo step que restaura `debug.keystore` desde
  `DEBUG_KEYSTORE_BASE64` para que el APK de Actions tenga firma estable y
  Google Sign-In no falle con error 10. Ver punto 4 de "Workflow". Secret
  PENDIENTE de crear por Rodrigo (opcional, solo para APKs de CI).
- Build local: `JAVA_HOME` al JDK de Android Studio
  (`C:\Program Files\Android\Android Studio\jbr`), `gradlew :app:assembleDebug`,
  instalar con `adb install -r` (adb en
  `%LOCALAPPDATA%\Android\Sdk\platform-tools`). Tras instalar, quitar y
  re-añadir el widget para que el launcher coja la versión nueva.
- Mergeado a `main` directo (sin PR) a petición del usuario.

### Sesión 2026-06-12 (4) — bloque backend aprobado (ETag, secado, alerta óptima, fotos en notas)

- **ETag/304 en el catálogo**: el backend manda `ETag` en `GET /api/schools`
  y la app (CatalogEtagStore + GetSchoolCatalogUseCase) manda If-None-Match;
  con 304 la lista se sirve desde la caché SQLDelight sin re-descargar.
- **Secado de roca**: el forecast expone `current.drying` (horas estimadas +
  mensaje); sublínea bajo "● ROCA SECA/HÚMEDA" del hero ("Seca en ~12 h",
  "Arenisca: evita escalar 48 h tras lluvia").
- **Alerta "ventana óptima hoy"**: toggle + umbral (60/70/80) en la pantalla
  de Alerta de tiempo; el backend (V24) evalúa las favoritas entre 7-11h
  Madrid y manda push (máx 1/día) si la mejor franja supera el umbral.
- **Fotos en notas** (V23): botón 📷 FOTO en el composer de notas, subida a
  Firebase Storage (`note-photos/`), thumbnail en la nota y dialog a
  pantalla completa con foto + texto. Sin red, el outbox encola solo texto.
- Migraciones Flyway del backend hasta **V24**.

### Sesión 2026-06-12 (3) — CI, Crashlytics, widget "Favoritas hoy"

- **CI GitHub Actions** (`.github/workflows/android-ci.yml`): compila debug
  y corre los tests unitarios en cada push (main y claude/**). Genera un
  google-services.json dummy porque el real está fuera de git.
- **Crashlytics** integrado (plugin + dep via BoM, recolección automática).
- **Widget de home "Favoritas hoy"** (Glance, `ui/widget/FavoritesWidget.kt`):
  score de hoy de las favoritas, refresh horario + botón ↻, fallback al
  último estado cacheado, tap → detalle de la escuela via deep link.
- **Consistencia UI**: Warn→tertiary en el tema (banner stale ok en dark),
  REINTENTAR en el error del detalle, Spacing en SchoolDetailScreen,
  contentDescription en 4 flechas de volver.
- **Pendiente bloque backend aprobado** (la sesión remota no tenía acceso a
  MeteoMontanaAPI): secado de roca, alerta ventana óptima, fotos en notas,
  ETag/304. Detalle completo en KMP_MIGRATION.md → "Próximo paso".
  **La próxima sesión web debe crearse con LOS DOS repos.**

### Sesión 2026-06-11 — alerta del finde → alerta de tiempo

- La "alerta del finde" pasa a llamarse **"Alerta de tiempo"** y el usuario
  elige qué días comparar: chips con los próximos 7 días desde hoy ("J 11",
  "V 12"…). Se guarda el día de la semana (V22: `alert_days` CSV ISO 1-7,
  default `5,6,7`), así la alerta se repite cada semana con esos días. El
  job evalúa la próxima ocurrencia de cada día elegido dentro de la ventana
  de 7 días; el push usa etiquetas reales L/M/X/J/V/S/D y "llueve X de N
  días". Apps antiguas sin `alertDays` siguen con vie/sáb/dom.

### Sesión 2026-06-12 (2) — feedback de pruebas resuelto

- **Alerta del finde**: la pantalla se cierra sola tras guardar; nuevo modo
  **POR CERCANÍA** (V21: mode/radius_km/user_lat/user_lon) — radio 25-200 km
  desde la posición al guardar, el job evalúa hasta 12 escuelas del radio y
  compara las 3 mejores.
- **Encoding ?? arreglado** (V20): el import inicial a Railway corrompió los
  acentos (Alcañiz→Alca??z). V20 regenera name/location/region/style/rock_type
  de las 191 escuelas desde data/escuelas.json de la PWA. Verificado en prod.
- **Notificaciones con marca**: ic_notification (montaña monocroma) teñido
  Terra + BigTextStyle; la alerta del finde pasa a push data-only y el tap
  abre CompareScreen (deep link targetType=compare con ids CSV).
- **Punto azul de ubicación en todos los mapas**: userDotBitmap compartido +
  rememberUserLocation(); SchoolMap (detalle) y FullScreenMapDialog lo pintan.

### Sesión 2026-06-12 — alerta del finde + comparador + desglose + onboarding

- **Alerta del finde** (backend V18 + Android): el usuario elige hasta 3
  escuelas, día y hora del aviso. `WeekendAlertScheduler` (@Scheduled cada
  hora, Europe/Madrid) evalúa vie/sáb/dom vía `WeekendAlertUseCase`: media
  de los 3 días por escuela, desglose V/S/D y lluvia (días con ≥1mm + máx
  acumulado). Push FCM con ranking 🥇🥈🥉. Endpoints `GET/PUT
  /api/me/weekend-alert`. Pantalla `WeekendAlertScreen` desde el perfil
  ("⛰ Alerta del finde"): switch, chips L-D y hora, picker de escuelas
  (busca en el catálogo cacheado local).
- **Comparador de escuelas**: long-press en cards de la lista selecciona
  (máx 3, borde terra); barra inferior "N SELECCIONADAS · COMPARAR" →
  `CompareScreen` (ruta `compare/{ids}`) con columnas lado a lado: score
  hero, temp/hum/viento, roca seca, ventana óptima, mejor día, mini-heatmap
  16h y CÓMO LLEGAR.
- **Desglose del score al tocarlo**: el índice del hero dice "VER DESGLOSE ▾"
  y al tocarlo abre el acordeón de factores (estado compartido).
- **Onboarding primera apertura**: 2 pasos (índice 0-100 + por qué la
  ubicación); el permiso se pide al terminar. Flag en SharedPreferences.

### Sesión 2026-06-11 (3) — refactor admin + favoritos lista + share imagen

- **AdminScreen.kt troceado** (56 KB → 6 ficheros): `AdminScreen` (tabs+enum),
  `PropuestasTab`, `ContributionCard`, `SubmissionCard`, `GestionarTab`,
  `AdminTabsMisc` (Stats/Activity/Push). Funciones cross-file → `internal`.
- **`MapViewLifecycleEffect`** (ui/components/): único punto de verdad del
  lifecycle de MapView. Sustituye las 4 copias (SchoolsMapPanel, SchoolMap,
  FullScreenMapDialog, AdminScreen).
- **Favorito desde la lista**: estrella tocable en `SchoolListItem` con update
  optimista (`toggleFavorite` en el VM, revierte si la red falla).
- **`animateItem()`** en la lista: las cards se deslizan al re-ordenarse.
- **Estado vacío con "QUITAR FILTROS"** (`clearFilters()` resetea a sin límite).
- **Compartir como imagen**: `ui/share/ShareConditionsImage.kt` genera una
  card Cumbre (score hero + datos + heatmap 16h) en PNG y la comparte via
  FileProvider (`${applicationId}.fileprovider`, `res/xml/file_paths.xml`).
  Fallback a texto si no hay forecast.
- **a11y**: `contentDescription` en botones volver (7 pantallas) y estrella
  de favorito del detalle.

### Sesión 2026-06-11 (2) — UX: refresh, mapa, forecast stale, skeleton

- **Pull-to-refresh** en la lista (`PullToRefreshBox` M3) + botón REINTENTAR
  en el estado de error. `SchoolListViewModel.refresh()` recarga catálogo,
  scores y favoritos.
- **Scores para todas las escuelas** en lotes de 50 encadenados (antes solo
  las primeras 50). Solo se piden los que faltan — teclear en el buscador ya
  no dispara llamadas ni re-sorts (era la causa del "salto" al escribir).
- **Mapa de la lista**: punto azul con la ubicación del usuario, centrado en
  el usuario al abrir (zoom 8), nombre de la escuela bajo el pin solo con
  zoom ≥ 8.5 (si no se solapan), fit-bounds solo cuando cambia la lista
  filtrada (no cuando llegan scores), tiles oscuros CartoDB en tema oscuro.
- **Forecast stale-while-revalidate**: `SavedForecast` ahora cachea el
  forecast de CUALQUIER escuela visitada. Si la red falla se pinta el último
  conocido con banner ámbar "PREVISIÓN DE HACE Xh + REINTENTAR".
- **Skeleton rows** en la carga inicial de la lista (en vez de spinner).

### Sesión 2026-06-11 — rendimiento percibido (paridad con la PWA)

- **Cargas en paralelo**: Profile, SchoolDetail, Admin y PublicProfile lanzaban
  sus llamadas al backend en serie (~350 ms de RTT contra Railway cada una).
  Ahora las independientes van con `async` dentro de `coroutineScope`.
- **Catálogo de escuelas con stale-while-revalidate**: tabla `CachedSchool`
  (SQLDelight, BD renombrada a v3) + `CachedSchoolsRepository`. La lista se
  pinta al instante desde caché al abrir y se refresca desde red en segundo
  plano. Los filtros (estilo/roca/distancia/texto) se aplican **en local**
  (misma semántica que el backend: equalsIgnoreCase + haversine) → tocar un
  chip de filtro ya no hace ninguna llamada de red.
- Tests de `SchoolListViewModel` reescritos al nuevo flujo; fix de un test
  de `SchoolDetailViewModel` que venía roto (mock relaxed devolvía snapshot
  offline fantasma).

### Sesión 2026-06-10 (tarde) — sectores, stats al back, push avatar, R8

- **Relación piedra→sector**: `school_blocks.sector_block_id` (V16, FK self-ref
  a la ZONE de la escuela, nullable). Al proponer piedra nueva hay dropdown
  "SECTOR (opcional)"; piedras existentes sin sector tienen "+ ASIGNAR SECTOR"
  en `BlockDetailDialog` → contribución tipo **ASSIGN_SECTOR** (V17 amplió el
  check de tipos) que el admin aprueba. Badge verde SECTOR en el detalle si la
  piedra lo tiene. En el journal (`AddBlockSheet`) los sectores catalogados
  salen como sugerencia y filtran las vías propuestas.
- **#6 journal**: el campo BLOQUE/VÍA sugiere vías reales (grado + tipo) y al
  elegir una autocompleta el grado.
- **#8 stats mensuales**: `GET /api/schools/{id}/monthly-stats` en el back
  (port de ClimbScore.kt, Caffeine propia TTL 30 días). La app ya no llama a
  archive-api.open-meteo.com; `OpenMeteoArchive.kt` y `ClimbScore.kt` borrados.
- **#9 topo aspect real**: `topoAspectRatio(w,h)` (clamp 0.55–2.2) sustituye
  al 4:3 fijo en `TopoPhotoCanvas` y `ContributionTopoDialog` — misma fórmula
  en editor y visores para que las líneas coincidan.
- **#2 push avatar**: pushes sociales data-only (con bloque `notification`,
  Android en background no ejecuta `onMessageReceived`) + `avatarUrl` en el
  payload; `PushService` lo pinta como largeIcon circular.
- **#1 fluidez**: `isMinifyEnabled` + `isShrinkResources` en release con keep
  rules (kotlinx-serialization, MapLibre JNI, SQLDelight). **Pendiente probar
  el APK release minificado en todas las pantallas** (primer build con R8).
- **Fix auth 403**: el plugin Auth de Ktor cacheaba el token Firebase y solo
  refrescaba ante 401 (Spring devuelve 403) → tras ~1h todo daba 403. Plugin
  propio en `ApiHttpClient` que pide token fresco al provider en cada request.
- Migraciones Flyway aplicadas hasta **V17**.

### Gestión completa de bloques desde el admin (sesión anterior)

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
- Nada bloqueado en Android: SECTOR/CORREGIR POSICIÓN, búsqueda de usuarios,
  follows, diario, foto de perfil y build R8 están completos (2026-06-10).
- Fase 3 KMP (app iOS) — EN PAUSA, requiere Mac. Ver `KMP_MIGRATION.md`.
- Modo offline con Room (decisión tomada, sin fecha).

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
