# Plan de migración a Kotlin Multiplatform (KMP) — Android + iOS

> Decisión tomada (2026-06-06): la app MeteoMontana será multiplataforma usando
> **Kotlin Multiplatform (KMP)**. Compartiremos `domain/` y `data/` en Kotlin
> entre Android e iOS. La UI Android sigue siendo Jetpack Compose. La UI iOS
> será **SwiftUI** consumiendo el módulo compartido.

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

En la siguiente sesión empiezo **Fase 1.0 (sesión 1 de 2): tests de funciones
puras + helpers**. Voy a:

1. Añadir dependencias de test:
   - `kotlinx-coroutines-test` (ya posiblemente esté)
   - `app.cash.turbine:turbine` para testear `StateFlow`
   - `org.junit.jupiter` o `kotlin-test`
   - `io.mockk:mockk` para mockear APIs/repos en tests de VM
2. Crear `app/src/test/java/com/meteomontana/android/...` con tests para:
   - `geo/HaversineTest.kt` — distancia entre puntos conocidos
   - `submissions/ParseLatLonPasteTest.kt` — todos los formatos de Google Maps
   - `detail/BoulderBloqueFormTest.kt` — `toBloquesJson` + round-trip
   - `theme/GradeColorTest.kt` — color por grado (matching the PWA)
   - `topo/LinePathTest.kt` — serialización de Offsets
3. Ejecutar `./gradlew test`. Todos verdes.
4. Commit y push.

Tras esa sesión Android funciona idéntico, tenemos red de seguridad para
empezar el refactor sin miedo. Empezamos cuando me digas.
