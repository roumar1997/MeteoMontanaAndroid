# Plan de migración a Kotlin Multiplatform (KMP) — Android + iOS

> Decisión tomada (2026-06-06): la app MeteoMontana será multiplataforma usando
> **Kotlin Multiplatform (KMP)**. Compartiremos `domain/` y `data/` en Kotlin
> entre Android e iOS. La UI Android sigue siendo Jetpack Compose. La UI iOS
> será **SwiftUI** consumiendo el módulo compartido.

---

## 📍 ESTADO ACTUAL DE LA MIGRACIÓN

> **Esta sección se actualiza al final de cada sesión.** Una sesión nueva
> debe leer SOLO esta sección y ya sabe por dónde seguir.

**Última actualización:** 2026-06-09 (UX polish, contadores follow, email Resend, push deep link, theme toggle, outbox, tiles offline, SQLDelight). Próximas: iOS port (cuando llegue Mac) + bloque #6 (vías en vez de bloques) + #8 (stats cacheadas backend) + #9 (foto crop).

**Modelo recomendado:** Sonnet para Fases 1.x y 2.x (refactor mecánico, plan ya escrito). Opus solo para decisiones de arquitectura ambiguas o bugs sin diagnóstico claro.

**Progreso global:**

- [x] **Fase 0** — Planificación (documento creado, decisiones tomadas).
- [x] **Fase 1.0** — Tests como red de seguridad (2 sesiones). ✅
  - [x] Sesión 1/2: 45 tests de funciones puras (Haversine, parseLatLonPaste,
    toBloquesJson, gradeStyle, LineStroke). Todos verdes. ✅
  - [x] Sesión 2/2: 33 tests de ViewModels (12 SchoolList + 12 SchoolDetail
    + 9 Admin). Todos verdes. ✅ Total Fase 1.0: 78 tests.
- [x] **Fase 1** — Refactor Clean Android. ✅ COMPLETA (1.1→1.6 todas cerradas).
  - [x] 1.1 — Use cases en `domain/usecase/`. ✅ Hecho schools, forecast,
    blocks, contributions, notes, favorites, notifications, profile y
    admin (9 use cases). Los 3 ViewModels (`SchoolListViewModel`,
    `SchoolDetailViewModel`, `AdminViewModel`) ya no dependen de
    `SchoolApi`/`AdminApi` directos (solo `AdminViewModel` mantiene
    `SchoolApi` para `getSchools()` del tab GESTIONAR — pendiente
    extraer en 1.3).
  - [x] 1.2 — Modelos de dominio puros (DTOs fuera de UiState). ✅
    16 modelos en `domain/model/`. Todos los `UiState` y consumidores
    de pantalla usan modelos de dominio. Solo `CreateBlockRequest`,
    `CreateBlockLineRequest`, `ContributionRequest`,
    `SubmitSchoolRequest`, `CreateJournalRequest`, `CreateNoteRequest`,
    `UpdateProfileRequest`, `FcmTokenRequest`, `AdminPushRequest`,
    `RejectReason` se mantienen como input DTOs (anotaciones Moshi;
    se cambian a Kotlinx Serialization en Fase 2).
  - [x] 1.3 — Partir `SchoolApi` por bounded context. ✅ 10 APIs nuevas
    (ForecastApi, BlockApi, NoteApi, ContributionApi, ProfileApi,
    FavoritesApi, JournalApi, SubmissionApi, SocialApi, NotificationApi).
    SchoolApi queda con 3 métodos (getSchools, searchSchools, getSchoolById).
    78 tests siguen verdes.
  - [x] 1.4 — Sacar dibujo del topo del Composable (instrucciones `DrawOp`). ✅
    DrawOp sealed class + TopoLineData + renderTopo() en domain/util/,
    sin imports Android. TopoPhotoCanvas y ContributionTopoDialog usan
    renderTopo() + traductor drawOp() Android-only. 7 tests nuevos → 86 total.
  - [x] 1.5 — Abstracciones de Firebase (`PhotoUploader`, `AuthService`, `ChatService`). ✅
    Interfaces en `domain/port/`. Implementaciones: `FirebaseStoragePhotoUploader`,
    `FirebaseAuthService`, `FirebaseChatService` en `data/`. Bindings en
    `RepositoryModule`. ChatViewModel, ChatListViewModel, AuthInterceptor y
    SchoolDetailViewModel migrados a inyectar las interfaces. 86 tests verdes.
  - [x] 1.6 — Wrappers de tipos Android (`FileRef` en vez de `Uri`). ✅
    FileRef (value class, domain/model/), FileReader (port), AndroidFileReader (data/).
    SchoolDetailViewModel ya no importa android.net.Uri ni Context. 86 tests verdes.
- [x] **Fase 2** — Crear módulo `shared` KMP (2-3 sesiones).
  - [x] 2.1 — Setup KMP + targets Android/iOS. ✅ Módulo `shared/` creado con
    `kotlin-multiplatform` + `android-library`. Targets: `androidTarget`,
    `iosX64`, `iosArm64`, `iosSimulatorArm64` (iOS deshabilitado en Windows,
    compila en Mac). Domain models + ports + repository + util movidos a
    `commonMain`. App consume `project(":shared")`. 86 tests verdes.
  - [x] 2.2 — Migrar `domain/usecase/` a `commonMain`. ✅ 22 use cases movidos.
    Interfaces de repositorio en commonMain: ForecastRepository, BlockRepository,
    NoteRepository, FavoritesRepository, ProfileRepository, NotificationsRepository,
    AdminRepository. Implementaciones Retrofit en app/data/repository/. Use cases
    sin @Inject (plain constructors), provistos por UseCasesModule Hilt. 86 tests verdes.
    Quedan en app/ (params DTO): CreateBlockUseCase, UpdateBlockUseCase, SubmitContributionUseCase.
  - [x] 2.3 — Migrar `data/` a `commonMain` con Ktor + Kotlinx Serialization. ✅
    DTOs con @Serializable + toDomain() en Mappings.kt. 12 KtorXxxApi classes.
    8 KtorXxxRepository classes + KtorContributionRepository. GetBlockUseCase añadido.
    CreateBlockUseCase, UpdateBlockUseCase, SubmitContributionUseCase movidos a commonMain.
    Retrofit + Moshi + OkHttp eliminados del proyecto. AuthInterceptor eliminado.
    NetworkModule reescrito con buildApiHttpClient(). RepositoryModule usa @Provides.
    ErrorMessage.kt migrado de HttpException (Retrofit) a ClientRequestException (Ktor).
    AdminViewModel.loadAllSchools() ahora usa GetSchoolsUseCase → List<School>.
    86 tests verdes.
  - [x] 2.4 — Implementaciones Firebase movidas a `shared/androidMain/`. ✅
    FirebaseAuthService, FirebaseChatService, FirebaseStoragePhotoUploader,
    AndroidFileReader en `shared/src/androidMain/`. Sin @Inject (plain constructors).
    RepositoryModule convertido a `object` con @Provides para los 4 servicios.
    Firebase deps añadidas a `shared/build.gradle.kts` via `add("androidMainImplementation", ...)`.
    `.gitignore` actualizado para excluir `shared/build/`. 86 tests verdes. assembleDebug OK.
  - [x] 2.5 — Adaptar `androidApp` para consumir `shared`. ✅
    Todos los ViewModels de `app/` dependen SOLO de use cases de `shared/commonMain`.
    Ningún VM importa KtorXxxApi directamente. SearchSchoolsUseCase añadido.
    SchoolRepository + KtorSchoolRepository implementan searchSchools().
    103 tests verdes. assembleDebug OK. Mergeado y pusheado a main.
- [ ] **Fase 3** — App iOS en SwiftUI. ← **SIGUIENTE**
  - [ ] 3.1 — Crear `iosApp/` (Xcode project stub + xcconfig para usar el framework KMP).
  - [ ] 3.2 — Conectar shared framework en SwiftUI: injección de repositorios vía DI iOS (Swinject o manual).
  - [ ] 3.3 — Pantallas MVP: lista de escuelas + detalle + forecast.
- [ ] **iOS .swift en paralelo** — durante Fases 1 y 2.
  - [ ] Estructura `iosApp/iosApp.xcodeproj` (con stubs sin compilar).
  - [ ] Cada sesión que refactorice algo Android → deja .swift equivalente.
- [ ] **Fase 3** — App iOS en SwiftUI (requiere Mac, 5-8 sesiones).
  - [ ] Apertura proyecto Xcode, ajustes visuales.
  - [ ] TestFlight.

**👉 Siguiente paso concreto:** ver final del documento ("Próximo paso").

> ⏸️ **FASE 3 EN PAUSA** — requiere Mac. Mientras tanto el usuario realiza
> mejoras visuales y técnicas en la app Android. Al abrir sesión nueva,
> preguntar al usuario qué quiere mejorar hoy, NO continuar la migración KMP.

**Lo que necesita el usuario aportar pronto:**
- Antes de fin de Fase 2: registrar app iOS en consola Firebase y descargar
  `GoogleService-Info.plist` (5 minutos desde Windows, navegador).
- Antes de Fase 3: Mac mini M4 (~700 €) + cuenta Apple Developer ($99/año).

---

---

## Por qué KMP y no otra opción

Auditoría real del front actual:

| Opción | Esfuerzo | Reutilización | Calidad final |
|---|---|---|---|
| **A.** Reescribir nativo Swift/SwiftUI | Alto (≈ otra vez Android) | Solo backend | Excelente, dos códigos paralelos |
| **B. KMP (elegida)** | Medio-alto | Backend + domain + data | Excelente, lógica única, UI nativa cada plataforma |
| C. Compose Multiplatform | Bajo si UI fuera Compose puro | Casi todo | **No viable hoy**: usamos MapLibre Android, `android.graphics.Canvas` en topo, Coil, Firebase con CredentialManager — todos bloqueantes |

**KMP** nos da:
- Un único cálculo de score, una única regla de mojado, un único parser de
  bloques JSON. Si cambia un grado o un umbral, cambia en los dos sitios.
- UI nativa en cada plataforma (Compose en Android, SwiftUI en iOS) →
  rendimiento y look&feel correctos.
- MapLibre iOS y Firebase iOS son SDKs maduros, los integramos en el target iOS.

---

## Estado actual (lo que hay que corregir antes de KMP)

La auditoría detectó estos puntos que **están bien en Android pero impiden
compartir código**:

1. **DTOs en UiState** — `SchoolListViewModel` expone `SchoolScoreDto`
   directamente; `SchoolDetailViewModel.Success` expone `ForecastDto`,
   `NoteDto`, `BlockDto`; `AdminViewModel` expone `ContributionDto` etc.
2. **ViewModels dependen de `SchoolApi` Retrofit directo** — no hay
   `GetSchoolsUseCase`, `GetForecastUseCase`...
3. **`SchoolApi` god interface** — 39 métodos. Hay que partirlo.
4. **`SchoolListViewModel` gordo** — 207 líneas: filtros + sort + GPS +
   Haversine + scores + favoritos + notificaciones.
5. **`android.graphics.Canvas` dentro de Composable** — `ContributionTopoDialog`
   dibuja con Paint Android-only.
6. **Firebase disperso** — `StorageUploadHelper` toca `FirebaseStorage`,
   `ChatRepository` toca `Firestore`, `AuthManager` toca `FirebaseAuth` +
   `CredentialManager`. No hay interfaces para esto.
7. **Madrid hardcoded como fallback** — `SchoolListViewModel:60-61`.
8. **Lógica de Haversine en ViewModel** en lugar de en `domain/util/Geo.kt`.

Estos no son bugs — son acoplamientos que hay que romper.

---

## Fases

### Fase 0 — Preparación (1 sesión)

**Lo que hago yo:**
- Crear este documento.
- Auditar dependencias y elegir versiones KMP (Kotlin 2.x, Ktor 3.x,
  Kotlinx Serialization, Kotlinx Coroutines, SQLDelight si lo añadimos para
  Room equivalente en KMP).
- Documentar el árbol final esperado.

**Lo que necesito de ti:**
- ✅ Confirmar que quieres KMP (ya hecho).
- Decidir el **scope de la primera iOS app**: ¿lanzas con MVP (escuelas + detalle
  + forecast + mapa) o todo (chat, journal, admin, contribuciones)?
- Confirmar si tienes **Mac** o vas a conseguir uno (ver sección "Lo que necesitas físicamente" al final).

---

### Fase 1.0 — Red de seguridad: tests automatizados (2 sesiones)

**ANTES de refactorizar**, escribo tests que validen el comportamiento actual.
Si tras refactorizar todos los tests siguen verdes, sabemos que no se rompió
nada visible. Esto reemplaza el "probar a mano tras cada cambio".

**Alcance de los tests** (realista — no test-everything):

| Tipo | Qué se testea | Esfuerzo |
|---|---|---|
| **Unitarios puros** | Funciones puras: `haversineKm`, `parseLatLonPaste`, `parseBloquesJson` / `toBloquesJson`, `gradeStyle`, `parseLineStroke`, `mapStartType`, `applySortInternal` | Bajo, alta cobertura |
| **ViewModel** | `SchoolListViewModel`: carga inicial, aplicar filtros, sort por score tras llegar scores, fallback Madrid, `onLocationGranted`. Con `kotlinx-coroutines-test` + `turbine` para StateFlow. | Medio |
| **ViewModel** | `SchoolDetailViewModel`: load + forecast error + addBlock + submitBoulderContribution. | Medio |
| **ViewModel** | `AdminViewModel`: load, fetchSchoolBlocks (cache), deleteBlock, updateBlock. | Medio |

**Lo que NO testeo** (pragmático):
- Composables — los tests de Compose son frágiles y lentos, no valen la
  pena para detectar regresiones de refactor interno.
- Retrofit / Ktor — son librerías testeadas. Mockear `SchoolApi` se hace
  inyectándolo, no testeando la red.
- Firebase — pruebas manuales en device.

**Tras Fase 1.0 tendremos**: ~40 tests unitarios y ~15 tests de VM. Cualquier
cambio del refactor que rompa el comportamiento → test rojo en CI.

### Fase 1 — Refactor del Android actual a Clean estricto (3-4 sesiones)

**Aquí NO se introduce KMP todavía**. Solo se reorganiza el código Android
para que sea trivial extraer a `shared/` después. **Cada refactor se valida
con los tests de Fase 1.0**.

#### 1.1 Use cases en `domain/usecase/`

Cada operación de ViewModel pasa a ser un caso de uso aislado:

```
domain/usecase/
  schools/
    GetSchoolsUseCase.kt         // filtros, paginación
    GetSchoolByIdUseCase.kt
    SearchSchoolsUseCase.kt
  forecast/
    GetForecastUseCase.kt
    GetTodayScoresUseCase.kt
  blocks/
    GetBlocksUseCase.kt
    CreateBlockUseCase.kt
    UpdateBlockUseCase.kt
    DeleteBlockUseCase.kt
  contributions/
    SubmitContributionUseCase.kt
    GetMyContributionsUseCase.kt
  admin/
    GetPendingContributionsUseCase.kt
    ApproveContributionUseCase.kt
    RejectContributionUseCase.kt
  social/
    SearchUsersUseCase.kt
    FollowUserUseCase.kt
  geo/
    HaversineDistance.kt         // función pura, no use case
```

ViewModels pasan a depender de use cases, no de `SchoolApi`.

#### 1.2 Modelos de dominio puros (DTOs fuera de UiState)

Convertir:
- `SchoolScoreDto` → `domain/model/SchoolScore.kt`
- `ForecastDto` → `domain/model/Forecast.kt`
- `NoteDto` → `domain/model/Note.kt`
- `BlockDto` → `domain/model/Block.kt` (ya existe parcialmente como `School`)
- `BlockLineDto` → `domain/model/BlockLine.kt`
- `ContributionDto` → `domain/model/Contribution.kt`

Cada DTO tiene `fun toDomain()`. UiState contiene **modelos de dominio**,
nunca DTOs.

#### 1.3 Partir `SchoolApi` por bounded context

```
data/api/
  SchoolApi.kt              // schools, search, by-region, by-distance
  ForecastApi.kt            // forecast, by-location, today-scores
  BlockApi.kt               // blocks CRUD
  NoteApi.kt                // notes CRUD
  ContributionApi.kt        // contributions submit/list/admin
  ProfileApi.kt             // /me, photo, fcm-token
  FavoritesApi.kt
  JournalApi.kt
  SocialApi.kt              // search-users, follow, follow-list
  NotificationApi.kt
```

Cada Repository implementación recibe solo el API que le toca.

#### 1.4 Sacar el dibujo del topo del Composable

`drawStartIcon`, normalización de puntos, badges numéricos → función pura
que recibe `TopoLineData` y devuelve **instrucciones de dibujo** (`DrawOp`
sealed class: `Line(points)`, `Circle(center, radius, color)`,
`Text(pos, label, color)`). El Composable solo traduce esas instrucciones
a `Canvas` de Compose. En iOS, las mismas instrucciones se traducen a
SwiftUI `Canvas`.

#### 1.5 Abstracciones de Firebase

```
domain/port/
  PhotoUploader.kt          // suspend fun upload(uri, schoolId): String
  ChatService.kt            // interfaz para Firestore chat
  AuthService.kt            // sign in, sign out, current user

data/
  FirebaseStoragePhotoUploader.kt   // implementación Android (Firebase Android SDK)
  FirebaseChatService.kt
  FirebaseAuthService.kt
```

En iOS escribiremos otras implementaciones usando los SDKs iOS de Firebase.

#### 1.6 Abstracción de tipos plataforma-específicos

- `android.net.Uri` → wrapper `domain/model/FileRef.kt` con `path: String`.
  El picker de fotos lo convierte a `FileRef` antes de salir del Composable.
- `Context` → eliminar de cualquier sitio que no sea infraestructura
  estrictamente Android.

**Resultado de Fase 1**: la app Android sigue funcionando exactamente igual,
pero ahora el `domain/` está listo para extraerse a un módulo común.

---

### Fase 2 — Crear módulo `shared` KMP (2-3 sesiones)

Estructura final del repo Android (que pasa a ser el monorepo del cliente):

```
MeteoMontanaClients/         (rename eventual del repo)
  shared/                     // ← módulo KMP
    src/commonMain/kotlin/
      domain/
        model/                // School, Block, Forecast... (los de Fase 1)
        usecase/              // GetSchoolsUseCase... (los de Fase 1)
        port/                 // PhotoUploader, AuthService...
        util/                 // Geo (Haversine)
      data/
        api/                  // Interfaces de API + DTOs Kotlinx Serialization
        client/               // Cliente HTTP (Ktor)
        repository/           // Implementaciones de los ports de dominio
    src/androidMain/kotlin/
      LocationProvider.kt     // actual con FusedLocation
      FirebaseStoragePhotoUploader.kt
      FirebaseAuthService.kt
    src/iosMain/kotlin/
      LocationProvider.kt     // actual con CLLocationManager
      FirebaseStoragePhotoUploader.kt  // Firebase iOS via cocoapods/SPM
      FirebaseAuthService.kt
  androidApp/                 // ← lo que hoy es app/
    src/main/...              // ui/ + di/ + MeteoMontanaApp.kt
  iosApp/                     // ← nuevo, Xcode
    iosApp/
      ContentView.swift
      MeteoMontanaApp.swift
      Screens/
        SchoolListView.swift
        SchoolDetailView.swift
        ...
```

#### 2.1 Setup KMP
- Crear `shared` con plugin `kotlin-multiplatform`.
- Targets: `androidTarget()`, `iosX64()`, `iosArm64()`, `iosSimulatorArm64()`.
- Configurar `androidApp` como consumidor del módulo `shared`.

#### 2.2 Migrar `domain/` a `commonMain`
- Mover modelos + use cases + ports + util.
- Reemplazar `kotlinx.coroutines` (ya multiplatform) — sin cambios.
- Date/time: `kotlinx-datetime`.
- Logs: `napier` o `kermit`.

#### 2.3 Migrar `data/` a `commonMain` (con Ktor)
- Reemplazar **Retrofit** → **Ktor Client** (multiplataforma).
- Reemplazar **Moshi** → **Kotlinx Serialization**.
- DTOs idénticos en estructura, anotaciones distintas (`@Serializable`).
- Repositorios: lógica idéntica, llamadas Ktor en vez de Retrofit.

#### 2.4 Implementaciones plataforma-específicas
- `androidMain`: LocationProvider (FusedLocation), Firebase Android SDKs.
- `iosMain`: LocationProvider (CLLocationManager), Firebase iOS SDKs vía
  Swift Package Manager o CocoaPods (decidiremos según ecosistema actual).

#### 2.5 Adaptar Android para consumir `shared`
- `androidApp` solo contiene UI (Composables + ViewModels).
- ViewModels reciben use cases del módulo `shared` via Hilt.
- Verificar que todas las pantallas siguen funcionando.

**Lo que necesito de ti en Fase 2:**
- Nada técnico. Confirmar entre sesiones que el Android sigue funcionando.

---

### Estrategia iOS en paralelo (durante Fases 1 y 2)

**Decisión:** en cada sesión que refactorice una parte de Android, dejo el
`.swift` equivalente escrito en `iosApp/`. Cuando llegue el Mac, basta abrir
Xcode y `Cmd+B`.

Ejemplo: al refactorizar `SchoolListViewModel` (Fase 1.1 use cases) dejo en
paralelo:

```
iosApp/iosApp/Screens/SchoolListView.swift
iosApp/iosApp/ViewModels/SchoolListViewModel.swift
```

Estos `.swift`:
- Consumen los use cases del `shared` (mismo nombre, mismo input/output).
- Tienen previews SwiftUI con datos de prueba para que al abrir Xcode se
  pueda diseñar sin backend.
- Replican el look Cumbre (Terra, Mono, Serif) usando el design system
  documentado en `DESIGN.md`.

**Limitación honesta**: yo no puedo *ver* cómo queda el SwiftUI ni
ejecutarlo en simulador. Estoy escribiendo "a ciegas" basándome en el
design system y la documentación oficial de SwiftUI. Cuando abras Xcode
habrá probablemente ajustes visuales menores (paddings, line-heights,
posiciones de iconos). Es **normal y esperado** — lo arreglamos juntos en
Fase 3 con el Mac delante.

### Fase 3 — App iOS en SwiftUI (5-8 sesiones)

#### 3.1 Setup proyecto Xcode
- Crear `iosApp/iosApp.xcodeproj` (target iOS 17+).
- Configurar dependencia al framework `shared.framework`.
- Configurar SPM / CocoaPods para Firebase iOS, MapLibre iOS.

#### 3.2 DI iOS
- KMP no incluye Hilt en iOS. Opciones: Koin (multiplatform), o factory
  manual via Swift. Recomiendo **Koin** para mantener un solo grafo.

#### 3.3 Pantallas iOS
Orden propuesto (incremental, app navegable desde la 3ª):

1. **Login** (Sign in with Apple + Sign in with Google).
2. **Lista de escuelas** con filtros + heatmap + 50km default.
3. **Detalle de escuela** (forecast + heatmap horario + notas).
4. **Mapa de escuela** (MapLibre iOS + markers + BlockDetailDialog en SwiftUI).
5. **Proponer parking + piedra** (con editor topo en SwiftUI Canvas).
6. **Notas + diario + perfil**.
7. **Chat** (Firestore iOS).
8. **Admin panel** (último, no es crítico para MVP iOS).

#### 3.4 Topo drawing en SwiftUI
- Reusa `TopoLineData` + `DrawOp` del `shared` (definido en Fase 1.4).
- SwiftUI `Canvas` ejecuta los `DrawOp` con CoreGraphics.
- La lógica de detectar drag → puntos normalizados está en `shared`.

#### 3.5 Publicación
- Cuenta Apple Developer ($99/año).
- TestFlight para beta.
- App Store review.

---

## Lo que necesitas físicamente — estrategia "todo lo posible sin Mac"

**Estrategia confirmada (2026-06-06):** dejamos lo de Apple/Mac para el final.
Avanzamos en Windows hasta que sea **literalmente imposible** continuar sin
Mac. Eso significa hacer en Windows:

### En Windows (sin Mac, sin Apple Developer, sin coste)

- ✅ **Fase 0** entera — planificación.
- ✅ **Fase 1** entera — refactor Clean Android.
- ✅ **Fase 2** entera — crear `shared` KMP, migrar a Ktor, mover modelos a
  `commonMain`. La app Android sigue funcionando y consume el `shared`.
- ✅ **Fase 2.4 extendida** — **yo escribo los `actual` de iOS en Kotlin
  ahí mismo** (`shared/src/iosMain/`). Quedan listos esperando a un Mac
  que los compile.
- ✅ **Pseudocódigo y stubs de SwiftUI** — puedo dejar los `.swift` escritos
  en `iosApp/` en el repo. No los podemos compilar desde Windows, pero
  estarán versionados en git esperando.

Cuando termina la Fase 2 estás en un punto donde:
- El módulo `shared` compila para Android y **el código fuente de iOS está
  100% escrito** (Kotlin `iosMain` + Swift `iosApp/`).
- Falta solo: abrir el proyecto en Xcode, dar a Build, arreglar lo que salte,
  configurar provisioning profile, subir a TestFlight.

### Cuando NO se puede seguir sin Mac

Estos pasos requieren Mac obligatoriamente:

1. **Compilar el framework iOS** (`./gradlew :shared:linkDebugFrameworkIosArm64`
   requiere las herramientas de línea de comandos de Xcode, solo macOS).
2. **Abrir el proyecto Xcode** y resolver errores de SwiftUI / linker /
   provisioning.
3. **Probar en simulador** o dispositivo iOS real.
4. **Firmar y subir a TestFlight** / App Store.

### Opciones de Mac (cuando llegue el momento)

1. **Mac mini M4** (~700 € nuevo). El más barato y rinde de sobra para Xcode.
2. **MacBook Air M3** (~1200 € nuevo). Si quieres movilidad.
3. **Alquilar Mac en la nube** — MacStadium / MacinCloud / Scaleway M1.
   Caro a largo plazo (50–100 €/mes), útil solo para validar el primer build.
4. **GitHub Actions con runner macOS** — Sirve para build/release CI, no
   para desarrollo iterativo.

**Recomendación honesta:** comprar Mac cuando termine Fase 2 y veas que el
`shared` funciona y el código iOS está listo. Así el Mac llega "para acabar"
en lugar de "para arrancar".

### Lo que NO se puede dejar "para el final" — Firebase iOS

Una excepción importante: para que `FirebaseAuthService` y
`FirebaseStoragePhotoUploader` funcionen en iOS necesitamos el archivo
`GoogleService-Info.plist` del proyecto Firebase. **Esto lo puedes generar
desde Windows** entrando en https://console.firebase.google.com → proyecto
`climbingteams` → "Add app" → iOS → bundle ID `com.meteomontana.ios` (o el
que decidas). Descargas el `.plist` y lo metes en el repo. Sin Mac, sin
Apple Developer todavía.

**Esto lo haces tú una vez. Yo te aviso cuándo.** Probablemente al final
de Fase 2.

---

## Lo que necesito de ti puntualmente

| Cuándo | Qué | ¿Necesita Mac? |
|---|---|---|
| Fase 0 (ya) | Decidir scope MVP iOS (¿qué pantallas son críticas?). | ❌ |
| Fase 1 | Probar la app Android tras cada sesión para confirmar que no se ha roto nada. | ❌ |
| Fase 2 | Lo mismo. | ❌ |
| **Final de Fase 2** | **Añadir app iOS al proyecto Firebase `climbingteams`** desde la web (Add app → iOS → bundle ID) y descargar `GoogleService-Info.plist`. Yo te aviso. | ❌ (solo navegador) |
| **Fase 3** (Mac requerido) | Compilar el proyecto Xcode y reportarme errores. | ✅ |
| Fase 3 | Crear cuenta Apple Developer ($99/año). | ✅ |
| Fase 3 | Configurar Sign in with Apple en la cuenta Developer. | ✅ |
| Fase 3 | Subir a TestFlight para beta-testers. | ✅ |

**Lo que NO necesito de ti:**
- Tocar código. Yo lo escribo todo.
- Decisiones de arquitectura sobre la marcha — están todas en este documento.
- Mantener Android al día — el Android sigue funcionando intacto durante todo el refactor (Fase 1 es invisible al usuario final, Fase 2 igual).

---

## Riesgos y mitigaciones

| Riesgo | Mitigación |
|---|---|
| Retrofit → Ktor introduce regresiones | Migrar progresivamente, primero un Repository, validar, luego el resto. |
| Firebase iOS SDK + KMP no se enlazan bien | Plan B: usar Firebase en `iosMain` directamente con interop a Objective-C/Swift. Es trabajo extra (Fase 2.4), no bloqueante. |
| MapLibre iOS difiere mucho de Android | Aceptado. El mapa iOS se escribe nuevo en SwiftUI, los datos vienen del `shared`. |
| El usuario quiere una feature nueva durante el refactor | Si es Android-only: la añadimos al `androidApp/` aparte. Si requiere lógica de negocio: la añadimos al `shared` y aparece automáticamente en iOS cuando llegue su turno. |
| Coste Mac | Esperar a Fase 3 para decidir. Fases 0–2 no necesitan Mac. |

---

## Estimación total

- **Fase 0**: 1 sesión (este documento). ✅ HECHO
- **Fase 1.0**: 2 sesiones (tests como red de seguridad).
- **Fase 1**: 3–4 sesiones (refactor Clean Android + escribir .swift iOS en paralelo).
- **Fase 2**: 2–3 sesiones (crear `shared`, migrar a Ktor, expect/actual + más .swift).
- **Fase 3**: 5–8 sesiones (cuando llegue el Mac: abrir Xcode, ajustar visual, TestFlight).

**Total**: 13–18 sesiones aproximadamente. Iremos sesión a sesión sin
prisa, cada una con un commit cerrado a `main` y todos los tests verdes.

---

## Próximo paso

**✅ Hecho (2026-06-12, sesión remota): bloque backend aprobado** — ETag/304
en `/api/schools*`, tiempo de secado (`current.hoursToDry` + lluvia pasada
real con `past_days=3`), mejor franja del día ganador en el push de la
alerta, y fotos en notas (V23 + UI Android). Detalle en la bitácora de
ambos CLAUDE.md.

**Pendiente inmediato (próxima sesión local):**

1. Compilar y probar en Android Studio (la sesión remota no tenía SDK):
   detalle de escuela → "● HÚMEDA · SECA EN ~Xh" tras lluvia, y publicar
   nota con foto.
2. **Firebase Console → Storage rules**: permitir escritura autenticada en
   `note-photos/**` (copiar la regla de `piedra-photos-pending/**`). Sin
   esto la subida de foto de nota falla con permission-denied.
3. Desplegar el backend (Railway hace deploy al hacer merge/push a main;
   Flyway aplica V23 sola).
4. Opcional: enseñar `hoursToDry` también en `CompareScreen` y valorar el
   plugin `HttpCache` de Ktor para aprovechar el ETag desde la app.

**Después: Fase 3.1: Crear el proyecto iOS (`iosApp/`) y conectar el framework KMP compilado.**

Fase 2.5 completada. El módulo `shared` está listo para ser consumido desde iOS:

- `commonMain/`: domain + data completos (use cases, repos, DTOs, Ktor APIs) ✅
- `androidMain/`: Firebase implementations (Auth, Chat, Storage, FileReader) ✅
- `iosMain/`: vacío — se rellena en Fase 3 con equivalentes Swift/iOS

Lo que toca en Fase 3.1 (requiere que el usuario tenga Mac o CI Mac):

1. **Crear `iosApp/`** con un Xcode project que referencia el framework KMP compilado
   (`shared.xcframework` generado por `./gradlew linkReleaseFrameworkIosSimulatorArm64`).

2. **Implementaciones `iosMain/`** equivalentes a `androidMain/`:
   - `FirebaseAuthService.kt` (iOS) — usando `FirebaseAuth` iOS SDK
   - `FirebaseStoragePhotoUploader.kt` (iOS)
   - `FirebaseChatService.kt` (iOS) — o conectar Firestore iOS SDK
   - `IosFileReader.kt` — leyendo desde URLs de iOS

3. **Inyección de dependencias iOS** — sin Hilt. Manual factory o Swinject.
   `KtorSchoolRepository(api: KtorSchoolApi)` se instancia en un `AppDependencies.swift`.

4. **Primera pantalla SwiftUI** — `SchoolListView` que consume `GetSchoolsUseCase`
   publicado como `@Observable` o `ObservableObject`.

**Pendiente Android:** nada — `uploadPhoto` de perfil, flujos SECTOR y
CORREGIR POSICIÓN, build R8 verificado, etc. están completos (2026-06-10).
Solo queda la Fase 3 (requiere Mac).

### Sub-paso 2A — Forecast (+ tipos anidados)

DTOs a migrar: `ForecastDto`, `CurrentDto`, `HourForecastDto`,
`DayForecastDto`, `BestDayDto`, `OptimalWindowDto`, `ScoreFactorDto`.

Tocará:
- `domain/model/Forecast.kt` (data classes anidadas).
- `data/api/dto/ForecastMapping.kt` con `toDomain()` para cada uno.
- `GetForecastUseCase` devuelve `Forecast`.
- `SchoolDetailUiState.Success.forecast: Forecast?`.
- Componentes: `ForecastBody.kt`, `HourlyScoreGrid.kt`, posiblemente
  `WmoWeatherIcon.kt` consumidor.

### Sub-paso 2B — Block + BlockLine

DTOs: `BlockDto`, `BlockLineDto`, `CreateBlockRequest`,
`CreateBlockLineRequest`.

Decisión a tomar al empezar: ¿`CreateBlockRequest` se queda como input
DTO o también se hace un `BlockInput` de dominio? Recomendación: por
ahora dejar los `*Request` como input DTOs (su única responsabilidad
es la serialización al backend), pero los outputs (`Block`, `BlockLine`)
sí pasan a dominio.

Tocará MUCHOS ficheros:
- `domain/model/Block.kt`, `BlockLine.kt`.
- `data/api/dto/BlockMapping.kt`.
- Use cases `GetBlocks`, `CreateBlock`, `UpdateBlock` (Delete no
  devuelve nada).
- `SchoolDetailUiState.Success.blocks: List<Block>`.
- `AdminUiState.schoolBlocks: Map<String, List<Block>>`.
- Componentes: `SchoolMap.kt`, `BlocksSection.kt`, `BlockDetailDialog.kt`,
  `TopoPhotoCanvas.kt`, `EditBlockDialog.kt`, `AddLinesFlow.kt`,
  `FullScreenMapDialog.kt`, `ContributionTopoDialog.kt`,
  `ProposeContributionFlow.kt`, `AdminScreen.kt`.

### Sub-paso 2C — Contribution

DTOs: `ContributionDto`. (`ContributionRequest` se queda como input).

Tocará:
- `domain/model/Contribution.kt`.
- `data/api/dto/ContributionMapping.kt`.
- Use cases admin de contributions + `SubmitContributionUseCase` (output).
- `AdminUiState.contributions: List<Contribution>`.
- `AdminScreen.kt` (ContributionCard).

### Sub-paso 2D — Profile, Inbox, Favorite, Submission ya hecha

Migración de los modelos restantes ligeros tras los tres pesados.

Regla común: nombres de campos idénticos a los DTOs para minimizar
cambios en las pantallas (solo import + tipo).

Los ViewModels y `UiState` siguen exponiendo DTOs Moshi (`SchoolScoreDto`,
`ForecastDto`, `NoteDto`, `BlockDto`, `ContributionDto`...). En KMP el
`shared/commonMain` no puede leer anotaciones Moshi, así que necesitamos
modelos de dominio puros y conversiones `DTO.toDomain()`.

Plan concreto:

1. Crear modelos en `domain/model/`:
   - `SchoolScore` (de `SchoolScoreDto`)
   - `Forecast`, `Current`, `HourForecast`, `DayForecast`, `BestDay`,
     `OptimalWindow`, `ScoreFactor` (de `ForecastDto` y sub-DTOs)
   - `Note` (de `NoteDto`)
   - `Block`, `BlockLine` (de `BlockDto`, `BlockLineDto`)
   - `Contribution` (de `ContributionDto`)
   - `Submission` (de `SubmissionDto`)
   - `AdminStats`, `AdminLog`, `AdminPushResult` (de los DTOs admin)
   - `PrivateProfile`, `PublicProfile`, `FollowStatus`, `Notification`,
     `Inbox` (de sus DTOs)
   - `FavoriteSchool`, `FavoritesGrid`...
2. Para cada DTO, función `toDomain()` en `data/api/dto/` (extensión).
3. Use cases: cambiar firma para devolver el modelo de dominio
   (`List<School>`, `List<Block>`, `Forecast`...) en lugar del DTO.
4. UiState (Loading/Success/Error de cada VM): reemplazar DTOs por
   modelos de dominio.
5. Pantallas (`SchoolListScreen`, `SchoolDetailScreen`, `AdminScreen`,
   componentes como `ForecastBody`, `BlocksSection`, `NotesSection`,
   `HourlyScoreGrid`, `ContributionCard`, etc.): usar los modelos de
   dominio en vez de DTOs.
6. Tests: las aserciones cambian (`s.notes.first().text` sigue válido,
   `BlockDto` → `Block`, etc.). El comportamiento es idéntico.

Reglas:
- Modelos en `domain/model/` sin imports Moshi/Retrofit/android.
- Solo data classes con tipos primitivos / otros modelos de dominio.
- Conversión solo en la frontera (`data/api/dto/` extensión).
- Tests verdes antes de commit (`./gradlew :app:testDebugUnitTest`,
  `JAVA_HOME=/c/Program Files/Android/Android Studio/jbr`).

Ya están hechos los use cases de `schools/` (GetSchools, GetSchoolById,
GetTodayScores) y `forecast/` (GetForecast). `SchoolListViewModel` y
`SchoolDetailViewModel` los consumen — pero ambos VMs todavía inyectan
`SchoolApi` para el resto de llamadas (favoritos, notificaciones, notas,
blocks, contributions). El objetivo de la siguiente sesión es eliminar
esa dependencia residual.

Plan concreto:

1. Crear use cases en `domain/usecase/`:
   - `blocks/`: `GetBlocksUseCase`, `CreateBlockUseCase`,
     `UpdateBlockUseCase`, `DeleteBlockUseCase`.
   - `contributions/`: `SubmitContributionUseCase`,
     `GetMyContributionsUseCase`.
   - `notes/`: `GetNotesUseCase`, `CreateNoteUseCase`.
   - `favorites/`: `GetMyFavoritesUseCase`, `AddFavoriteUseCase`,
     `RemoveFavoriteUseCase`.
   - `notifications/`: `GetMyNotificationsUseCase`.
   - `profile/`: `GetMyProfileUseCase` (solo para detectar admin desde VM).
   - `admin/`: `GetStatsUseCase`, `GetPendingSubmissionsUseCase`,
     `GetPendingContributionsUseCase`, `ApproveSubmissionUseCase`,
     `RejectSubmissionUseCase`, `ApproveContributionUseCase`,
     `RejectContributionUseCase`, `SendPushUseCase`, `GetLogsUseCase`.
2. Refactor `SchoolListViewModel`: quitar `SchoolApi` del constructor.
3. Refactor `SchoolDetailViewModel`: quitar `SchoolApi`.
4. Refactor `AdminViewModel`: quitar `AdminApi` y `SchoolApi`.
5. Adaptar los 3 tests de ViewModel para mockear use cases en vez de
   APIs. Las aserciones de comportamiento deben quedar idénticas.

Reglas:
- Cada use case es una `class` con `operator fun invoke(...)` o `suspend`.
- Inyección por Hilt. Sin objects, sin singletons manuales.
- Use cases en `commonMain` cuando llegue Fase 2 → NO importar nada de
  `android.*` ni `androidx.*` dentro de ellos. (`Uri` en
  `SubmitBoulderContributionUseCase` se queda en Android-only y lo
  envolveremos en `FileRef` en Fase 1.6 — por ahora lo aceptamos como
  excepción documentada).
- Tras cada paso: `./gradlew :app:testDebugUnitTest` verde antes de
  commit (`JAVA_HOME=/c/Program Files/Android/Android Studio/jbr` si
  desde PowerShell externo).

### Nota de entorno

El proyecto requiere **Java 21** para compilar (Kotlin 2.0.21 no soporta
Java 25 que es la del sistema). Hay que ejecutar Gradle con
`JAVA_HOME` apuntando al JBR de Android Studio:

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew testDebugUnitTest
```

Desde Android Studio funciona automáticamente porque usa su JBR.
Cuando actualicemos Kotlin a 2.2.x en Fase 2 ya soportará Java 25.

---

## 🤖 Protocolo para sesiones nuevas (LEER PRIMERO)

Si estás abriendo una sesión nueva y NO tienes contexto, sigue exactamente
estos pasos:

1. **Lee `CLAUDE.md`** (raíz del repo Android). Te da el contexto general:
   stack, endpoints, convenciones.

2. **Lee este documento** (`KMP_MIGRATION.md`) y específicamente la sección
   **📍 ESTADO ACTUAL DE LA MIGRACIÓN** del principio. Ahí ves:
   - Qué fases están hechas (`[x]`) y cuáles pendientes (`[ ]`).
   - Marcada con `← SIGUIENTE` la sub-tarea que toca.

3. **NO repreguntes al usuario** "¿por dónde íbamos?". Ya lo sabes leyendo
   los documentos.

4. **NO replanifiques** la migración. Las fases y el orden están decididos.
   Si crees que hay un cambio importante, propónselo al usuario, pero no
   actúes sin confirmar.

5. **Ejecuta la tarea siguiente** tal como está descrita en su sección
   ("Próximo paso" al final del documento + descripción de su fase).

6. **Al final de la sesión**, ANTES de hacer commit, **actualiza dos cosas
   en este documento**:
   - El checklist de la sección **📍 ESTADO ACTUAL**: marca `[x]` las
     tareas hechas, mueve el `← SIGUIENTE` a la próxima.
   - La sección **"Próximo paso"** al final del documento: describe en
     concreto qué hará la próxima sesión (no genérico, concreto).
   - Cambia "Última actualización: AAAA-MM-DD" al inicio del estado.

7. **Commit + push a `main` de ambos repos** (Android y Backend si tocaste
   los dos). Mensaje de commit descriptivo.

8. **Sincroniza al worktree** si trabajaste en
   `C:\Users\rouma\MeteoMontanaAndroid` directo:
   ```powershell
   Copy-Item -Force -Recurse <ficheros> .claude\worktrees\heuristic-dewdney-6ff0f1\
   ```
   (esto es por la convención de este repo, no es estándar KMP).

### Reglas duras

- **Idioma del usuario**: español. **Código**: inglés.
- **Paso a paso, una cosa a la vez.** No mezclar tareas de fases distintas.
- **Verifica antes de proponer.** Lee el código existente antes de tocar algo.
- **Aplica los cambios directamente** (Edit/Write). No pegar snippets para
  que el usuario copie a mano.
- **Si tocas backend + Android**, empieza por el backend (compila →
  reinicia → luego Android).
- **Tests verdes**: tras cada refactor en Fases 1.x y 2.x, los tests de
  Fase 1.0 deben seguir pasando. Si alguno se rompe, lo arreglas antes de
  commitear.
- **No instalar nada** que requiera Mac o licencias de pago. Si lo necesitas,
  páralo y díselo al usuario.

---

## 🍎 PORT A iOS — backlog acumulado en sesiones Android

> Cada vez que en Android añadamos una feature que use APIs nativas, código
> Android-only, librerías sin port a iOS, o UI Compose, **se anota aquí**.
> Cuando arranque la fase iOS (cuando el usuario tenga Mac), esta es la lista
> de cosas que hay que portar en SwiftUI / Swift / KMP nativo.

### Features añadidas pendientes de port a iOS

#### A) Foto de perfil (subida)
- **Estado Android**: ✅ funciona. Picker `ActivityResultContracts.GetContent` →
  decode + compress con `BitmapFactory` a 1024px JPEG 80% → `PhotoUploader.
  uploadProfilePhoto(bytes)` → Firebase Storage `profile-photos/{uid}.{ext}` →
  PUT `/api/me` con `photoUrl`.
- **Lado iOS**:
  - Picker: `PHPickerViewController` o `PhotosPicker` (SwiftUI nativo iOS 16+).
  - Compresión: `UIImage.jpegData(compressionQuality:)`.
  - Inyectar `PhotoUploader` (impl iOS usando FirebaseStorage SDK).
  - Reutilizar `UpdateMyProfileUseCase` desde `shared/`.
- **Backend cambios ya aplicados** (sirven para los dos):
  - `UpdateProfileRequest` añade campo `photoUrl`.
  - `UserDtoMapper.resolvePhotoUrl()` detecta URLs https:// y las devuelve
    sin firmar.
- **Reglas Storage publicadas** (ya están vivas para los dos):
  ```
  match /profile-photos/{file} { allow read: if true; allow write: if request.auth != null && size<5MB; }
  match /piedra-photos-pending/{file} { ... }
  match /piedra-photos/{file} { ... }
  ```

#### B) Cropper de foto de perfil (rotar + zoom + circular)
- **Estado Android**: pendiente — librería `uCrop` (o cropper custom Compose).
- **Lado iOS**: `TOCropViewController` (Pod Swift madura) o cropper custom SwiftUI
  con `MagnificationGesture` + `RotationGesture`. Ambos producen `Data` de
  bytes que pasan al `PhotoUploader` compartido.

#### C) Pantalla `DayDetailScreen` (detalle de día clickable)
- **Estado Android**: ✅ Compose. ÍNDICE DEL DÍA + condiciones (MÁX/MÍN/VIENTO/
  LLUVIA/PROB) + tabla "PRÓXIMAS 24H" con icono WMO + temp + % + mm + viento +
  minibarra de lluvia.
- **Lado iOS**: SwiftUI equivalente. Datos vienen ya del `Forecast` del
  `shared/`. Tab y nav del schoolId/lat/lon/dayIndex igual.
- Helper `Modifier.clickable { onDayClick(i) }` en `DayRow` → en iOS, gesto
  `.onTapGesture { ... }` en cada fila.

#### D) Stats mensuales últimos 3 años + cache local
- **Estado Android**: ✅ `MonthlyStatsRepository` + Room (`MonthlyStatsEntity`,
  TTL 180d) + `OpenMeteoArchiveClient` (Ktor → archive-api.open-meteo.com) +
  `ClimbScore.kt` (port simplificado del scoring de la PWA).
- **Decisión clave para iOS**: **migrar persistencia a SQLDelight** desde
  `shared/commonMain` ANTES de portar. Razón:
  - Room es Android-only (no compila en Mac/iOS).
  - SQLDelight es KMP-friendly, mismo SQL en ambos.
- **Tareas concretas**:
  1. Migrar `MonthlyStatsEntity` + `MonthlyStatsDao` → SQLDelight schema en
     `shared/commonMain/sqldelight/`.
  2. Mover `MonthlyStatsRepository` a `shared/commonMain` (no usa nada
     Android-only).
  3. Mover `OpenMeteoArchiveClient` a `shared/commonMain` — reemplazar
     `org.json.JSONObject` por kotlinx-serialization con `@Serializable`
     (shared ya tiene el plugin).
  4. Mover `ClimbScore.kt` a `shared/commonMain` (puro Kotlin).
  5. UI iOS: `MonthlyStatsSection` SwiftUI equivalente.
- **Beneficio**: la lógica histórica + cache se comparte 100% entre Android
  y iOS. Solo la UI cambia.

#### E) Escuelas guardadas offline (snapshot + mapa + vías)
- **Estado Android**: ✅ Room (`SavedSchoolEntity`, `SavedBlockEntity`,
  `SavedBlockLineEntity`, `SavedForecastEntity`) + `SavedSchoolRepository` +
  serialización forecast vía `ForecastJson.kt` (org.json) +
  `SavedSchoolsScreen` + botón "GUARDAR OFFLINE" + banner "● SIN CONEXIÓN ·
  Datos del 7 jun 14:23".
- **Decisión clave para iOS**: misma de D — migrar a SQLDelight.
- **Tareas concretas**:
  1. Migrar las 5 entities + 2 DAOs a SQLDelight `.sq` files.
  2. Mover `SavedSchoolRepository` y `ForecastJson` a `shared/commonMain` —
     reemplazar `org.json.JSONObject` por kotlinx-serialization (`@Serializable`
     en los data classes de Forecast — ya están serializables porque están
     en commonMain).
  3. UI iOS: `SavedSchoolsScreen` SwiftUI.
  4. UI iOS: banner offline equivalente en SchoolDetailView.
- **Fotos de bloques offline**:
  - Android usa Coil — cachea en disco automáticamente al cargar online,
    sirve la misma URL offline desde cache.
  - iOS equivalente: `Kingfisher` o `NukeUI` (configurar disk cache).
  - **No** necesita descarga manual a filesystem.
- **Tiles de mapa offline**: pendiente en Android también (MapLibre
  `OfflineManager`). En iOS, MapLibre iOS SDK tiene su propio
  `MGLOfflineStorage`. Ambos requieren código separado por plataforma.

#### F) "PRÓXIMAS 16 HORAS" desde la hora actual
- **Estado Android**: ✅ `HourlyScoreGrid` usa `java.time.LocalDateTime.now()`
  para filtrar.
- **Lado iOS**: reemplazar por `Date()` + `Calendar.current.component(.hour, ...)`
  o `kotlinx.datetime.Clock.System.now()` desde shared.
- **Mejor**: mover el filtro a `shared/commonMain` con `kotlinx.datetime` y
  exponer una propiedad `Forecast.hoursFromNow(maxN: Int)` en domain. Así
  ambos UIs llaman al mismo helper.

#### G) Detección online/offline
- **Estado Android**: implícita — si una llamada Ktor falla, se considera
  offline y `SchoolDetailViewModel` cae al snapshot Room.
- **Pendiente para los dos**: abstracción `NetworkMonitor` en
  `shared/commonMain` (interfaz). Implementaciones:
  - Android: `ConnectivityManager.NetworkCallback`.
  - iOS: `NWPathMonitor` de Network framework.
- Útil para mostrar banner global "SIN CONEXIÓN" + decidir flujos antes de
  intentar llamadas.

#### H) Logging
- **Estado Android**: `android.util.Log.i(tag, msg)` en
  `EditProfileViewModel`, `FirebaseStoragePhotoUploader`.
- **Para shared**: usar `co.touchlab:kermit` (KMP-friendly, syntax igual a
  Logcat). En iOS escribe a `os_log`. Permite poner los logs en
  `shared/commonMain` sin perder visibilidad en cada plataforma.

#### I) Acceso a ficheros (URI → bytes)
- **Estado Android**: `context.contentResolver.openInputStream(uri)` +
  `BitmapFactory`.
- **Lado iOS**: `PHPickerResult.itemProvider.loadDataRepresentation(...)`.
- **Abstracción ya existente**: `FileReader` port (`shared/commonMain/port/`).
  Falta:
  - Extender `FileReader` con método `readImageCompressed(ref, maxDim, quality)`
    para que el cropper/compresor también esté abstraído.
  - Impl Android: la lógica que ya está en `EditProfileViewModel.compressImage`.
  - Impl iOS: equivalente con `UIImage`.
- Así `EditProfileViewModel.uploadPhoto(ref: FileRef)` puede vivir en `shared/`.

### Cambios de arquitectura recomendados ANTES de portar a iOS

Para que la fase iOS sea rápida y compartamos código de verdad:

1. **Migrar Room → SQLDelight** (Fase 4.x propuesta).
   - Crear `shared/commonMain/sqldelight/com/meteomontana/db/` con `.sq` files.
   - Mover `MonthlyStatsRepository`, `SavedSchoolRepository`,
     `MonthlyStatsEntity`, `SavedSchool*` a commonMain.
   - Driver en androidMain: `AndroidSqliteDriver`. En iosMain: `NativeSqliteDriver`.
2. **Migrar org.json → kotlinx-serialization** (en `ForecastJson` y
   `OpenMeteoArchiveClient`). Ya tenemos el plugin en shared.
3. **Crear `NetworkMonitor` port + impls** por plataforma.
4. **Crear `Clock`/`Now` helper** con `kotlinx.datetime` en commonMain para
   reemplazar usos de `java.time.LocalDateTime`.
5. **Extender `FileReader`** con `readImageCompressed()` para mover lógica
   de subida de foto a shared.
6. **Adoptar Kermit** para logs.

Una vez hecho esto, el port a iOS es básicamente: setup proyecto Xcode +
SwiftUI screens espejo de las Composables + 2 impls (FileReader, NetworkMonitor)
+ inyección Koin (o init manual desde Swift).

---

## ⚡ Sesiones recientes (sigue rellenándose)

### Refactors KMP-prep ya HECHOS en sesiones Android

Estado de los 6 refactors propuestos para poder portar a iOS sin reescribir:

- [x] **Kermit** logging (`co.touchlab:kermit`) — exposed via `api()` desde shared.
- [x] **kotlinx-serialization** sustituyendo `org.json` en `ForecastJson.kt` y
  `OpenMeteoArchiveClient.kt` (ambos ya en `shared/commonMain`).
- [x] **kotlinx.datetime** — extension `List<HourForecast>.fromNow(count)` en
  `shared/commonMain/util/ForecastExtensions.kt`.
- [x] **FileReader** extendido con `readImageCompressed(ref, maxDim, quality)`.
  Impl Android en `AndroidFileReader.kt` (Bitmap/JPEG). iOS: pendiente
  (`UIImage.jpegData(compressionQuality:)`).
- [x] **NetworkMonitor** port (`domain/port/NetworkMonitor.kt`) +
  `AndroidNetworkMonitor` (ConnectivityManager). iOS pendiente
  (`NWPathMonitor`). UI Android consume vía `NetworkBanner` global.
- [x] **Room → SQLDelight** ✅ TODO migrado:
  - Schema `shared/commonMain/sqldelight/com/meteomontana/db/Schema.sq` con
    tablas `MonthlyStats`, `SavedSchool`, `SavedBlock`, `SavedBlockLine`,
    `SavedForecast`, `Outbox`.
  - `expect class DatabaseFactory` + actuals Android (AndroidSqliteDriver)
    e iOS (NativeSqliteDriver, archivo ya escrito en `iosMain/`).
  - `MonthlyStatsRepository` y `SavedSchoolRepository` movidos a
    `shared/commonMain`. Room totalmente eliminado de `app/`.

### Features nuevas Android que faltan portar a iOS

#### J) Cropper de foto de perfil — uCrop (Android)
- **Estado**: ✅ funciona con `com.github.yalantis:ucrop:2.2.10` lanzado vía
  `ActivityResultContracts`. Crop circular + zoom + rotación + 1:1 aspect.
- **iOS**: usar `TOCropViewController` (Pod) o cropper custom SwiftUI con
  `MagnificationGesture` + `RotationGesture`. Output `Data` → mismo
  `PhotoUploader.uploadProfilePhoto(bytes, mime)` de shared.

#### K) Outbox offline (sumisiones que se mandan al volver red)
- **Estado**: ✅ tabla `Outbox` en SQLDelight + `OutboxRepository` en shared
  + `OutboxFlusher` Android que escucha `NetworkMonitor.isOnline` y drena.
- Soportado offline: contribuciones (PARKING/SECTOR/POSITION_CORRECTION) +
  notas. Pendiente extender a BOULDER con foto (requiere copiar bytes a
  filesystem app y diferir el upload a Firebase Storage).
- **iOS**: `OutboxFlusher` se puede reusar tal cual (puro Kotlin) o
  reescribir con `CoroutineScope` desde Swift via interop. Lo más simple:
  exponer `start()` desde shared. Crear impl iOS del flusher si se decide
  no compartir la lógica con SubmitContributionUseCase/CreateNoteUseCase
  desde Swift.

#### L) Notificaciones push + deep link tappable
- **Estado**: ✅ `PushService` (extiende `FirebaseMessagingService`) construye
  notificación nativa con `PendingIntent` que abre `MainActivity` con
  extras `targetType`/`targetId`. `MainActivity` los lee, los pasa como
  `DeepLinkTarget` a `AppRoot → MainScreen` que navega.
- Backend: `ChatPushController.notify(toUid, preview)` dispara FCM tras
  enviar mensaje en Firestore. Permiso `POST_NOTIFICATIONS` runtime en
  Android 13+.
- **iOS**:
  - Capability "Push Notifications" + APNs en Apple Developer Console.
  - `AppDelegate` registra el device token y lo manda al backend con el
    mismo endpoint `PUT /api/me/fcm-token` (Firebase Admin SDK funciona
    igual para APNs si configuras el APNs Auth Key en Firebase Console).
  - `UNUserNotificationCenter` para mostrar/abrir + decodificar el payload
    `targetType`/`targetId` y navegar SwiftUI.
  - El push del chat funciona igual sin cambiar backend.

#### M) Modo oscuro persistente (ThemeManager)
- **Estado**: ✅ `ThemeManager` Android usa `SharedPreferences("cumbre.theme")`
  con clave `mode: LIGHT|DARK|SYSTEM`. `StateFlow<ThemeMode>` consumido por
  `MainActivity` para elegir `MeteoMontanaTheme(darkTheme = …)`. Botón
  sol/luna en `SchoolListScreen.TopIconsRow`.
- **iOS**: equivalente con `@AppStorage("cumbre.theme") var mode: String`
  + `.preferredColorScheme(isDark ? .dark : .light)` en la root view. El
  ThemeManager se puede mover a `shared/commonMain` con `expect` para
  persistencia y `actual` Android (SharedPreferences) / iOS (UserDefaults).

#### N) Tiles offline MapLibre (OfflineTileManager)
- **Estado**: ✅ `OfflineTileManager.kt` usa `OfflineManager.createOfflineRegion`
  con bounds ~2 km² + zoom 10..16, tile source OpenTopoMap.
- **iOS**: MapLibre iOS SDK tiene `MLNOfflineStorage` con API casi idéntica
  (`MLNTilePyramidOfflineRegion`). Reescribir en Swift, mismas coords +
  bounds + zoom.

#### O) Cropper / Move flow con marker fantasma
- **Estado**: ✅ Implementado en `SchoolMap.kt` (usuario) y `FullScreenMapDialog.kt`
  (admin GESTIONAR). Flujo: tap marker → fantasma alpha 0.35 + banner →
  tap mapa → ghost ★ terra → ACEPTAR (move directo admin o propuesta usuario).
- Admin viz de POSITION_CORRECTION: marker gris ✕ + marker ★ + polyline
  terra + auto-fit bounds.
- **iOS**: replicar UI con MapLibre iOS Annotations. Lógica de estado
  (CorrectionGhost, CorrectionMoving) ya está en `ProposeContributionFlow`
  Compose-only — habría que extraer parte de la lógica a `shared/commonMain`
  como `CorrectionStateMachine` para no duplicar.

#### P) Detalle de día clicable
- **Estado**: ✅ `DayDetailScreen.kt` + `DayDetailViewModel` (filter
  `forecast.hours` por día + fallback a snapshot offline). Banner "● SIN
  CONEXIÓN" en modo offline.
- **iOS**: SwiftUI screen espejo. `DayDetailViewModel` puede vivir en
  `shared/commonMain` si se le quita el `SavedStateHandle` (sustituir por
  init params).

#### Q) Chat con FCM real
- **Estado**: ✅ `ChatViewModel.send()` escribe en Firestore + llama a
  `KtorChatPushApi.notifyMessage(toUid, preview)`. Backend envía FCM al
  token del receptor.
- **iOS**: Firestore SDK iOS para el chat + APNs para push. Endpoint
  backend se reutiliza intacto.

#### R) Sectores + corrección de posición + smaller markers
- **Estado**: ✅ Flujos `SectorForm`, `CorrectionMoving` en
  `ProposeContributionFlow`. Bitmaps de piedras reducidos (40dp → 22dp).
  Marker escuela (triángulo negro tipo montaña) tappable para mover la
  escuela como propuesta.
- **iOS**: replicar bitmaps con `UIBezierPath`/Core Graphics. Misma
  lógica de estado.

### Lo que aún NO está hecho (próximas sesiones Android antes de portar)

- BOULDER con foto en el outbox offline (requiere persistir bytes locales).
- Submission de nueva escuela en outbox.
- TTL cache forecast en backend: ✅ ya hecho con Caffeine 30m.
- Fotos de bloques offline: Coil cachea automáticamente al cargar online.
  En iOS usar `Kingfisher` o `NukeUI` con disk cache habilitado.

### Backend — qué hacer cuando se llegue a Mac/producción

El backend Spring Boot es **plataforma-independiente**. NO hay que portarlo.
Tres opciones para usarlo desde Mac:

1. **Clonarlo también en Mac** y seguir levantando local (`docker compose up
   -d && ./mvnw spring-boot:run`). Funciona igual que en Windows.
   IP del Mac → cambiar `API_BASE_URL` en `app/build.gradle.kts`.
2. **Dejarlo en el Windows** apagado salvo cuando programes. Mismo flujo de
   antes pero con la IP del PC Windows.
3. **Recomendado para iOS y producción**: desplegar en Railway/Render/Fly.
   Una URL HTTPS estable como `https://api.meteomontana.app` que valga para
   Mac, iOS, Android producción y cualquier compañero. Postgres gestionada
   en la misma plataforma. Coste ~5 €/mes en uso real. Ver sección
   "Publicación" en CLAUDE.md (si está) o el documento aparte de deploy.

### Comprobaciones antes de cambiar a Mac

1. `git status` en `MeteoMontanaAndroid` y `MeteoMontanaAPI` → todo commiteado.
2. `git push origin main` en ambos.
3. Recuerda llevarte el `serviceAccountKey.json` de Firebase (NO está en
   git por seguridad). En Mac va a `MeteoMontanaAPI/api/src/main/resources/`.
4. Si tienes datos de dev importantes en Postgres local, `pg_dump > backup.sql`
   para restaurarlo en Mac o en producción.

### Mensaje plantilla para arrancar la sesión iOS

```
Estoy en Mac. Xcode, Android Studio, Homebrew, JDK 21, Cocoapods instalados.
Repos clonados. Backend corriendo en <URL/IP>.

Lee KMP_MIGRATION.md → sección "🍎 PORT A iOS" y "Sesiones recientes".
Quiero empezar por <Fase 3.x | iOS setup | feature X>.
```

---

## 🆕 Sesión 2026-06-09 — UX polish y features sociales

Cambios entregados:

#### Social y perfil
- **Contadores follow visibles** en `ProfileScreen.Header` ("X seguidores · Y siguiendo") — clicables → `FollowListScreen`.
- `ProfileViewModel` ahora pide `GetFollowStatus` además de profile + stats.
- **Auto top grade**: `ProfileViewModel.addBlock()` compara el grado de la nueva entrada de diario con `topGrade` del perfil y, si es mayor, actualiza vía `UpdateMyProfileUseCase`. Helper `gradeRank("7a+")` → `705`.
- Campo "GRADO MÁXIMO" en `EditProfileScreen` pasa a **read-only** con label "automático".

#### Chat y push
- `ChatScreen` envuelto en `imePadding()` + auto-scroll al último mensaje en `LaunchedEffect(messages.size)`. Soluciona el bug "los mensajes se van arriba al abrir el teclado".
- Push "nuevo seguidor": backend `FollowUseCase` envía data payload con `targetType=user` + `targetId=<uid>` + título personalizado ("@username te sigue ahora"). El cliente Android hace deep link al perfil público al pulsar.

#### UX
- **Search debounce 200ms** en `SchoolListViewModel.setQuery()` con `Job` cancelable. Elimina el "terremoto" al teclear.
- **CoffeeBanner** rediseñado:
  - Usa `MaterialTheme.colorScheme.primaryContainer` en vez de `TerraBg` hardcoded → se ve bien en dark mode.
  - Botón "Apóyanos" abre `DonateDialog` (modal con descripción + botón "☕ INVÍTAME A UN CAFÉ" que lanza Intent a `https://ko-fi.com/climbingteams`).

#### Email transaccional
- Backend nuevo: `ResendEmailService` (HTTP POST a `api.resend.com/emails`).
- Configurable vía env vars `RESEND_API_KEY` y `RESEND_FROM`. Si no hay key, no rompe — solo loguea skip.
- `ReviewContributionUseCase.approve()` y `.reject()` mandan email al usuario que propuso (asunto + HTML con motivo).

### Pendiente para próximas sesiones

- **#1 Fluidez de la app**: medir en build release. Si sigue lenta → perfilar recomposiciones con Layout Inspector.
- **#6 Vías (líneas) en vez de bloques** en `AddBlockSheet`: al elegir bloque mostrar las vías existentes con su grado/tipo, autocompletar el grado de la vía elegida. Si la vía no está en BD, permitir nombre+grado manual.
- **#8 Stats mensuales lentos**: mover el cálculo a un endpoint backend cacheado (`GET /api/schools/{id}/monthly-stats`) en vez de llamar a `archive-api.open-meteo.com` desde Android. El backend ya tiene `@Cacheable` con Caffeine — añadir un use case más.
- **#9 Foto crop solo mitad visible**: el `TopoPhotoCanvas` usa aspect 4:3 fijo. Ajustar para respetar el aspect real de la foto subida.
